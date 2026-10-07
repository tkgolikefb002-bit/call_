package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.content.FileProvider;
import java.io.File;

public class MyAccessibilityService extends AccessibilityService {
    public static MyAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // Phương thức gọi lại khi kết thúc cuộc gọi (được gọi từ MyInCallService)
    public void onCallFinished() {
        // Thực hiện các hành động tiếp theo sau khi gọi xong nếu cần
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        
        String packageName = event.getPackageName().toString();

        // Khi phát hiện camera Oppo được bật lên bởi app BEST
        if ("com.oplus.camera".equals(packageName)) {
            try {
                File imageFile = new File(getFilesDir(), "ma_van_don.jpg");
                if (imageFile.exists()) {
                    Uri imageUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        imageFile
                    );

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/jpeg");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    shareIntent.setPackage("com.best.android.vietcourier");
                    
                    startActivity(shareIntent);
                    performGlobalAction(GLOBAL_ACTION_BACK);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Xử lý khi service bị gián đoạn
    }
}

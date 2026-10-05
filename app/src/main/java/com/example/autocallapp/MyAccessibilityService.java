package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.content.FileProvider;
import java.io.File;

public class MyAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        
        String packageName = event.getPackageName().toString();

        // Khi phát hiện camera Oppo được bật lên bởi app BEST
        if ("com.oplus.camera".equals(packageName)) {
            try {
                // Lấy file ma_van_don.jpg đã chuẩn bị sẵn trong bộ nhớ trong của app
                File imageFile = new File(getFilesDir(), "ma_van_don.jpg");
                if (imageFile.exists()) {
                    Uri imageUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        imageFile
                    );

                    // Tạo Intent ACTION_SEND để đẩy ảnh sang app BEST
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/jpeg");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    // Chỉ định thẳng package của app BEST nhận file
                    shareIntent.setPackage("com.best.android.vietcourier");
                    
                    // Thực thi bắn Intent
                    startActivity(shareIntent);

                    // Giả lập phím Back để đóng ngay giao diện camera Oppo đè bên trên
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

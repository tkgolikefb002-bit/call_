package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

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
        // Lắng nghe sự kiện trợ năng nếu cần thiết trong tương lai
    }

    @Override
    public void onInterrupt() {
        // Xử lý khi service bị gián đoạn
    }
}

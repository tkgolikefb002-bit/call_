package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class MyAccessibilityService extends AccessibilityService {
    public static MyAccessibilityService instance;
    private static AccessibilityNodeInfo cachedRootNode;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        cachedRootNode = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Tự động bắt và lưu root node khi có tương tác trong app BEST
        if (event.getPackageName() != null && event.getPackageName().toString().contains("best")) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                cachedRootNode = root;
            }
        }
    }

    // Phương thức lấy root node an toàn, tránh lỗi mất focus từ bảng nổi
    public static AccessibilityNodeInfo getRootNodeSafely(AccessibilityService service) {
        if (service != null) {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root != null) {
                return root;
            }
        }
        return cachedRootNode;
    }

    @Override
    public void onInterrupt() {
        // Xử lý khi service bị gián đoạn
    }
}

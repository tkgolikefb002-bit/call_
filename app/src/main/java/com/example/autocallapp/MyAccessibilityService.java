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
        if (event.getPackageName() != null && event.getPackageName().toString().contains("best")) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                cachedRootNode = root;
            }
        }
    }

    // Bổ sung phương thức để MyInCallService gọi khi kết thúc cuộc gọi
    public void onCallFinished() {
        // Thực hiện các hành động tiếp theo sau khi gọi xong nếu cần
    }

    // Phương thức lấy root node an toàn
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
    }
}

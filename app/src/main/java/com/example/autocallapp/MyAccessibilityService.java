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
        // Lấy ngay root node của màn hình hiện tại mà không cần lọc tên package
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            cachedRootNode = root;
        }
    }

    public void onCallFinished() {
        // Xử lý khi kết thúc cuộc gọi
    }

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
        cachedRootNode = null;
    }
}

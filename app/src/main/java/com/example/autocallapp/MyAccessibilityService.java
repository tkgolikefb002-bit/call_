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
        CharSequence packageName = event.getPackageName();
        if (packageName != null) {
            String pkgStr = packageName.toString().toLowerCase();
            // Mở rộng từ khóa nhận diện app BEST hoặc các tiến trình liên quan
            if (pkgStr.contains("best") || pkgStr.contains("800best") || pkgStr.contains("sea")) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    cachedRootNode = root;
                }
            }
        }

        // Bổ sung bắt sự kiện thay đổi cửa sổ để tự động cập nhật node mà không bị kẹt
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED 
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                cachedRootNode = root;
            }
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

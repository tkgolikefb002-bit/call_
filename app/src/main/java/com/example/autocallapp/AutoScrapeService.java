package com.example.autocrape;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoScrapeService extends AccessibilityService {

    private static final String TAG = "AutoScrapeService";
    private boolean isRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Các thành phần logic quét mã cơ bản
    private final Set<String> scrapedCodes = new HashSet<>();
    private List<String> targetCodesToProcess = new ArrayList<>();
    private int currentIndex = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = android.view.accessibility.AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                     AccessibilityServiceInfo.FLAG_CAN_OBSERVE_KEYBOARD;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Xử lý sự kiện giao diện nếu cần
    }

    @Override
    public void onInterrupt() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    // --- CÁC HÀM HỖ TRỢ ĐIỀU HƯỚNG / TÌM KIẾM CƠ BẢN ---

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String id) {
        if (root == null) return null;
        if (id.equals(root.getViewIdResourceName())) {
            return root;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            AccessibilityNodeInfo result = findNodeById(child, id);
            if (result != null) {
                return result;
            }
            if (child != null) {
                child.recycle();
            }
        }
        return null;
    }

    private void setTextToNode(AccessibilityNodeInfo node, String text) {
        if (node == null) return;
        if (node.isEditable()) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_VALUE, text);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        } else {
            // Fallback dùng Clipboard nếu node không editable trực tiếp
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("label", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
            }
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        }
    }

    // Thực hiện thao tác vuốt màn hình để quét mã
    public boolean performVerticalSwipe(float startX, float startY, float endX, float endY, long duration) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.accessibilityservice.GestureDescription.Builder builder = new android.accessibilityservice.GestureDescription.Builder();
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration));
            android.accessibilityservice.GestureDescription gesture = builder.build();
            
            final boolean[] success = {false};
            dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(android.accessibilityservice.GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    success[0] = true;
                }

                @Override
                public void onCancelled(android.accessibilityservice.GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    success[0] = false;
                }
            }, null);
            return success[0];
        }
        return false;
    }
}

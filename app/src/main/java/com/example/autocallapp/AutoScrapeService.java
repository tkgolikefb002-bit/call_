package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoScrapeService extends AccessibilityService {
    private static final String TAG = "AutoScrapeService";
    public static AutoScrapeService instance;
    private boolean isScraping = false;
    private boolean isCallingProcessActive = false; 
    
    private final Set<String> collectedPhones = new LinkedHashSet<>();
    private final List<String> callQueueList = new ArrayList<>();
    private int currentCallIndex = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility Service đã kết nối thành công!");
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        isScraping = false;
        isCallingProcessActive = false;
    }

    // =========================================================================
    // PHẦN 1: QUÉT SỐ ĐIỆN THOẠI TRONG TAB HIỆN TẠI (VUỐT DỌC CHUẨN XÁC)
    // =========================================================================
    public void startScraping() {
        if (isScraping) {
            Toast.makeText(this, "Đang trong quá trình quét số điện thoại...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        clearSavedData();
        isScraping = true;
        updatePopupProgress(0);
        
        Toast.makeText(this, "Bắt đầu quét tab hiện tại...", Toast.LENGTH_SHORT).show();

        Runnable scanRunnable = new Runnable() {
            int noNewDataCount = 0;
            
            @Override
            public void run() {
                if (!isScraping) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                int previousSize = collectedPhones.size();
                
                if (rootNode != null) {
                    List<AccessibilityNodeInfo> phoneNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");
                    if (phoneNodes != null && !phoneNodes.isEmpty()) {
                        for (AccessibilityNodeInfo phoneNode : phoneNodes) {
                            if (phoneNode != null && phoneNode.getText() != null) {
                                if (phoneNode.isVisibleToUser()) {
                                    extractAndAddPhone(phoneNode.getText().toString());
                                }
                            }
                            if (phoneNode != null) phoneNode.recycle();
                        }
                    }
                    rootNode.recycle();
                }

                updatePopupProgress(collectedPhones.size());

                if (collectedPhones.size() > previousSize) {
                    noNewDataCount = 0;
                } else {
                    noNewDataCount++;
                }

                if (noNewDataCount >= 4) {
                    isScraping = false;
                    savePhonesToFile();
                    updatePopupProgress(collectedPhones.size());
                    Toast.makeText(getApplicationContext(), "Đã quét xong tab này! Tổng số: " + collectedPhones.size(), Toast.LENGTH_LONG).show();
                    return;
                }

                performVerticalSwipe(handler, this);
            }
        };

        handler.post(scanRunnable);
    }

    private void performVerticalSwipe(Handler handler, Runnable nextRunnable) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(500, 1400);
            path.lineTo(500, 500);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 200));
            
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    handler.postDelayed(nextRunnable, 300);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    handler.postDelayed(nextRunnable, 300);
                }
            }, null);
        } else {
            handler.postDelayed(nextRunnable, 400);
        }
    }

    private void extractAndAddPhone(String text) {
        if (text == null || text.isEmpty()) return;
        Pattern pattern = Pattern.compile("0[35789]\\d{8}");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String phone = matcher.group();
            if (phone != null && phone.length() == 10) {
                collectedPhones.add(phone);
            }
        }
    }

    // =========================================================================
    // PHẦN 2: TIẾN TRÌNH TỰ ĐỘNG GỌI (TÌM Ô TÌM KIẾM, DÁN VÀ BẤM GỌI)
    // =========================================================================
    public void startAutoCallingSequence() {
        if (isCallingProcessActive) return;

        loadPhonesForCalling();

        if (callQueueList.isEmpty()) {
            Toast.makeText(this, "Không có số điện thoại nào trong danh sách để gọi! Hãy quét trước.", Toast.LENGTH_LONG).show();
            return;
        }

        isCallingProcessActive = true;
        currentCallIndex = 0;
        Toast.makeText(this, "Bắt đầu tiến trình tự động gọi (" + callQueueList.size() + " số)...", Toast.LENGTH_SHORT).show();
        executeNextCallStep();
    }

    private void executeNextCallStep() {
        if (!isCallingProcessActive) return;

        if (currentCallIndex >= callQueueList.size()) {
            Toast.makeText(this, "Đã hoàn thành toàn bộ danh sách gọi điện!", Toast.LENGTH_LONG).show();
            isCallingProcessActive = false;
            return;
        }

        String targetPhone = callQueueList.get(currentCallIndex);
        currentCallIndex++;
        
        if (FloatingWidgetService.instance != null) {
            handler.post(() -> FloatingWidgetService.instance.updateProgress(currentCallIndex));
        }

        inputPhoneToSearchBox(targetPhone);
    }

    private void inputPhoneToSearchBox(String phoneNumber) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            List<AccessibilityNodeInfo> searchBoxes = new ArrayList<>();
            findEditTextByHintRecursive(rootNode, searchBoxes);

            if (searchBoxes.isEmpty()) {
                List<AccessibilityNodeInfo> byId = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/searchEditText");
                if (byId != null) searchBoxes.addAll(byId);
            }

            boolean filled = false;
            if (!searchBoxes.isEmpty()) {
                for (AccessibilityNodeInfo box : searchBoxes) {
                    if (box != null) {
                        box.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        box.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        
                        Rect rect = new Rect();
                        box.getBoundsInScreen(rect);
                        if (rect.width() > 0 && rect.height() > 0) {
                            clickAtCoordinates(rect.centerX(), rect.centerY());
                        }
                        
                        filled = true;
                        break;
                    }
                }
            }

            rootNode.recycle();

            if (filled) {
                handler.postDelayed(() -> performClipboardPasteAndSearch(phoneNumber), 400);
            } else {
                clickAtCoordinates(500, 150);
                handler.postDelayed(() -> performClipboardPasteAndSearch(phoneNumber), 400);
            }
        } else {
            handler.postDelayed(this::executeNextCallStep, 500);
        }
    }

    private void performClipboardPasteAndSearch(String phoneNumber) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Phone", phoneNumber);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            AccessibilityNodeInfo focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focusedNode != null) {
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, phoneNumber);
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                
                focusedNode.recycle();
            }
            rootNode.recycle();
        }

        clickAtCoordinates(500, 150);
        Log.d(TAG, "Đã dán số: " + phoneNumber + ", chờ 1s để app lọc kết quả...");

        handler.postDelayed(() -> verifyAndClickCallButton(phoneNumber), 1000);
    }

    private void clickAtCoordinates(float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            dispatchGesture(builder.build(), null, null);
        }
    }

    private void findEditTextByHintRecursive(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;
        
        CharSequence hint = node.getHintText();
        CharSequence className = node.getClassName();
        
        if (className != null && className.toString().contains("EditText")) {
            if (hint != null && (hint.toString().contains("Nhập mã") || hint.toString().contains("vận đơn") || hint.toString().contains("Người nhận"))) {
                results.add(node);
                return;
            } else if (results.isEmpty()) {
                results.add(node);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            findEditTextByHintRecursive(child, results);
            if (child != null) child.recycle();
        }
    }

    private void verifyAndClickCallButton(String phoneNumber) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            boolean clicked = false;
            
            // Tìm chính xác node số điện thoại theo ID chuẩn xác trong XML: com.best.android.vietcourier:id/tvPhoneNub
            List<AccessibilityNodeInfo> phoneNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");
            if (phoneNodes != null && !phoneNodes.isEmpty()) {
                for (AccessibilityNodeInfo node : phoneNodes) {
                    if (node != null && node.getText() != null && node.getText().toString().contains(phoneNumber)) {
                        if (node.isVisibleToUser()) {
                            Rect rect = new Rect();
                            node.getBoundsInScreen(rect);
                            if (rect.width() > 0 && rect.height() > 0) {
                                // Thay vì dùng performAction(ACTION_CLICK) dễ dính sự kiện cha nhảy trang,
                                // ta sử dụng tọa độ màn hình chính xác của text số điện thoại (rect.centerX(), rect.centerY()) để click
                                clickAtCoordinates(rect.centerX(), rect.centerY());
                                clicked = true;
                            }
                        }
                        node.recycle();
                        if (clicked) break;
                    } else {
                        if (node != null) node.recycle();
                    }
                }
            }

            rootNode.recycle();

            if (clicked) {
                Toast.makeText(this, "Đang gọi: " + phoneNumber, Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Không tìm thấy nút số điện thoại cho số: " + phoneNumber + ", chuyển sang số tiếp theo.");
                handler.postDelayed(this::executeNextCallStep, 400);
            }
        } else {
            handler.postDelayed(this::executeNextCallStep, 500);
        }
    }

    public void onCallFinished() {
        if (!isCallingProcessActive) return;
        handler.postDelayed(this::executeNextCallStep, 1000);
    }

    public void stopAutoCallingSequence() {
        isCallingProcessActive = false;
        handler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "Đã dừng tiến trình tự động gọi!", Toast.LENGTH_SHORT).show();
    }

    private void savePhonesToFile() {
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachSoDienThoai.txt");
            FileOutputStream fos = new FileOutputStream(file, false);
            for (String phone : collectedPhones) {
                fos.write((phone + "\n").getBytes(StandardCharsets.UTF_8));
            }
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean clearSavedData() {
        collectedPhones.clear();
        callQueueList.clear();
        updatePopupProgress(0);
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachSoDienThoai.txt");
            if (file.exists()) {
                return file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void loadPhonesForCalling() {
        callQueueList.clear();
        if (!collectedPhones.isEmpty()) {
            callQueueList.addAll(collectedPhones);
        }

        try {
            File file = new File(getExternalFilesDir(null), "DanhSachSoDienThoai.txt");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !callQueueList.contains(trimmed)) {
                        callQueueList.add(trimmed);
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePopupProgress(int count) {
        if (FloatingWidgetService.instance != null) {
            handler.post(() -> FloatingWidgetService.instance.updateProgress(count));
        }
    }
}

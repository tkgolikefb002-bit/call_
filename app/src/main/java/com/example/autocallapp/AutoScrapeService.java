package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoScrapeService extends AccessibilityService {
    private static final String TAG = "AutoScrapeService";
    public static AutoScrapeService instance;
    private boolean isScraping = false;
    private boolean isCallingProcessActive = false; 
    private final Set<String> collectedCodes = new HashSet<>();
    private final List<String> callQueueList = new ArrayList<>();
    private int currentCallIndex = 0;
    private Handler handler = new Handler(Looper.getMainLooper());

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
        Log.w(TAG, "Service bị gián đoạn (onInterrupt)!");
    }

    // =========================================================================
    // HÀM HỖ TRỢ GỠ LỖI: IN TOÀN BỘ CẤU TRÚC GIAO DIỆN MÀN HÌNH RA LOGCAT
    // =========================================================================
    private void inspectAndLogNodeTree(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;
        
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  │");
        }

        String viewId = node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "No-ID";
        String className = node.getClassName() != null ? node.getClassName().toString() : "UnknownClass";
        CharSequence text = node.getText();
        
        Log.d(TAG, String.format("%s─ [Class: %s] [ID: %s] [Text: %s] [Editable: %b] [Clickable: %b]",
                indent.toString(), className, viewId, text, node.isEditable(), node.isClickable()));

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                inspectAndLogNodeTree(child, depth + 1);
            }
        }
    }

    // =========================================================================
    // PHẦN 1: TIẾN TRÌNH QUÉT MÃ
    // =========================================================================
    public void startScraping() {
        if (isScraping) {
            Toast.makeText(this, "Đang trong quá trình quét mã, vui lòng đợi...", Toast.LENGTH_SHORT).show();
            return;
        }
        isScraping = true;
        
        loadExistingCodes();
        updatePopupProgress(collectedCodes.size());
        
        Toast.makeText(this, "Bắt đầu quét nhanh mã đơn hàng...", Toast.LENGTH_SHORT).show();

        Runnable scrapeRunnable = new Runnable() {
            int scrollAttempts = 0;
            
            @Override
            public void run() {
                if (!isScraping) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCode");
                    
                    int previousSize = collectedCodes.size();
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.getText() != null) {
                            String code = node.getText().toString().trim();
                            if (!code.isEmpty()) {
                                collectedCodes.add(code);
                            }
                        }
                    }

                    if (collectedCodes.size() > previousSize) {
                        scrollAttempts = 0; 
                        updatePopupProgress(collectedCodes.size());
                    } else {
                        scrollAttempts++;
                        if (scrollAttempts >= 1) {
                            isScraping = false;
                            saveCodesToFile();
                            updatePopupProgress(collectedCodes.size());
                            Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng: " + collectedCodes.size() + " mã.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }
                performFastScrollDownAndContinue(handler, this);
            }
        };

        handler.post(scrapeRunnable);
    }

    // =========================================================================
    // PHẦN 2: TIẾN TRÌNH TỰ ĐỘNG GỌI (ĐÃ SỬA LỖI VĂNG APP & TỐI ƯU DÁN MÃ)
    // =========================================================================
    public void startAutoCallingSequence() {
        if (isCallingProcessActive) return;

        loadExistingCodesForCalling();

        if (callQueueList.isEmpty()) {
            Toast.makeText(this, "Không có mã nào trong danh sách để gọi!", Toast.LENGTH_LONG).show();
            return;
        }

        isCallingProcessActive = true;
        currentCallIndex = 0;
        Toast.makeText(this, "Bắt đầu tự động gọi " + callQueueList.size() + " đơn hàng...", Toast.LENGTH_SHORT).show();
        executeNextCallStep();
    }

    public void stopAutoCallingSequence() {
        isCallingProcessActive = false;
        handler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "Đã dừng tiến trình gọi tự động!", Toast.LENGTH_SHORT).show();
    }

    private void loadExistingCodesForCalling() {
        callQueueList.clear();
        
        if (!collectedCodes.isEmpty()) {
            callQueueList.addAll(collectedCodes);
        }

        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
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

    private void executeNextCallStep() {
        if (!isCallingProcessActive) return;

        if (currentCallIndex >= callQueueList.size()) {
            Toast.makeText(this, "Đã hoàn thành toàn bộ danh sách đơn hàng!", Toast.LENGTH_LONG).show();
            isCallingProcessActive = false;
            return;
        }

        String targetCode = callQueueList.get(currentCallIndex);
        searchAndCallForCode(targetCode);
    }

    private void searchAndCallForCode(String targetCode) {
        if (!isCallingProcessActive) return;

        Log.d(TAG, "==================================================");
        Log.d(TAG, "BẮT ĐẦU XỬ LÝ MÃ ĐƠN: " + targetCode);

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "Màn hình chưa sẵn sàng (rootNode = null)");
            moveToNextCodeAfterDelay();
            return;
        }

        // IN CẤU TRÚC GIAO DIỆN RA LOGCAT ĐỂ KIỂM TRA MÀN HÌNH
        Log.d(TAG, "--- KIỂM TRA CẤU TRÚC MÀN HÌNH HIỆN TẠI ---");
        inspectAndLogNodeTree(rootNode, 0);

        // 1. Tìm ô tìm kiếm theo ID
        List<AccessibilityNodeInfo> searchNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/search_src_text");
        
        // 2. Nếu không thấy theo ID, tìm bất kỳ ô EditText nào
        if (searchNodes == null || searchNodes.isEmpty()) {
            Log.w(TAG, "Không tìm thấy ô theo ID search_src_text. Đang tìm ô dạng EditText...");
            searchNodes = new ArrayList<>();
            findEditTextNodes(rootNode, searchNodes);
        }

        if (searchNodes != null && !searchNodes.isEmpty()) {
            AccessibilityNodeInfo searchBox = searchNodes.get(0);
            Log.d(TAG, "Đã tìm thấy khung nhập liệu! Class: " + searchBox.getClassName() + " | ID: " + searchBox.getViewIdResourceName());

            // Thực hiện Focus & Click
            searchBox.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            searchBox.performAction(AccessibilityNodeInfo.ACTION_CLICK);

            // Dán nội dung mã đơn trực tiếp (không dùng Runnable async để tránh văng app)
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetCode);
            boolean isSetTextSuccess = searchBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

            Log.d(TAG, "Trạng thái dán văn bản [" + targetCode + "]: " + (isSetTextSuccess ? "THÀNH CÔNG" : "THẤT BẠI"));

            // Sau khi dán, chờ 800ms để app lọc kết quả rồi tiến hành lấy số gọi
            handler.postDelayed(this::findAndCallFilteredPhoneNumber, 800);

        } else {
            Log.e(TAG, "KHÔNG TÌM THẤY BẤT KỲ Ô NHẬP TÌM KIẾM NÀO TRÊN MÀN HÌNH!");
            Toast.makeText(this, "Không tìm thấy ô tìm kiếm trên màn hình!", Toast.LENGTH_SHORT).show();
            moveToNextCodeAfterDelay();
        }
    }

    private void findEditTextNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;
        
        if (node.isEditable() || "android.widget.EditText".equals(node.getClassName())) {
            results.add(node);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findEditTextNodes(child, results);
            }
        }
    }

    private void findAndCallFilteredPhoneNumber() {
        if (!isCallingProcessActive) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "RootNode null khi chuẩn bị đọc SĐT!");
            moveToNextCodeAfterDelay();
            return;
        }

        String phoneNumber = null;
        List<AccessibilityNodeInfo> phoneNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");

        if (phoneNodes != null && !phoneNodes.isEmpty()) {
            for (AccessibilityNodeInfo pNode : phoneNodes) {
                if (pNode.getText() != null) {
                    String phone = pNode.getText().toString().trim();
                    if (phone.startsWith("0") && phone.length() >= 9) {
                        phoneNumber = phone;
                        Log.d(TAG, "Đã tìm thấy SĐT sau khi lọc: " + phoneNumber);
                        break;
                    }
                }
            }
        }

        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            currentCallIndex++;
            if (FloatingWidgetService.instance != null) {
                handler.post(() -> FloatingWidgetService.instance.updateProgress(currentCallIndex));
            }
            makePhoneCall(phoneNumber);
        } else {
            Log.w(TAG, "Không tìm thấy SĐT hợp lệ cho mã đơn hàng này!");
            Toast.makeText(this, "Không tìm thấy SĐT cho mã: " + callQueueList.get(currentCallIndex), Toast.LENGTH_SHORT).show();
            moveToNextCodeAfterDelay();
        }
    }

    private void makePhoneCall(String phoneNumber) {
        try {
            Log.d(TAG, "Đang khởi chạy cuộc gọi tới SĐT: " + phoneNumber);
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + phoneNumber));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (SecurityException e) {
            Log.e(TAG, "Chưa cấp quyền CALL_PHONE!", e);
            Toast.makeText(this, "Thiếu quyền gọi điện thoại!", Toast.LENGTH_SHORT).show();
        }
    }

    public void onCallFinished() {
        if (!isCallingProcessActive) return;

        Log.d(TAG, "Cuộc gọi kết thúc, chuyển sang mã tiếp theo...");
        handler.postDelayed(this::executeNextCallStep, 1500);
    }

    private void loadExistingCodes() {
        collectedCodes.clear();
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        collectedCodes.add(trimmed);
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
            FloatingWidgetService.instance.updateProgress(count);
        }
    }

    private void performFastScrollDownAndContinue(Handler handler, Runnable nextRunnable) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(500, 1100);
            path.lineTo(500, 500);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 250));
            
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    handler.postDelayed(nextRunnable, 1000);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    handler.postDelayed(nextRunnable, 1000);
                }
            }, null);
        } else {
            handler.postDelayed(nextRunnable, 1200);
        }
    }

    private void moveToNextCodeAfterDelay() {
        currentCallIndex++;
        if (FloatingWidgetService.instance != null) {
            handler.post(() -> FloatingWidgetService.instance.updateProgress(currentCallIndex));
        }
        handler.postDelayed(this::executeNextCallStep, 800);
    }

    private void saveCodesToFile() {
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            FileOutputStream fos = new FileOutputStream(file, false);
            for (String code : collectedCodes) {
                fos.write((code + "\n").getBytes(StandardCharsets.UTF_8));
            }
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean clearSavedData() {
        collectedCodes.clear();
        callQueueList.clear();
        updatePopupProgress(0);
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists()) {
                return file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}

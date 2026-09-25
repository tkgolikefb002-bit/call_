package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
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
    public static AutoScrapeService instance;
    private boolean isScraping = false;
    private boolean isCallingProcessActive = false; // Trạng thái tiến trình gọi tự động
    private final Set<String> collectedCodes = new HashSet<>();
    private final List<String> callQueueList = new ArrayList<>();
    private int currentCallIndex = 0;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
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
        
        Toast.makeText(this, "Bắt đầu quét và cuộn mã đơn hàng...", Toast.LENGTH_SHORT).show();

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
                        if (scrollAttempts >= 3) {
                            isScraping = false;
                            saveCodesToFile();
                            updatePopupProgress(collectedCodes.size());
                            Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng: " + collectedCodes.size() + " mã.", Toast.LENGTH_LONG).show();
                            rootNode.recycle();
                            return;
                        }
                    }
                    rootNode.recycle();
                }
                performScrollDownAndContinue(handler, this);
            }
        };

        handler.post(scrapeRunnable);
    }

    // =========================================================================
    // PHẦN 2: TIẾN TRÌNH TỰ ĐỘNG GỌI ĐIỆN LẦN LƯỢT TỪNG MÃ
    // =========================================================================
    public void startAutoCallingSequence() {
        if (isCallingProcessActive) return;

        loadExistingCodesForCalling();

        if (callQueueList.isEmpty()) {
            Toast.makeText(this, "Không có mã nào trong danh sách để gọi!", Toast.LENGTH_LONG).show();
            // Đưa trạng thái nút play về lại ban đầu nếu cần
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
        currentCallIndex++;

        // Cập nhật trạng thái lên giao diện nếu muốn (hiển thị đơn thứ mấy)
        if (FloatingWidgetService.instance != null) {
            handler.post(() -> FloatingWidgetService.instance.updateProgress(currentCallIndex));
        }

        // Bước 1: Dán mã vận đơn vào khung tìm kiếm
        boolean pasted = pasteCodeIntoSearchBox(targetCode);

        // Bước 2: Chờ app logistics load kết quả (1.5 giây)
        handler.postDelayed(() -> {
            if (!isCallingProcessActive) return;

            // Bước 3: Quét số điện thoại trên màn hình (id: tvPhoneNub)
            String phoneNumber = findPhoneNumberOnScreen();

            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                // Bước 4: Gọi điện (Sẽ kích hoạt InCallService ngắt 700ms và nhảy giây)
                makePhoneCall(phoneNumber);
            } else {
                Toast.makeText(this, "Không tìm thấy SĐT cho mã: " + targetCode, Toast.LENGTH_SHORT).show();
                // Không có số thì tự động nhảy sang mã kế tiếp sau 1.5 giây
                handler.postDelayed(this::executeNextCallStep, 1500);
            }
        }, 1500);
    }

    private boolean pasteCodeIntoSearchBox(String code) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;

        boolean foundAndFilled = false;
        
        // Cách 1: Tìm node có thể chỉnh sửa trực tiếp (EditText) trên màn hình hiện tại
        AccessibilityNodeInfo editableBox = findFirstEditableNode(rootNode);
        
        // Cách 2: Nếu không tìm thấy qua thuộc tính editable, thử tìm theo hint "Nhập mã vận đơn" hoặc id khung nhập
        if (editableBox == null) {
            List<AccessibilityNodeInfo> textNodes = rootNode.findAccessibilityNodeInfosByText("Nhập mã vận đơn");
            for (AccessibilityNodeInfo node : textNodes) {
                editableBox = findEditableNode(node);
                if (editableBox != null) break;
            }
        }

        // Nếu đã tìm thấy ô nhập liệu, tiến hành điền mã và bấm focus
        if (editableBox != null) {
            // Click vào ô nhập liệu để bật bàn phím / focus
            editableBox.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            
            // Gán giá trị mã đơn vào ô
            android.os.Bundle arguments = new android.os.Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_VALUE, code);
            foundAndFilled = editableBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            
            editableBox.recycle();
        }

        rootNode.recycle();
        return foundAndFilled;
    }

    // Hàm phụ trợ tìm ô EditText đầu tiên xuất hiện trên màn hình
    private AccessibilityNodeInfo findFirstEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && "android.widget.EditText".equals(node.getClassName())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findFirstEditableNode(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    private String findPhoneNumberOnScreen() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return null;

        String phone = null;
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node.getText() != null) {
                    String text = node.getText().toString().trim();
                    if (text.startsWith("0") && text.length() >= 9) {
                        phone = text;
                        break;
                    }
                }
            }
        }
        rootNode.recycle();
        return phone;
    }

    private void makePhoneCall(String phoneNumber) {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + phoneNumber));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(this, "Thiếu quyền gọi điện thoại!", Toast.LENGTH_SHORT).show();
        }
    }

    // ĐƯỢC GỌI TỪ MyInCallService NGAY SAU KHI CUỘC GỌI KẾT THÚC (700ms)
    public void onCallFinished() {
        if (!isCallingProcessActive) return;

        // Nghỉ ngơi 2 giây sau mỗi cuộc gọi xong rồi chuyển sang mã đơn tiếp theo trong danh sách
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executeNextCallStep();
            }
        }, 2000);
    }

    // =========================================================================
    // CÁC HÀM HỖ TRỢ KHÁC
    // =========================================================================
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

    private void performScrollDownAndContinue(Handler handler, Runnable nextRunnable) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(500, 1200);
            path.lineTo(500, 500);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 400));
            
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    handler.postDelayed(nextRunnable, 1800);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    handler.postDelayed(nextRunnable, 1800);
                }
            }, null);
        } else {
            handler.postDelayed(nextRunnable, 2000);
        }
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

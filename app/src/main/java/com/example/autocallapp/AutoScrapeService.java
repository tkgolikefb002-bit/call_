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
    // PHẦN 1: TIẾN TRÌNH QUÉT MÃ (Đã có sẵn, giữ nguyên ổn định)
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
    // PHẦN 2: TIẾN TRÌNH TỰ ĐỘNG GỌI ĐIỆN LẦN LƯỢT TỪNG MÃ (MỚI THÊM)
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
        Toast.makeText(this, "Bắt đầu tiến trình tự động gọi danh sách mã...", Toast.LENGTH_SHORT).show();
        executeNextCallStep();
    }

    public void stopAutoCallingSequence() {
        isCallingProcessActive = false;
        handler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "Đã dừng tiến trình gọi tự động!", Toast.LENGTH_SHORT).show();
    }

    private void loadExistingCodesForCalling() {
        callQueueList.clear();
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
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
            Toast.makeText(this, "Đã gọi xong toàn bộ danh sách đơn hàng!", Toast.LENGTH_LONG).show();
            isCallingProcessActive = false;
            return;
        }

        String targetCode = callQueueList.get(currentCallIndex);
        currentCallIndex++;

        // Bước 1: Tự động điền mã vào khung tìm kiếm trên màn hình ứng dụng
        boolean pasted = pasteCodeIntoSearchBox(targetCode);

        // Bước 2 & 3: Thực hiện gọi điện sau khi đã điền (hoặc gọi trực tiếp mã/số điện thoại)
        handler.postDelayed(() -> {
            if (!isCallingProcessActive) return;
            makePhoneCall(targetCode);
        }, 1000);
    }

    private boolean pasteCodeIntoSearchBox(String code) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;

        boolean foundAndFilled = false;
        // Tìm ô nhập mã dựa trên gợi ý hoặc ID khung nhập trong ứng dụng Best Express
        List<AccessibilityNodeInfo> textNodes = rootNode.findAccessibilityNodeInfosByText("Nhập mã vận đơn");
        for (AccessibilityNodeInfo node : textNodes) {
            AccessibilityNodeInfo editableBox = findEditableNode(node);
            if (editableBox != null) {
                android.os.Bundle arguments = new android.os.Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_VALUE, code);
                editableBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                foundAndFilled = true;
                break;
            }
        }
        rootNode.recycle();
        return foundAndFilled;
    }

    private AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findEditableNode(node.getChild(i));
            if (result != null) return result;
        }
        return null;
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

    /**
     * Gọi hàm này khi cuộc gọi kết thúc (từ CallReceiver hoặc MyInCallService) 
     * để tự động chuyển sang mã tiếp theo sau 3 giây.
     */
    public void onCallFinished() {
        if (!isCallingProcessActive) return;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executeNextCallStep();
            }
        }, 3000);
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

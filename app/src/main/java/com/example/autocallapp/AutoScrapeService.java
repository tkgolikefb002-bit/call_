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

        if (FloatingWidgetService.instance != null) {
            handler.post(() -> FloatingWidgetService.instance.updateProgress(currentCallIndex));
        }

        // Quét trực tiếp trên màn hình, tìm đúng dòng có mã 'targetCode' và gọi số điện thoại tương ứng
        boolean foundAndCalled = findAndCallForCode(targetCode);

        if (!foundAndCalled) {
            Toast.makeText(this, "Không thấy mã trên màn hình: " + targetCode, Toast.LENGTH_SHORT).show();
            // Nếu không thấy mã này trên màn hình hiện tại, tự động chuyển sang mã tiếp theo sau 1.5 giây
            handler.postDelayed(this::executeNextCallStep, 1500);
        }
    }

    private boolean findAndCallForCode(String targetCode) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;

        String phoneNumber = null;

        // Tìm tất cả các node hiển thị mã vận đơn đang có trên màn hình
        List<AccessibilityNodeInfo> billNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCard");
        if (billNodes == null || billNodes.isEmpty()) {
            billNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCode");
        }

        if (billNodes != null) {
            for (AccessibilityNodeInfo billNode : billNodes) {
                if (billNode.getText() != null) {
                    String codeOnScreen = billNode.getText().toString().trim();
                    // So khớp chính xác mã cần tìm
                    if (targetCode.equals(codeOnScreen)) {
                        // Đã tìm thấy đúng dòng chứa mã đơn này! Lấy số điện thoại nằm trong cùng dòng đó.
                        phoneNumber = findPhoneNumberInSameRow(billNode);
                        break;
                    }
                }
            }
        }

        rootNode.recycle();

        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            makePhoneCall(phoneNumber);
            return true;
        }
        return false;
    }

    private String findPhoneNumberInSameRow(AccessibilityNodeInfo node) {
        // Duyệt ngược lên các node cha để tìm khung chứa toàn bộ item đơn hàng đó, sau đó quét lấy SĐT (tvPhoneNub)
        AccessibilityNodeInfo parent = node.getParent();
        int depth = 0;
        while (parent != null && depth < 5) {
            List<AccessibilityNodeInfo> phoneNodes = parent.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");
            if (phoneNodes != null && !phoneNodes.isEmpty()) {
                for (AccessibilityNodeInfo pNode : phoneNodes) {
                    if (pNode.getText() != null) {
                        String phone = pNode.getText().toString().trim();
                        if (phone.startsWith("0") && phone.length() >= 9) {
                            return phone;
                        }
                    }
                }
            }
            parent = parent.getParent();
            depth++;
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

    // ĐƯỢC GỌI TỪ MyInCallService NGAY SAU KHI CUỘC GỌI KẾT THÚC (700ms)
    public void onCallFinished() {
        if (!isCallingProcessActive) return;

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

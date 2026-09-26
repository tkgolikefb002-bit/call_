package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

public class AutoScrapeService extends AccessibilityService {
    private static final String TAG = "AutoScrapeService";
    public static AutoScrapeService instance;
    private boolean isScraping = false;
    private boolean isCallingProcessActive = false; 
    
    private final Set<String> collectedPhones = new LinkedHashSet<>();
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
    }

    // =========================================================================
    // PHẦN 1: QUÉT VÀ LƯU SỐ ĐIỆN THOẠI (CHỈ LẤY SỐ NGƯỜI NHẬN Ở DƯỚI CÙNG THẺ ĐƠN)
    // =========================================================================
    public void startScraping() {
        if (isScraping) {
            Toast.makeText(this, "Đang trong quá trình quét số điện thoại...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Tự động xóa sạch danh sách cũ ngay khi bắt đầu quét mới
        clearSavedData();
        
        isScraping = true;
        updatePopupProgress(0);
        
        Toast.makeText(this, "Bắt đầu quét danh sách số điện thoại mới...", Toast.LENGTH_SHORT).show();

        Runnable scrapeRunnable = new Runnable() {
            int scrollAttempts = 0;
            
            @Override
            public void run() {
                if (!isScraping) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    int previousSize = collectedPhones.size();
                    
                    // Tìm tất cả các khung container đơn hàng trên màn hình
                    List<AccessibilityNodeInfo> containerNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/llBottomParent");
                    
                    if (containerNodes != null && !containerNodes.isEmpty()) {
                        for (AccessibilityNodeInfo container : containerNodes) {
                            if (container != null) {
                                Rect containerBounds = new Rect();
                                container.getBoundsInScreen(containerBounds);
                                
                                // Kiểm tra container có hiển thị thực tế trên màn hình không
                                if (containerBounds.top >= 0 && containerBounds.bottom > 0 && containerBounds.top < 2400) {
                                    // Tìm tất cả các số điện thoại bên trong khung đơn hàng này
                                    List<AccessibilityNodeInfo> phoneNodes = container.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");
                                    
                                    if (phoneNodes != null && !phoneNodes.isEmpty()) {
                                        AccessibilityNodeInfo targetPhoneNode = null;
                                        int maxTop = -1;
                                        
                                        // Lọc lấy số nằm ở vị trí thấp nhất trong thẻ đơn (số của người nhận)
                                        for (AccessibilityNodeInfo pNode : phoneNodes) {
                                            if (pNode != null) {
                                                Rect pBounds = new Rect();
                                                pNode.getBoundsInScreen(pBounds);
                                                if (pBounds.top > maxTop) {
                                                    maxTop = pBounds.top;
                                                    targetPhoneNode = pNode;
                                                }
                                            }
                                        }
                                        
                                        if (targetPhoneNode != null && targetPhoneNode.getText() != null) {
                                            String phone = targetPhoneNode.getText().toString().trim();
                                            if (isValidPhoneNumber(phone)) {
                                                collectedPhones.add(phone);
                                            }
                                        }
                                        
                                        // Giải phóng bộ nhớ node
                                        for (AccessibilityNodeInfo pNode : phoneNodes) {
                                            if (pNode != null) pNode.recycle();
                                        }
                                    }
                                }
                                container.recycle();
                            }
                        }
                    }

                    if (collectedPhones.size() > previousSize) {
                        scrollAttempts = 0; 
                        updatePopupProgress(collectedPhones.size());
                    } else {
                        scrollAttempts++;
                        // Nếu cuộn 2 lần không tìm thấy số mới -> Dừng quét và lưu file
                        if (scrollAttempts >= 2) {
                            isScraping = false;
                            savePhonesToFile();
                            updatePopupProgress(collectedPhones.size());
                            Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng số điện thoại: " + collectedPhones.size(), Toast.LENGTH_LONG).show();
                            rootNode.recycle();
                            return;
                        }
                    }
                    rootNode.recycle();
                }
                
                performFastScrollDownAndContinue(handler, this);
            }
        };

        handler.post(scrapeRunnable);
    }

    private boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;
        phone = phone.replaceAll("\\s+", "").replaceAll("-", "");
        return phone.matches("^0\\d{8,10}$");
    }

    // =========================================================================
    // PHẦN 2: TIẾN TRÌNH TỰ ĐỘNG GỌI CÁC SỐ ĐÃ LƯU
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
        Toast.makeText(this, "Bắt đầu tự động gọi " + callQueueList.size() + " số điện thoại...", Toast.LENGTH_SHORT).show();
        executeNextCallStep();
    }

    public void stopAutoCallingSequence() {
        isCallingProcessActive = false;
        handler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "Đã dừng tiến trình gọi tự động!", Toast.LENGTH_SHORT).show();
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

        Log.d(TAG, "Đang gọi số: " + targetPhone);
        makePhoneCall(targetPhone);
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
            moveToNextCodeAfterDelay();
        }
    }

    public void onCallFinished() {
        if (!isCallingProcessActive) return;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executeNextCallStep();
            }
        }, 1500);
    }

    private void moveToNextCodeAfterDelay() {
        handler.postDelayed(this::executeNextCallStep, 1000);
    }

    private void loadExistingPhones() {
        collectedPhones.clear();
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachSoDienThoai.txt");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        collectedPhones.add(trimmed);
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
}

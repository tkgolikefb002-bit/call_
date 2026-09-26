package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
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
    // QUÉT TỐC ĐỘ CAO 100MS: SỬ DỤNG SCROLL FORWARD TRỰC TIẾP TRÊN RECYCLERVIEW
    // =========================================================================
    public void startScraping() {
        if (isScraping) {
            Toast.makeText(this, "Đang trong quá trình quét số điện thoại...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        clearSavedData();
        isScraping = true;
        updatePopupProgress(0);
        
        Toast.makeText(this, "Bắt đầu quét tốc độ cao 100ms...", Toast.LENGTH_SHORT).show();

        Runnable ultraFastRunnable = new Runnable() {
            int noNewDataCount = 0;
            
            @Override
            public void run() {
                if (!isScraping) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                int previousSize = collectedPhones.size();
                
                if (rootNode != null) {
                    // 1. Quét toàn bộ số điện thoại đang hiển thị
                    List<AccessibilityNodeInfo> phoneNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");
                    if (phoneNodes != null && !phoneNodes.isEmpty()) {
                        for (AccessibilityNodeInfo phoneNode : phoneNodes) {
                            if (phoneNode != null && phoneNode.getText() != null) {
                                extractAndAddPhone(phoneNode.getText().toString());
                            }
                            if (phoneNode != null) phoneNode.recycle();
                        }
                    }

                    // 2. Tìm container danh sách (RecyclerView hoặc ScrollView) để ra lệnh cuộn trực tiếp
                    AccessibilityNodeInfo scrollableContainer = findScrollableNode(rootNode);
                    if (scrollableContainer != null) {
                        scrollableContainer.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                        scrollableContainer.recycle();
                    }
                    
                    rootNode.recycle();
                }

                updatePopupProgress(collectedPhones.size());

                // Kiểm tra tiến độ dữ liệu tăng lên
                if (collectedPhones.size() > previousSize) {
                    noNewDataCount = 0;
                } else {
                    noNewDataCount++;
                }

                // Nếu quét liên tục mà 4 nhịp không còn tăng số mới -> Đã chạm đáy
                if (noNewDataCount >= 4) {
                    isScraping = false;
                    savePhonesToFile();
                    updatePopupProgress(collectedPhones.size());
                    Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng số điện thoại: " + collectedPhones.size(), Toast.LENGTH_LONG).show();
                    return;
                }

                // Độ trễ siêu tốc 100ms đúng như các app auto chuyên nghiệp
                handler.postDelayed(this, 1000); // Bạn có thể giảm xuống 100ms nếu máy và app mượt
            }
        };

        handler.post(ultraFastRunnable);
    }

    // Hàm đệ quy tìm khung cuộn (RecyclerView / ScrollView)
    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        if (root.isScrollable()) {
            return root;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            AccessibilityNodeInfo result = findScrollableNode(child);
            if (result != null) {
                if (child != result) child.recycle();
                return result;
            }
            if (child != null) child.recycle();
        }
        return null;
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
    // PHẦN 2: TIẾN TRÌNH TỰ ĐỘNG GỌI CÁC SỐ ĐÃ LƯU
    // =========================================================================
    public void startAutoCallingSequence() {
        if (isCallingProcessActive) return;

        loadPhonesForCalling();

        if (callQueueList.isEmpty()) {
            Toast.makeText(this, "Không có số điện thoại nào trong danh sách để gọi! Hãy quét trước.", Toast.LENGTH_SHORT).show();
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
        handler.postDelayed(this::executeNextCallStep, 1500);
    }

    private void moveToNextCodeAfterDelay() {
        handler.postDelayed(this::executeNextCallStep, 1000);
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
}

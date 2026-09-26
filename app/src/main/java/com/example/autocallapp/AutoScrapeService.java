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
        isCallingProcessActive = false;
    }

    // =========================================================================
    // QUÉT TĨNH 1 LẦN TRÊN MÀN HÌNH HIỆN TẠI (KHÔNG CUỘN, KHÔNG DÍNH RÁC CŨ)
    // =========================================================================
    public void startScraping() {
        clearSavedData(); // Xóa sạch dữ liệu cũ trong bộ nhớ và file txt
        
        Toast.makeText(this, "Đang quét màn hình hiện tại...", Toast.LENGTH_SHORT).show();

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            traverseAndExtractPhones(rootNode);
            rootNode.recycle();
        }

        savePhonesToFile();
        updatePopupProgress(collectedPhones.size());
        
        Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng số: " + collectedPhones.size(), Toast.LENGTH_LONG).show();
    }

    private void traverseAndExtractPhones(AccessibilityNodeInfo node) {
        if (node == null) return;

        // Ưu tiên quét ID chuẩn của số điện thoại nếu app cung cấp
        if (node.getViewIdResourceName() != null && node.getViewIdResourceName().endsWith("tvPhoneNub")) {
            if (node.getText() != null) {
                extractAndAddPhones(node.getText().toString());
            }
        }

        // Quét dự phòng qua Text và ContentDescription của toàn bộ node trên màn hình hiện tại
        if (node.getText() != null) {
            extractAndAddPhones(node.getText().toString());
        }
        if (node.getContentDescription() != null) {
            extractAndAddPhones(node.getContentDescription().toString());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseAndExtractPhones(child);
                child.recycle();
            }
        }
    }

    // Lọc chuẩn xác tuyệt đối số di động VN (bắt đầu bằng 03, 05, 07, 08, 09 và đủ 10 số)
    private void extractAndAddPhones(String text) {
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
            Toast.makeText(this, "Không có số điện thoại nào để gọi! Hãy quét trước.", Toast.LENGTH_LONG).show();
            return;
        }

        isCallingProcessActive = true;
        currentCallIndex = 0;
        Toast.makeText(this, "Bắt đầu tự động gọi " + callQueueList.size() + " số...", Toast.LENGTH_SHORT).show();
        executeNextCallStep();
    }

    public void stopAutoCallingSequence() {
        isCallingProcessActive = false;
        handler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "Đã dừng gọi tự động!", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Đã hoàn thành danh sách gọi điện!", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Thiếu quyền gọi điện!", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::executeNextCallStep, 1000);
        }
    }

    public void onCallFinished() {
        if (!isCallingProcessActive) return;
        handler.postDelayed(this::executeNextCallStep, 1500);
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

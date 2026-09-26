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
    // QUÉT MÀN HÌNH HIỆN TẠI VỚI HÀM LỌC SỐ ĐIỆN THOẠI LINH HOẠT
    // =========================================================================
    public void startScraping() {
        clearSavedData();
        
        Toast.makeText(this, "Đang quét màn hình hiện tại...", Toast.LENGTH_SHORT).show();

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            traverseAndCollectPhones(rootNode);
            rootNode.recycle();
        }

        savePhonesToFile();
        updatePopupProgress(collectedPhones.size());
        
        Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng số điện thoại: " + collectedPhones.size(), Toast.LENGTH_LONG).show();
    }

    // Hàm đệ quy duyệt qua tất cả các node con trên màn hình
    private void traverseAndCollectPhones(AccessibilityNodeInfo node) {
        if (node == null) return;

        if (node.getText() != null) {
            String text = node.getText().toString().trim();
            String cleanedPhone = extractPhoneNumber(text);
            if (cleanedPhone != null) {
                collectedPhones.add(cleanedPhone);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseAndCollectPhones(child);
                child.recycle();
            }
        }
    }

    // Hàm trích xuất số điện thoại linh hoạt, loại bỏ khoảng trắng và ký tự thừa
    private String extractPhoneNumber(String text) {
        if (text == null) return null;
        // Loại bỏ khoảng trắng, dấu gạch ngang hoặc ký tự lạ nếu có
        String cleaned = text.replaceAll("[^0-9]", "");
        
        // Kiểm tra xem chuỗi số có bắt đầu bằng số 0 và có độ dài từ 10 đến 11 số không (hoặc 9-11 tùy nhà mạng)
        if (cleaned.startsWith("0") && cleaned.length() >= 9 && cleaned.length() <= 11) {
            return cleaned;
        }
        return null;
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

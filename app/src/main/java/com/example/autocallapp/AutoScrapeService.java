package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
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
    // QUY TRÌNH CHUẨN: QUÉT DỮ LIỆU HIỆN TẠI TRƯỚC -> SAU ĐÓ MỚI CUỘN XUỐNG
    // =========================================================================
    public void startScraping() {
        if (isScraping) {
            Toast.makeText(this, "Đang trong quá trình quét số điện thoại...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        clearSavedData();
        isScraping = true;
        updatePopupProgress(0);
        
        Toast.makeText(this, "Bắt đầu quét danh sách đơn hàng...", Toast.LENGTH_SHORT).show();

        Runnable scrapeRunnable = new Runnable() {
            int scrollAttempts = 0;
            
            @Override
            public void run() {
                if (!isScraping) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    int previousSize = collectedPhones.size();
                    
                    // BƯỚC 1: TÌM VÀ QUÉT CÁC SỐ TRÊN MÀN HÌNH HIỆN TẠI TRƯỚC
                    List<AccessibilityNodeInfo> billNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCode");
                    
                    if (billNodes != null && !billNodes.isEmpty()) {
                        for (AccessibilityNodeInfo billNode : billNodes) {
                            if (billNode != null && billNode.isVisibleToUser()) {
                                AccessibilityNodeInfo parentCard = getOrderCardContainer(billNode);
                                if (parentCard != null) {
                                    List<AccessibilityNodeInfo> phoneNodes = parentCard.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");
                                    if (phoneNodes != null) {
                                        for (AccessibilityNodeInfo phoneNode : phoneNodes) {
                                            if (phoneNode != null && phoneNode.isVisibleToUser() && phoneNode.getText() != null) {
                                                String rawText = phoneNode.getText().toString();
                                                extractAndAddPhone(rawText);
                                            }
                                            if (phoneNode != null) phoneNode.recycle();
                                        }
                                    }
                                    parentCard.recycle();
                                }
                            }
                        }
                    }

                    // Cập nhật lại giao diện popup số lượng đã quét được
                    if (collectedPhones.size() > previousSize) {
                        scrollAttempts = 0; 
                        updatePopupProgress(collectedPhones.size());
                        rootNode.recycle();
                        
                        // Nếu vừa tìm thấy số mới, chờ 1 giây rồi tiếp tục cuộn xuống tìm tiếp
                        handler.postDelayed(this, 1000);
                    } else {
                        scrollAttempts++;
                        // Nếu cuộn 2 lần liên tiếp không thấy số mới -> Đã đến cuối danh sách
                        if (scrollAttempts >= 2) {
                            isScraping = false;
                            savePhonesToFile();
                            updatePopupProgress(collectedPhones.size());
                            Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng số điện thoại: " + collectedPhones.size(), Toast.LENGTH_LONG).show();
                            rootNode.recycle();
                            return;
                        }
                        rootNode.recycle();
                        
                        // BƯỚC 2: SAU KHI ĐÃ QUÉT XONG HIỆN TẠI MÀ KHÔNG THẤY THÊM, THỰC HIỆN CUỘN XUỐNG
                        performFastScrollDownAndContinue(handler, this);
                    }
                } else {
                    // Nếu không bắt được root node, thử lại sau 1 giây
                    handler.postDelayed(this, 1000);
                }
            }
        };

        // Chạy ngay lượt quét đầu tiên cho màn hình hiện tại
        handler.post(scrapeRunnable);
    }

    private AccessibilityNodeInfo getOrderCardContainer(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 4; i++) {
            if (current == null) break;
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null) break;
            
            List<AccessibilityNodeInfo> testPhone = parent.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvPhoneNub");
            if (testPhone != null && !testPhone.isEmpty()) {
                for(AccessibilityNodeInfo p : testPhone) p.recycle();
                if (current != node) current.recycle();
                return parent;
            }
            
            if (current != node) current.recycle();
            current = parent;
        }
        return node.getParent();
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
                    // Chờ 800ms sau khi cuộn để RecyclerView kịp render dữ liệu mới rồi mới chạy tiếp vòng lặp quét
                    handler.postDelayed(nextRunnable, 800);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    handler.postDelayed(nextRunnable, 800);
                }
            }, null);
        } else {
            handler.postDelayed(nextRunnable, 1000);
        }
    }
}

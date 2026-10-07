package com.example.autocrape;

import com.example.autocrape.AutoScrapeService;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoScrapeService extends AccessibilityService {

    private static final String TAG = "AutoScrapeService";
    
    // Khai báo instance tĩnh để các service khác có thể gọi tới
    public static AutoScrapeService instance;

    private boolean isRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Lưu trữ danh sách mã đã quét (tránh trùng lặp)
    private final Set<String> scrapedCodesSet = new HashSet<>();
    // Danh sách các mã sau khi bóc tách để xử lý tuần tự
    private final List<String> extractedQueue = new ArrayList<>();
    
    private int currentIndex = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this; // Gán instance khi service được kết nối thành công
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null; // Xóa instance khi service bị hủy
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Có thể để trống hoặc dùng nếu cần bắt sự kiện thay đổi giao diện
    }

    @Override
    public void onInterrupt() {
        stopScraping();
    }

    // --- 1. BẮT ĐẦU QUÁ TRÌNH VỪA CUỘN VỪA LƯU MÃ ---
    public void startScrapeAndScrollLoop() {
        if (isRunning) return;
        isRunning = true;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    // Quét toàn bộ mã hiển thị trên màn hình hiện tại
                    collectBillCodes(rootNode);
                    rootNode.recycle();
                }

                // Thực hiện cuộn xuống để lấy tiếp các mã phía dưới
                boolean scrolled = performScrollDown();

                if (scrolled) {
                    // Đợi một nhịp ngắn (ví dụ 1.2 giây) cho nội dung load xong rồi lặp lại
                    handler.postDelayed(this, 1200);
                } else {
                    // Hết trang hoặc không cuộn được nữa -> Dừng và chuyển sang giai đoạn bóc tách
                    Log.d(TAG, "Đã cuộn đến cuối trang. Tổng số mã quét được: " + scrapedCodesSet.size());
                    stopScraping();
                    
                    // Đưa toàn bộ vào hàng đợi để sẵn sàng bóc từng mã
                    prepareQueue();
                }
            }
        });
    }

    public void stopScraping() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    // --- 2. ĐỆ QUY TÌM VÀ LƯU MÃ ĐƠN (tvBillCode) ---
    private void collectBillCodes(AccessibilityNodeInfo node) {
        if (node == null) return;

        // Kiểm tra đúng ID chứa mã vận đơn của app VietCourier
        String viewId = node.getViewIdResourceName();
        if (viewId != null && viewId.endsWith("tvBillCode")) {
            CharSequence text = node.getText();
            if (text != null) {
                String code = text.toString().trim();
                if (!code.isEmpty() && !scrapedCodesSet.contains(code)) {
                    scrapedCodesSet.add(code);
                    Log.d(TAG, "Đã lưu mã mới: " + code);
                }
            }
        }

        // Duyệt các node con
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            collectBillCodes(child);
            if (child != null) {
                child.recycle();
            }
        }
    }

    // --- 3. HÀM CUỘN TRANG (GESTURE SWIPE) ---
    private boolean performScrollDown() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.accessibilityservice.GestureDescription.Builder builder = new android.accessibilityservice.GestureDescription.Builder();
            android.graphics.Path path = new android.graphics.Path();
            
            // Tọa độ vuốt từ dưới lên trên (điều chỉnh theo màn hình thiết bị của bạn)
            path.moveTo(500, 1800);
            path.lineTo(500, 600);
            
            builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 400));
            dispatchGesture(builder.build(), null, null);
            return true;
        }
        return false;
    }

    // --- 4. BÓC TỪNG MÃ RA KHỎI HÀNG ĐỢI ---
    private void prepareQueue() {
        extractedQueue.clear();
        extractedQueue.addAll(scrapedCodesSet);
        currentIndex = 0;
        Log.d(TAG, "Đã chuẩn bị xong hàng đợi với " + extractedQueue.size() + " mã.");
    }

    // Gọi hàm này để lấy ra mã tiếp theo trong danh sách
    public String getNextCodeToProcess() {
        if (extractedQueue.isEmpty() || currentIndex >= extractedQueue.size()) {
            return null; // Hết mã
        }
        String code = extractedQueue.get(currentIndex);
        currentIndex++;
        Log.d(TAG, "Bóc thành công mã tiếp theo: " + code + " (Thứ tự: " + currentIndex + "/" + extractedQueue.size() + ")");
        return code;
    }
}

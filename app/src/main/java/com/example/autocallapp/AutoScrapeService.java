package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoScrapeService extends AccessibilityService {
    public static AutoScrapeService instance;
    private boolean isScraping = false;
    private final Set<String> collectedCodes = new HashSet<>();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Không cần xử lý sự kiện liên tục ở đây, dùng vòng lặp chủ động khi bấm nút
    }

    @Override
    public void onInterrupt() {
        isScraping = false;
    }

    // Hàm bắt đầu quá trình quét và tự động lướt màn hình khi bấm nút kính lúp
    public void startScraping() {
        if (isScraping) {
            Toast.makeText(this, "Đang trong quá trình quét mã, vui lòng đợi...", Toast.LENGTH_SHORT).show();
            return;
        }
        isScraping = true;
        collectedCodes.clear();
        
        Toast.makeText(this, "Bắt đầu quét và cuộn mã đơn hàng...", Toast.LENGTH_SHORT).show();

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            int scrollAttempts = 0;
            
            @Override
            public void run() {
                if (!isScraping) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    // Tìm tất cả các mã đơn hàng theo resource-id của Best Express
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

                    // Nếu số lượng mã không tăng sau khi cuộn, nghĩa là đã đến cuối trang
                    if (collectedCodes.size() == previousSize) {
                        scrollAttempts++;
                        if (scrollAttempts >= 3) { // Thử lại 3 lần chắc chắn hết trang thì dừng
                            isScraping = false;
                            saveCodesToFile();
                            Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng: " + collectedCodes.size() + " mã.", Toast.LENGTH_LONG).show();
                            rootNode.recycle();
                            return;
                        }
                    } else {
                        scrollAttempts = 0; // Reset lại nếu vẫn tìm thấy mã mới
                    }

                    rootNode.recycle();
                }

                // TỰ ĐỘNG KÉO MÀN HÌNH XUỐNG DƯỚI ĐỂ TÌM MÃ TIẾP THEO
                performScrollDown();

                // Lặp lại sau 1.5 giây để chờ app load kịp dữ liệu mới
                handler.postDelayed(this, 1500);
            }
        });
    }

    private void performScrollDown() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            // Tọa độ vuốt dọc màn hình từ dưới lên trên (giữa màn hình điện thoại)
            path.moveTo(500, 1500);
            path.lineTo(500, 500);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 400));
            dispatchGesture(builder.build(), null, null);
        }
    }

    private void saveCodesToFile() {
        try {
            // Lưu file danh sách mã đơn vào bộ nhớ ngoài của ứng dụng
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
}

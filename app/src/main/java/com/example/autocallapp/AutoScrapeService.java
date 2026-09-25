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
    }

    @Override
    public void onInterrupt() {
        isScraping = false;
    }

    public void startScraping() {
        if (isScraping) {
            Toast.makeText(this, "Đang trong quá trình quét mã, vui lòng đợi...", Toast.LENGTH_SHORT).show();
            return;
        }
        isScraping = true;
        collectedCodes.clear();
        
        Toast.makeText(this, "Bắt đầu quét và cuộn mã đơn hàng...", Toast.LENGTH_SHORT).show();

        Handler handler = new Handler(Looper.getMainLooper());
        
        // Runnable thực hiện nhiệm vụ quét mã tại chỗ
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

                    if (collectedCodes.size() == previousSize) {
                        scrollAttempts++;
                        if (scrollAttempts >= 4) { // Tăng nhẹ số lần thử để tránh dừng quá sớm khi mạng chậm
                            isScraping = false;
                            saveCodesToFile();
                            Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng: " + collectedCodes.size() + " mã.", Toast.LENGTH_LONG).show();
                            rootNode.recycle();
                            return;
                        }
                    } else {
                        scrollAttempts = 0; // Reset lại nếu quét thêm được mã mới
                    }

                    rootNode.recycle();
                }

                // Thực hiện vuốt màn hình để sang trang/danh sách tiếp theo
                performScrollDownAndContinue(handler, this);
            }
        };

        // Bắt đầu vòng lặp quét ngay lập tức lần đầu tiên
        handler.post(scrapeRunnable);
    }

    private void performScrollDownAndContinue(Handler handler, Runnable nextRunnable) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            // Tọa độ vuốt từ dưới lên trên (giữa màn hình)
            path.moveTo(500, 1600);
            path.lineTo(500, 400);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 400));
            
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    // Sau khi vuốt xong, đợi thêm 1 giây để app logistics render/tải dữ liệu rồi mới chạy tiếp
                    handler.postDelayed(nextRunnable, 1000);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    // Nếu lỡ bị hủy cử chỉ, vẫn thử cho chạy tiếp sau 1 giây
                    handler.postDelayed(nextRunnable, 1000);
                }
            }, null);
        } else {
            // Hỗ trợ dự phòng nếu máy Android quá cũ không hỗ trợ Gesture
            handler.postDelayed(nextRunnable, 1500);
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

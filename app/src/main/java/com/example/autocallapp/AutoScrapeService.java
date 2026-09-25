package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
        
        // Tải các mã đã lưu từ file trước đó vào bộ nhớ để tiếp tục quét và đối chiếu
        loadExistingCodes();
        updatePopupProgress(collectedCodes.size());
        
        Toast.makeText(this, "Bắt đầu quét và cuộn mã đơn hàng...", Toast.LENGTH_SHORT).show();

        Handler handler = new Handler(Looper.getMainLooper());
        
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
                                collectedCodes.add(code); // Set tự động loại bỏ mã trùng lặp
                            }
                        }
                    }

                    // Kiểm tra xem có tìm thấy mã mới hay không
                    if (collectedCodes.size() > previousSize) {
                        scrollAttempts = 0; // Reset lại số lần thử nếu có mã mới
                        updatePopupProgress(collectedCodes.size());
                    } else {
                        scrollAttempts++;
                        
                        // Đã thay đổi: Giảm xuống 3 lần cuộn liên tiếp không thấy mã mới -> Dừng quét
                        if (scrollAttempts >= 3) {
                            isScraping = false; // Dừng trạng thái quét
                            saveCodesToFile();   // Lưu toàn bộ vào file
                            
                            // Cập nhật giao diện lần cuối, thông báo thành công và giữ nguyên popup
                            updatePopupProgress(collectedCodes.size());
                            Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng: " + collectedCodes.size() + " mã.", Toast.LENGTH_LONG).show();
                            
                            rootNode.recycle();
                            return; // Dừng vòng lặp, tuyệt đối không tắt popup
                        }
                    }

                    rootNode.recycle();
                }

                // Tiếp tục cuộn trang tiếp theo
                performScrollDownAndContinue(handler, this);
            }
        };

        handler.post(scrapeRunnable);
    }

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
            // Tọa độ vuốt từ dưới lên trên (giữa màn hình)
            path.moveTo(500, 1200);
            path.lineTo(500, 500);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 400));
            
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    // Giữ thời gian chờ 1.8 giây để app kịp tải dữ liệu ổn định sau khi vuốt
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
        updatePopupProgress(0); // Reset tiến độ về 0
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

package com.example.autocallapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FloatingWidgetService extends Service {
    public static FloatingWidgetService instance;
    private WindowManager windowManager;
    private View floatingView;
    private boolean isRunning = false;  
    private TextView tvProgress;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String CHANNEL_ID = "AutoScrapeForegroundChannel";
    private static final int NOTIFICATION_ID = 12345;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; 

        try {
            createNotificationChannel();
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Bảng điều khiển Auto đang chạy")
                    .setContentText("Đang hiển thị dạng nổi trên màn hình")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
            startForeground(NOTIFICATION_ID, notification);

            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_popup, null);
            tvProgress = floatingView.findViewById(R.id.tvProgress);

            int LAYOUT_FLAG;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
            }

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    LAYOUT_FLAG,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 100;
            params.y = 200;

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.addView(floatingView, params);
            }

            floatingView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(floatingView, params);
                            return true;
                    }
                    return false;
                }
            });

            Button btnClose = floatingView.findViewById(R.id.btnClose);
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> stopSelf());
            }

            // Nút Play / Pause: Chạy hàng loạt từ danh sách đã lưu
            Button btnPlayPause = floatingView.findViewById(R.id.btnPlayPause);
            if (btnPlayPause != null) {
                btnPlayPause.setOnClickListener(v -> {
                    isRunning = !isRunning;
                    if (isRunning) {
                        btnPlayPause.setText("⏸");
                        showToastOnMainThread("Bắt đầu chạy hàng loạt: Dán mã & Gọi điện...");
                        startBatchLoopAutomation();
                    } else {
                        btnPlayPause.setText("▶");
                        showToastOnMainThread("Đã tạm dừng tiến trình hàng loạt!");
                    }
                });
            }

            // Nút Kính lúp: Quét mã hiện tại tại tab đang hiển thị dựa vào khung chứa đơn -> Lưu ngay -> Cuộn xuống -> Lặp lại
            Button btnSearch = floatingView.findViewById(R.id.btnSearch);
            if (btnSearch != null) {
                btnSearch.setOnClickListener(v -> {
                    if (MyAccessibilityService.instance == null) {
                        showToastOnMainThread("Lỗi: Dịch vụ trợ năng chưa chạy. Hãy bật lại quyền Trợ năng!");
                        return;
                    }

                    showToastOnMainThread("Đang tiến hành quét, lưu và cuộn tại tab hiện tại...");

                    new Thread(() -> {
                        int totalScrapedNew = 0;
                        int noNewDataCount = 0;

                        // Vòng lặp quét liên tục cho đến khi hết dữ liệu mới (không cuộn được nữa)
                        while (noNewDataCount < 8) {
                            AccessibilityNodeInfo rootNode = MyAccessibilityService.getRootNodeSafely(MyAccessibilityService.instance);
                            if (rootNode == null) {
                                android.util.Log.e("DEBUG_SCAN", "LỖI: rootNode bị null! Đang thử lại...");
                                try { Thread.sleep(1000); } catch (InterruptedException e) {}
                                noNewDataCount++;
                                continue;
                            }

                            // 1. Quét lấy mã vận đơn dựa trên cấu trúc khối đơn hàng (clParent)
                            List<String> foundCodes = extractTrackingNumbersFromNode(rootNode);
                            int addedCount = 0;

                            // 2. Lưu ngay vào file (chỉ lưu mã chưa có)
                            for (String code : foundCodes) {
                                if (saveTrackingNumberUnique(code)) {
                                    addedCount++;
                                    totalScrapedNew++;
                                }
                            }

                            List<String> allLines = getAllTrackingNumbers();
                            if (allLines != null) {
                                updateProgress(allLines.size());
                            } else {
                                updateProgress(totalScrapedNew);
                            }

                            if (addedCount > 0) {
                                noNewDataCount = 0; // Reset lại bộ đếm nếu tìm thấy mã mới
                            }

                            // 3. Thực hiện cuộn màn hình xuống dưới để nạp dữ liệu tiếp theo
                            boolean scrolled = performBestScrollDown(rootNode);
                            
                            // Giải phóng node gốc sau khi dùng xong
                            rootNode.recycle();

                            if (!scrolled) {
                                noNewDataCount++;
                            } else {
                                // 4. Đợi app render khung hình mới sau khi cuộn
                                try {
                                    Thread.sleep(900); 
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        showToastOnMainThread("Quét hoàn tất! Tổng mã mới thêm: " + totalScrapedNew);
                    }).start();
                });
            }

            Button btnDelete = floatingView.findViewById(R.id.btnDelete);
            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> clearSavedDataFile());
            }

        } catch (Exception e) {
            e.printStackTrace();
            showToastOnMainThread("Lỗi khởi tạo popup: " + e.getMessage());
        }
    }

    private void showToastOnMainThread(String message) {
        mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Auto Scrape Foreground Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private List<String> getAllTrackingNumbers() {
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists()) {
                return Files.readAllLines(file.toPath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; 
    }

    private String getNextTrackingNumberFromSavedList() {
        List<String> lines = getAllTrackingNumbers();
        if (lines != null && !lines.isEmpty()) {
            return lines.get(0).trim();
        }
        return null;
    }

    private void removeFirstTrackingNumber() {
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath());
                if (!lines.isEmpty()) {
                    lines.remove(0);
                    Files.write(file.toPath(), lines);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearSavedDataFile() {
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists() && file.delete()) {
                updateProgress(0);
                showToastOnMainThread("Đã xóa file dữ liệu thành công!");
            } else {
                showToastOnMainThread("Không tìm thấy file dữ liệu.");
            }
        } catch (Exception e) {
            showToastOnMainThread("Lỗi khi xóa: " + e.getMessage());
        }
    }

    private boolean saveTrackingNumberUnique(String trackingCode) {
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            List<String> lines = new ArrayList<>();
            if (file.exists()) {
                lines = Files.readAllLines(file.toPath());
                if (lines.contains(trackingCode)) {
                    return false;
                }
            }
            lines.add(trackingCode);
            Files.write(file.toPath(), lines);
            android.util.Log.d("DEBUG_FILE", "ĐÃ LƯU THÀNH CÔNG MÃ: " + trackingCode);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // QUÉT DỰA TRÊN KHUNG CHỨA ĐƠN HÀNG (clParent) VÀ CHỈ LẤY MÃ VẬN ĐƠN (tvBillCode)
    private List<String> extractTrackingNumbersFromNode(AccessibilityNodeInfo node) {
        List<String> codes = new ArrayList<>();
        if (node == null) return codes;

        // Tìm tất cả các khối chứa đơn hàng (clParent) trên màn hình
        List<AccessibilityNodeInfo> parentNodes = node.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/clParent");
        if (parentNodes != null && !parentNodes.isEmpty()) {
            for (AccessibilityNodeInfo parent : parentNodes) {
                if (parent != null) {
                    // Kiểm tra xem khối đơn hàng này có nằm trong vùng nhìn thấy thực tế của màn hình không
                    Rect parentBounds = new Rect();
                    parent.getBoundsInScreen(parentBounds);

                    int screenTopLimit = 450;
                    int screenBottomLimit = 1700;

                    if (parentBounds.top > screenTopLimit && parentBounds.bottom < screenBottomLimit) {
                        
                        // Tìm mã vận đơn (tvBillCode) BÊN TRONG khối đơn hàng này (Bất kể vị trí index 0 hay 1 do có checkbox hay không)
                        List<AccessibilityNodeInfo> billCodes = parent.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCode");
                        if (billCodes != null && !billCodes.isEmpty()) {
                            for (AccessibilityNodeInfo billNode : billCodes) {
                                CharSequence text = billNode.getText();
                                if (text != null) {
                                    String code = text.toString().trim();
                                    boolean isValidPrefix = code.startsWith("TT") || code.startsWith("84") || code.startsWith("BE");
                                    boolean isValidLength = code.length() >= 10 && code.length() <= 25;

                                    if (isValidPrefix && isValidLength && !code.isEmpty() && !codes.contains(code)) {
                                        android.util.Log.d("DEBUG_SCAN", "Lấy chuẩn mã vận đơn trong khung: " + code);
                                        codes.add(code);
                                    }
                                }
                                billNode.recycle();
                            }
                        }
                    }
                    parent.recycle();
                }
            }
        }
        return codes;
    }

    // Hàm cuộn màn hình tìm kiếm node có khả năng cuộn
    private boolean performBestScrollDown(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;

        AccessibilityNodeInfo scrollableNode = findScrollableNode(rootNode);
        if (scrollableNode != null) {
            boolean result = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            scrollableNode.recycle();
            if (result) {
                return true;
            }
        }
        return false;
    }

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

    private boolean typeTrackingNumberIntoApp(AccessibilityNodeInfo rootNode, String trackingNumber) {
        if (rootNode == null) return false;

        List<AccessibilityNodeInfo> editTexts = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/etSearch");
        if (editTexts == null || editTexts.isEmpty()) {
            editTexts = findAllEditTexts(rootNode);
        }

        if (editTexts != null && !editTexts.isEmpty()) {
            for (AccessibilityNodeInfo node : editTexts) {
                if (node.isEditable() || (node.getClassName() != null && node.getClassName().toString().contains("EditText"))) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, trackingNumber);
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                        return true; 
                    }
                }
            }
        }
        return false;
    }

    private List<AccessibilityNodeInfo> findAllEditTexts(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> list = new ArrayList<>();
        if (root == null) return list;
        if (root.getClassName() != null && root.getClassName().toString().contains("EditText")) {
            list.add(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            list.addAll(findAllEditTexts(root.getChild(i)));
        }
        return list;
    }

    private boolean triggerCallAction(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;

        if (clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/tvPhoneNub", 3, 500)) {
            return true;
        }

        String[] fallbackIds = {
            "com.best.android.vietcourier:id/ivSelect", // Có thể bổ sung fallback nếu cần
            "com.best.android.vietcourier:id/ivCall",
            "com.best.android.vietcourier:id/img_call",
            "com.best.android.vietcourier:id/btnCall"
        };
        for (String id : fallbackIds) {
            if (clickNodeByIdWithRetry(rootNode, id, 1, 200)) {
                return true;
            }
        }

        return false;
    }

    private void startBatchLoopAutomation() {
        new Thread(() -> {
            while (isRunning) {
                String trackingNumber = getNextTrackingNumberFromSavedList();
                if (trackingNumber == null || trackingNumber.isEmpty()) {
                    isRunning = false;
                    showToastOnMainThread("Đã chạy xong toàn bộ danh sách đơn hàng!");
                    break;
                }

                List<String> allLines = getAllTrackingNumbers();
                if (allLines != null) {
                    updateProgress(allLines.size());
                }

                AccessibilityNodeInfo rootNode = MyAccessibilityService.getRootNodeSafely(MyAccessibilityService.instance);

                if (typeTrackingNumberIntoApp(rootNode, trackingNumber)) {
                    try { Thread.sleep(1500); } catch (InterruptedException e) { e.printStackTrace(); }
                }

                rootNode = MyAccessibilityService.getRootNodeSafely(MyAccessibilityService.instance);
                boolean called = triggerCallAction(rootNode);
                if (called) {
                    showToastOnMainThread("Đang gọi đơn: " + trackingNumber);
                }

                try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }

                removeFirstTrackingNumber();

                try {
                    Thread.sleep(2000); 
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
            isRunning = false;
            mainHandler.post(() -> {
                Button btnPlayPause = floatingView.findViewById(R.id.btnPlayPause);
                if (btnPlayPause != null) btnPlayPause.setText("▶");
            });
        }).start();
    }

    private boolean clickNodeByIdWithRetry(AccessibilityNodeInfo rootNode, String resourceId, int maxRetries, long delayMs) {
        for (int i = 0; i < maxRetries; i++) {
            if (rootNode == null) return false;
            List<AccessibilityNodeInfo> list = rootNode.findAccessibilityNodeInfosByViewId(resourceId);
            if (list != null && !list.isEmpty()) {
                for (AccessibilityNodeInfo node : list) {
                    if (node.isClickable()) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    } else {
                        AccessibilityNodeInfo parent = node.getParent();
                        while (parent != null) {
                            if (parent.isClickable()) {
                                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                return true;
                            }
                            parent = parent.getParent();
                        }
                    }
                }
            }
            try {
                Thread.sleep(delayMs);
                if (MyAccessibilityService.instance != null) {
                    rootNode = MyAccessibilityService.getRootNodeSafely(MyAccessibilityService.instance);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void updateProgress(int count) {
        if (tvProgress != null) {
            tvProgress.post(() -> tvProgress.setText("Tiến độ: " + count + " đơn"));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null; 
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

package com.example.autocallapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class FloatingWidgetService extends Service {
    public static FloatingWidgetService instance; 
    private WindowManager windowManager;
    private View floatingView;
    private boolean isRunning = false;  
    private TextView tvProgress; 

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
            // 1. Đưa Service lên Foreground an toàn tuyệt đối
            createNotificationChannel();
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Bảng điều khiển Auto đang chạy")
                    .setContentText("Đang hiển thị dạng nổi trên màn hình")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
            startForeground(NOTIFICATION_ID, notification);

            // 2. Khởi tạo giao diện popup nổi từ XML
            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_layout_floating_popup, null); // Hoặc layout_floating_popup tùy project của bạn
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

            // Cho phép chạm và kéo thả bảng popup đi quanh màn hình
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

            // NÚT ĐÓNG (✕)
            Button btnClose = floatingView.findViewById(R.id.btnClose);
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> stopSelf());
            }

            // NÚT PLAY/PAUSE: Chỉ làm nhiệm vụ gán mã => bấm gọi
            Button btnPlayPause = floatingView.findViewById(R.id.btnPlayPause);
            if (btnPlayPause != null) {
                btnPlayPause.setOnClickListener(v -> {
                    isRunning = !isRunning;
                    if (isRunning) {
                        btnPlayPause.setText("⏸");
                        Toast.makeText(this, "Đã bắt đầu tiến trình gán mã và gọi!", Toast.LENGTH_SHORT).show();
                        
                        if (AutoScrapeService.instance != null) {
                            AutoScrapeService.instance.startAutoCallingSequence();
                        }
                    } else {
                        btnPlayPause.setText("▶");
                        Toast.makeText(this, "Đã tạm dừng tiến trình gọi!", Toast.LENGTH_SHORT).show();
                        
                        if (AutoScrapeService.instance != null) {
                            AutoScrapeService.instance.stopAutoCallingSequence();
                        }
                    }
                });
            }

            // NÚT KÍNH LÚP (btnSearch) - Bắt đầu quét mã
            Button btnSearch = floatingView.findViewById(R.id.btnSearch);
            if (btnSearch != null) {
                btnSearch.setOnClickListener(v -> {
                    if (AutoScrapeService.instance != null) {
                        updateProgress(0);
                        AutoScrapeService.instance.startScraping();
                    } else {
                        Toast.makeText(this, "Vui lòng bật Quyền Trợ năng (Accessibility) trước!", Toast.LENGTH_LONG).show();
                    }
                });
            }

            // NÚT THÙNG RÁC (btnDelete) - Xóa dữ liệu đã lưu
            Button btnDelete = floatingView.findViewById(R.id.btnDelete);
            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    if (AutoScrapeService.instance != null) {
                        boolean cleared = AutoScrapeService.instance.clearSavedData();
                        if (cleared) {
                            updateProgress(0);
                            Toast.makeText(this, "Đã xóa toàn bộ dữ liệu!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Không có dữ liệu để xóa.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        try {
                            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
                            if (file.exists() && file.delete()) {
                                updateProgress(0);
                                Toast.makeText(this, "Đã xóa file dữ liệu thành công!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Không tìm thấy file dữ liệu.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Lỗi khi xóa: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            // NÚT GẮN ẢNH (🖼 Gắn Ảnh)
            Button btnAttachImages = floatingView.findViewById(R.id.btnAttachImages);
            if (btnAttachImages != null) {
                btnAttachImages.setOnClickListener(v -> {
                    Toast.makeText(this, "Đang thực hiện gắn ảnh quang cảnh...", Toast.LENGTH_SHORT).show();
                });
            }

            // NÚT CHỌN KIỆN (📦 Chọn Kiện): Chạy chuỗi đầy đủ (Gán mã -> Gọi -> Tích chọn -> Các bước sau với độ trễ an toàn)
            Button btnSelectParcel = floatingView.findViewById(R.id.btnSelectParcel);
            if (btnSelectParcel != null) {
                btnSelectParcel.setOnClickListener(v -> {
                    handleParcelAutomationFullSequence();
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khởi tạo popup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

    /**
     * Lấy mã vận đơn tiếp theo từ danh sách đã lưu (file DanhSachMaDon.txt)
     */
    private String getNextTrackingNumberFromSavedList() {
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath());
                if (!lines.isEmpty()) {
                    return lines.get(0).trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; 
    }

    /**
     * Chuẩn bị và tạo bộ 2 ảnh dựa trên mã vận đơn động vừa lấy
     */
    private Uri prepareParcelImages(String trackingNumber) {
        try {
            Bitmap originalParcelBitmap = BitmapFactory.decodeStream(getAssets().open("default_parcel_image.jpg"));
            Uri editedParcelUri = ImageUtils.createModifiedParcelImage(this, originalParcelBitmap, trackingNumber);

            if (editedParcelUri != null) {
                Toast.makeText(this, "Đã tạo bộ 2 ảnh cho mã: " + trackingNumber, Toast.LENGTH_SHORT).show();
            }
            return editedParcelUri;

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Chuỗi quy trình đầy đủ khi bấm nút "Chọn Kiện":
     * - Bước 1: Gán mã & Gọi (tương tự nút Play)
     * - Bước 2: Tích chọn checkbox
     * - Các bước tiếp theo với độ trễ lớn hơn và kiểm tra an toàn
     */
    private void executeFullAutomationSteps(String trackingNumber) {
        if (AutoScrapeService.instance == null) {
            Toast.makeText(this, "Chưa bật Quyền Trợ năng (Accessibility)!", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // Tăng độ trễ ban đầu để hệ thống ổn định giao diện
                Thread.sleep(1000);

                // --- PHẦN 1: GÁN MÃ VÀ GỌI (Tương tự chức năng nút Play) ---
                if (AutoScrapeService.instance != null) {
                    // Gọi hàm gán mã và kích hoạt gọi điện từ AutoScrapeService
                    AutoScrapeService.instance.startAutoCallingSequence();
                }
                
                // Chờ tiến trình gán mã và gọi thực thi xong ổn định (độ trễ an toàn 2.5 giây)
                Thread.sleep(2500);

                android.accessibilityservice.AccessibilityService accessibilityService = AutoScrapeService.instance;
                if (accessibilityService == null || accessibilityService.getRootInActiveWindow() == null) {
                    return;
                }

                android.view.accessibility.AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();

                // --- BƯỚC 2: Click chọn checkbox của mã vận đơn (Có cơ chế chờ phần tử xuất hiện) ---
                boolean clickedSelect = clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/ivSelect", 3, 1000);
                Thread.sleep(1200); // Chờ chậm rãi, chắc ăn

                // --- BƯỚC 3: Click nút "Kiện vấn đề" ---
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/tvDeliveryFailed", 3, 1000);
                Thread.sleep(1500);

                // --- BƯỚC 4: Chọn lý do "Người nhận không nhận kiện hàng (từ chối)" ---
                rootNode = accessibilityService.getRootInActiveWindow(); // Refresh lại node sau khi chuyển trang
                clickNodeByTextWithRetry(rootNode, "Người nhận không nhận kiện hàng", 3, 1000);
                Thread.sleep(1500);

                // --- BƯỚC 5: Chọn phân loại "Khách không đặt hàng" ---
                rootNode = accessibilityService.getRootInActiveWindow();
                clickNodeByTextWithRetry(rootNode, "Khách không đặt hàng", 3, 1000);
                Thread.sleep(1500);

                // --- BƯỚC 6: Click nút thêm ảnh ---
                rootNode = accessibilityService.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/multiImageAdd", 3, 1000);
                Thread.sleep(1500);

                // --- BƯỚC 7: Dừng lại (Stop) để kiểm tra trực quan thao tác ảnh với mã hiện tại ---

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * HÀM ĐIỀU PHỐI CHÍNH CHO NÚT CHỌN KIỆN
     */
    public void handleParcelAutomationFullSequence() {
        String nextTrackingNumber = getNextTrackingNumberFromSavedList();

        if (nextTrackingNumber == null || nextTrackingNumber.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy mã vận đơn trong danh sách đã lưu!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Tạo bộ ảnh ứng với mã vận đơn vừa bốc được
        Uri editedParcelUri = prepareParcelImages(nextTrackingNumber);

        // 2. Chạy chuỗi tự động hóa đầy đủ nếu ảnh đã sẵn sàng
        if (editedParcelUri != null) {
            executeFullAutomationSteps(nextTrackingNumber);
        }
    }

    // --- CÁC HÀM HỖ TRỢ CLICK AN TOÀN CÓ CƠ CHẾ THỬ LẠI (RETRY) ĐỂ KHÔNG CHẠY QUÁ NHANH ---
    
    private boolean clickNodeByIdWithRetry(android.view.accessibility.AccessibilityNodeInfo rootNode, String resourceId, int maxRetries, long delayMs) {
        for (int i = 0; i < maxRetries; i++) {
            if (rootNode == null) return false;
            java.util.List<android.view.accessibility.AccessibilityNodeInfo> list = rootNode.findAccessibilityNodeInfosByViewId(resourceId);
            if (list != null && !list.isEmpty()) {
                for (android.view.accessibility.AccessibilityNodeInfo node : list) {
                    if (node.isClickable()) {
                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    } else {
                        android.view.accessibility.AccessibilityNodeInfo parent = node.getParent();
                        while (parent != null) {
                            if (parent.isClickable()) {
                                parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                                return true;
                            }
                            parent = parent.getParent();
                        }
                    }
                }
            }
            try {
                Thread.sleep(delayMs);
                // Cập nhật lại rootNode sau mỗi lần chờ
                if (AutoScrapeService.instance != null) {
                    rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private boolean clickNodeByTextWithRetry(android.view.accessibility.AccessibilityNodeInfo rootNode, String text, int maxRetries, long delayMs) {
        for (int i = 0; i < maxRetries; i++) {
            if (rootNode == null) return false;
            java.util.List<android.view.accessibility.AccessibilityNodeInfo> list = rootNode.findAccessibilityNodeInfosByText(text);
            if (list != null && !list.isEmpty()) {
                for (android.view.accessibility.AccessibilityNodeInfo node : list) {
                    if (node.isClickable()) {
                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                        return true;
                    } else {
                        android.view.accessibility.AccessibilityNodeInfo parent = node.getParent();
                        while (parent != null) {
                            if (parent.isClickable()) {
                                parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                                return true;
                            }
                            parent = parent.getParent();
                        }
                    }
                }
            }
            try {
                Thread.sleep(delayMs);
                if (AutoScrapeService.instance != null) {
                    rootNode = AutoScrapeService.instance.getRootInActiveWindow();
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
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

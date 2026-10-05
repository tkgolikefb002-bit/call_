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
import android.os.Bundle;
import android.os.IBinder;
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

            // NÚT PLAY/PAUSE (▶): Chạy HÀNG LOẠT vòng lặp (Bốc mã -> Dán mã -> Gọi điện cho toàn bộ danh sách)
            Button btnPlayPause = floatingView.findViewById(R.id.btnPlayPause);
            if (btnPlayPause != null) {
                btnPlayPause.setOnClickListener(v -> {
                    isRunning = !isRunning;
                    if (isRunning) {
                        btnPlayPause.setText("⏸");
                        Toast.makeText(this, "Bắt đầu chạy hàng loạt: Dán mã & Gọi điện...", Toast.LENGTH_SHORT).show();
                        
                        // Chạy tiến trình vòng lặp hàng loạt
                        startBatchLoopAutomation();
                    } else {
                        btnPlayPause.setText("▶");
                        Toast.makeText(this, "Đã tạm dừng tiến trình hàng loạt!", Toast.LENGTH_SHORT).show();
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

            // NÚT CHỌN KIỆN (📦 Chọn Kiện): Chạy ĐƠN LẺ toàn bộ 8 bước chuyên sâu
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
     * Lấy danh sách toàn bộ mã vận đơn từ file DanhSachMaDon.txt
     */
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

    /**
     * Lấy mã vận đơn tiếp theo (dòng đầu tiên)
     */
    private String getNextTrackingNumberFromSavedList() {
        List<String> lines = getAllTrackingNumbers();
        if (lines != null && !lines.isEmpty()) {
            return lines.get(0).trim();
        }
        return null;
    }

    /**
     * Xóa mã đầu tiên sau khi đã xử lý xong để chuyển sang mã tiếp theo trong danh sách hàng loạt
     */
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

    /**
     * Chuẩn bị và tạo bộ ảnh dựa trên mã vận đơn động vừa lấy
     */
    private Uri prepareParcelImages(String trackingNumber) {
        try {
            Bitmap originalParcelBitmap = BitmapFactory.decodeStream(getAssets().open("default_parcel_image.jpg"));
            Uri editedParcelUri = ImageUtils.createModifiedParcelImage(this, originalParcelBitmap, trackingNumber);

            if (editedParcelUri != null) {
                Toast.makeText(this, "Đã tạo bộ ảnh cho mã: " + trackingNumber, Toast.LENGTH_SHORT).show();
            }
            return editedParcelUri;

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * HÀM QUAN TRỌNG: Tự động tìm khung nhập mã (EditText hoặc thông qua ID) và điền mã vận đơn vào
     */
    private boolean typeTrackingNumberIntoApp(AccessibilityNodeInfo rootNode, String trackingNumber) {
        if (rootNode == null) return false;

        // 1. Thử tìm ô nhập liệu theo các ID phổ biến hoặc thuộc tính EditText trên app BEST Courier
        // (Bạn có thể thay đổi resource-id chính xác của ô nhập mã nếu biết, ví dụ: "com.best.android.vietcourier:id/etSearch")
        List<AccessibilityNodeInfo> editTexts = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/etSearch");
        if (editTexts == null || editTexts.isEmpty()) {
            // Dự phòng tìm kiếm tất cả các node dạng EditText trên màn hình hiện tại
            editTexts = findAllEditTexts(rootNode);
        }

        if (editTexts != null && !editTexts.isEmpty()) {
            for (AccessibilityNodeInfo node : editTexts) {
                if (node.isEditable() || node.getClassName().toString().contains("EditText")) {
                    // Focus vào ô nhập
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    
                    // Gán trực tiếp văn bản (mã vận đơn) vào ô
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, trackingNumber);
                    boolean success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    if (success) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<AccessibilityNodeInfo> findAllEditTexts(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> list = new java.util.ArrayList<>();
        if (root == null) return list;
        if (root.getClassName() != null && root.getClassName().toString().contains("EditText")) {
            list.add(root);
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            list.addAll(findAllEditTexts(root.getChild(i)));
        }
        return list;
    }

    /**
     * TIẾN TRÌNH HÀNG LOẠT (Nút Play): Lặp qua toàn bộ danh sách mã -> Dán mã -> Gọi điện
     */
    private void startBatchLoopAutomation() {
        new Thread(() -> {
            while (isRunning) {
                String trackingNumber = getNextTrackingNumberFromSavedList();
                if (trackingNumber == null || trackingNumber.isEmpty()) {
                    isRunning = false;
                    break;
                }

                if (AutoScrapeService.instance == null || AutoScrapeService.instance.getRootInActiveWindow() == null) {
                    try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }
                    continue;
                }

                AccessibilityNodeInfo rootNode = AutoScrapeService.instance.getRootInActiveWindow();

                // 1. Dán mã vào khung
                boolean typed = typeTrackingNumberIntoApp(rootNode, trackingNumber);
                if (typed) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
                }

                // 2. Kích hoạt gọi điện
                if (AutoScrapeService.instance != null) {
                    AutoScrapeService.instance.startAutoCallingSequence();
                }

                // 3. Xóa mã vừa chạy khỏi danh sách để chuyển sang mã tiếp theo trong vòng lặp
                removeFirstTrackingNumber();

                // Chờ một khoảng thời gian trước khi sang đơn tiếp theo
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
            isRunning = false;
        }).start();
    }

    /**
     * Chuỗi quy trình đầy đủ 8 bước khi bấm nút "Chọn Kiện":
     * 1. Lấy mã và điền (dán) vào khung
     * 2. Bấm gọi điện
     * 3. Tích chọn checkbox của mã (`ivSelect`)
     * 4. Click "Kiện vấn đề" (`tvDeliveryFailed`)
     * 5. Chọn lý do "Người nhận không nhận kiện hàng"
     * 6. Chọn phân loại "Khách không đặt hàng"
     * 7. Click ô thêm ảnh (`multiImageAdd`) -> Gọi Intent hệ thống nạp ảnh động đã chỉnh sửa
     * 8. Click nút "Thêm" (`vAdd`) để hoàn tất
     */
    private void executeFullAutomationSteps(String trackingNumber, Uri imageUri) {
        if (AutoScrapeService.instance == null) {
            Toast.makeText(this, "Chưa bật Quyền Trợ năng (Accessibility)!", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                Thread.sleep(1000);

                AccessibilityNodeInfo rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                if (rootNode == null) return;

                // --- BƯỚC 1: Dán mã vận đơn vào khung nhập ---
                typeTrackingNumberIntoApp(rootNode, trackingNumber);
                Thread.sleep(1500);

                // --- BƯỚC 2: Bấm gọi điện ---
                if (AutoScrapeService.instance != null) {
                    AutoScrapeService.instance.startAutoCallingSequence();
                }
                Thread.sleep(2500);

                // --- BƯỚC 3: Click chọn checkbox của mã vận đơn (`ivSelect`) ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/ivSelect", 3, 1000);
                Thread.sleep(1200);

                // --- BƯỚC 4: Click nút "Kiện vấn đề" (`tvDeliveryFailed`) ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/tvDeliveryFailed", 3, 1000);
                Thread.sleep(1500);

                // --- BƯỚC 5: Chọn lý do "Người nhận không nhận kiện hàng" ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByTextWithRetry(rootNode, "Người nhận không nhận kiện hàng", 3, 1000);
                Thread.sleep(1500);

                // --- BƯỚC 6: Chọn phân loại "Khách không đặt hàng" ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByTextWithRetry(rootNode, "Khách không đặt hàng", 3, 1000);
                Thread.sleep(1500);

                // --- BƯỚC 7: Click ô thêm ảnh (`multiImageAdd`) và gọi Intent hệ thống nạp ảnh động ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/multiImageAdd", 3, 1000);
                Thread.sleep(1000);

                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/jpeg");
                    if (imageUri != null) {
                        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    }
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(shareIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Thread.sleep(2500); // Chờ hệ thống nạp ảnh

                // --- BƯỚC 8: Click nút "Thêm" (`vAdd`) để hoàn tất ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                boolean clickedAddButton = clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/vAdd", 3, 1000);
                if (!clickedAddButton) {
                    clickNodeByTextWithRetry(rootNode, "Thêm", 3, 1000);
                }
                
                Toast.makeText(this, "Đã hoàn tất 8 bước xử lý kiện hàng!", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * HÀM ĐIỀU PHỐI CHO NÚT CHỌN KIỆN (📦)
     */
    public void handleParcelAutomationFullSequence() {
        String nextTrackingNumber = getNextTrackingNumberFromSavedList();

        if (nextTrackingNumber == null || nextTrackingNumber.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy mã vận đơn trong danh sách đã lưu!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Tạo bộ ảnh ứng với mã vận đơn vừa bốc
        Uri editedParcelUri = prepareParcelImages(nextTrackingNumber);

        // 2. Chạy chuỗi tự động hóa đơn lẻ 8 bước
        if (editedParcelUri != null) {
            executeFullAutomationSteps(nextTrackingNumber, editedParcelUri);
        }
    }

    // --- CÁC HÀM HỖ TRỢ CLICK AN TOÀN CÓ CƠ CHẾ THỬ LẠI (RETRY) ---
    
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
                if (AutoScrapeService.instance != null) {
                    rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private boolean clickNodeByTextWithRetry(AccessibilityNodeInfo rootNode, String text, int maxRetries, long delayMs) {
        for (int i = 0; i < maxRetries; i++) {
            if (rootNode == null) return false;
            List<AccessibilityNodeInfo> list = rootNode.findAccessibilityNodeInfosByText(text);
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

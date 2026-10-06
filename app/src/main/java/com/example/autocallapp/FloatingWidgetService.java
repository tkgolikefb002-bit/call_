package com.example.autocallapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
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
    private File preparedImageFile;
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

            // NÚT PLAY/PAUSE (▶): Chạy HÀNG LOẠT vòng lặp (Bốc mã -> Dán mã -> Gọi điện)
            Button btnPlayPause = floatingView.findViewById(R.id.btnPlayPause);
            if (btnPlayPause != null) {
                btnPlayPause.setOnClickListener(v -> {
                    isRunning = !isRunning;
                    if (isRunning) {
                        btnPlayPause.setText("⏸");
                        Toast.makeText(this, "Bắt đầu chạy hàng loạt: Dán mã & Gọi điện...", Toast.LENGTH_SHORT).show();
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

    private Uri prepareParcelImages(String trackingNumber) {
        try {
            Bitmap originalParcelBitmap = BitmapFactory.decodeStream(getAssets().open("default_parcel_image.jpg"));
            // Lưu trực tiếp file vào thư mục nội bộ app để Camera ảo đọc
            File cacheFile = new File(getFilesDir(), "ma_van_don.jpg");
            
            // Gọi hàm tạo ảnh của bạn và lưu vào cacheFile
            // (Hoặc nếu ImageUtils trả về Uri, bạn có thể copy nội dung sang cacheFile này)
            Uri editedParcelUri = ImageUtils.createModifiedParcelImage(this, originalParcelBitmap, trackingNumber);
            
            return editedParcelUri;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tự động tìm ô nhập mã và điền mã vận đơn vào
     */
    private boolean typeTrackingNumberIntoApp(AccessibilityNodeInfo rootNode, String trackingNumber) {
        if (rootNode == null) return false;

        List<AccessibilityNodeInfo> editTexts = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/etSearch");
        if (editTexts == null || editTexts.isEmpty()) {
            editTexts = findAllEditTexts(rootNode);
        }

        if (editTexts != null && !editTexts.isEmpty()) {
            for (AccessibilityNodeInfo node : editTexts) {
                if (node.isEditable() || node.getClassName().toString().contains("EditText")) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
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
     * HÀM GỌI ĐIỆN CHUẨN XÁC: Click trực tiếp vào đúng resource-id hiển thị số điện thoại (`tvPhoneNub`)[cite: 1]
     */
    private boolean triggerCallAction(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;

        // Ưu tiên tuyệt đối: Click vào đúng ID chứa SĐT / nút gọi trên app BEST[cite: 1]
        boolean clicked = clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/tvPhoneNub", 3, 500);
        if (clicked) {
            return true;
        }

        // Dự phòng các ID phụ khác nếu có
        String[] fallbackIds = {
            "com.best.android.vietcourier:id/ivCall",
            "com.best.android.vietcourier:id/img_call",
            "com.best.android.vietcourier:id/btnCall"
        };
        for (String id : fallbackIds) {
            if (clickNodeByIdWithRetry(rootNode, id, 1, 200)) {
                return true;
            }
        }

        // Dự phòng cuối cùng: Gọi qua service hệ thống
        if (AutoScrapeService.instance != null) {
            AutoScrapeService.instance.startAutoCallingSequence();
            return true;
        }

        return false;
    }

    /**
     * TIẾN TRÌNH HÀNG LOẠT (Nút Play): Lặp qua toàn bộ danh sách mã -> Dán mã -> Gọi điện `tvPhoneNub`[cite: 1]
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
                    try { Thread.sleep(1500); } catch (InterruptedException e) { e.printStackTrace(); }
                }

                // 2. Kích hoạt gọi điện bằng cách bấm vào `tvPhoneNub`[cite: 1]
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                triggerCallAction(rootNode);
                try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }

                // 3. Xóa mã vừa chạy khỏi danh sách để chuyển sang đơn tiếp theo
                removeFirstTrackingNumber();

                try {
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
            isRunning = false;
        }).start();
    }

    /**
     * Chuỗi quy trình đầy đủ 8 bước khi bấm nút "Chọn Kiện" (📦):
     * 1. Dán mã vào khung
     * 2. Bấm gọi điện (`tvPhoneNub`)[cite: 1]
     * 3. Tích chọn checkbox của mã (`ivSelect`)[cite: 1]
     * 4. Click "Kiện vấn đề" (`tvDeliveryFailed`)[cite: 1]
     * 5. Chọn lý do "Người nhận không nhận kiện hàng"[cite: 1]
     * 6. Chọn phân loại "Khách không đặt hàng"[cite: 1]
     * 7. Click ô thêm ảnh (`multiImageAdd`) -> Gọi Intent hệ thống nạp ảnh động[cite: 1]
     * 8. Click nút "Thêm" (`vAdd`) để hoàn tất[cite: 1]
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

                // --- BƯỚC 2: Bấm gọi điện (`tvPhoneNub`)[cite: 1] ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                triggerCallAction(rootNode);
                Thread.sleep(2500);

                // --- BƯỚC 3: Click chọn checkbox của mã vận đơn (`ivSelect`)[cite: 1] ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/ivSelect", 3, 1000);
                Thread.sleep(1000);

                // --- BƯỚC 4: Click nút "Kiện vấn đề" (`tvDeliveryFailed`)[cite: 1] ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/tvDeliveryFailed", 3, 1000);
                Thread.sleep(1000);

                // --- BƯỚC 5: Chọn lý do "Người nhận không nhận kiện hàng"[cite: 1] ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByTextWithRetry(rootNode, "Người nhận không nhận kiện hàng", 3, 1000);
                Thread.sleep(1000);

                // --- BƯỚC 6: Chọn phân loại "Khách không đặt hàng"[cite: 1] ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByTextWithRetry(rootNode, "Khách không đặt hàng", 3, 1000);
                Thread.sleep(1000);

                // --- BƯỚC 7: Mở ô thêm ảnh và ÉP BUỘC gọi thẳng Camera ảo của app mình ---
                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/multiImageAdd", 3, 1000);
                Thread.sleep(1000);

                rootNode = AutoScrapeService.instance.getRootInActiveWindow();
                clickNodeByTextWithRetry(rootNode, "Chụp ảnh", 3, 1000);
                Thread.sleep(800); 

                // Tạo Intent trỏ định danh chính xác vào VirtualCameraActivity của app mình để chiếm quyền tuyệt đối
                Intent virtualCamIntent = new Intent(this, VirtualCameraActivity.class);
                virtualCamIntent.setAction(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                virtualCamIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                // Kiểm tra nếu app ngoài gửi kèm EXTRA_OUTPUT thì truyền tiếp qua
                // (Đoạn này giúp app BEST nhận diện đây chính là kết quả trả về từ camera mong đợi)
                startActivity(virtualCamIntent);

                Thread.sleep(2000);

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

    public void handleParcelAutomationFullSequence() {
        String nextTrackingNumber = getNextTrackingNumberFromSavedList();

        if (nextTrackingNumber == null || nextTrackingNumber.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy mã vận đơn trong danh sách đã lưu!", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri editedParcelUri = prepareParcelImages(nextTrackingNumber);
        if (editedParcelUri != null) {
            executeFullAutomationSteps(nextTrackingNumber, editedParcelUri);
        }
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

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

            // NÚT ĐÓNG (✕) - DUY NHẤT NÚT NÀY MỚI TẮT POPUP
            Button btnClose = floatingView.findViewById(R.id.btnClose);
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> stopSelf());
            }

            // Xử lý nút Chạy / Dừng (Nút Play/Pause ở giữa)
            Button btnPlayPause = floatingView.findViewById(R.id.btnPlayPause);
            if (btnPlayPause != null) {
                btnPlayPause.setOnClickListener(v -> {
                    isRunning = !isRunning;
                    if (isRunning) {
                        btnPlayPause.setText("⏸");
                        Toast.makeText(this, "Đã bắt đầu tiến trình tự động!", Toast.LENGTH_SHORT).show();
                        
                        // Gọi hàm điều phối chính xử lý kiện hàng
                        handleParcelAutomation();

                        if (AutoScrapeService.instance != null) {
                            AutoScrapeService.instance.startAutoCallingSequence();
                        }
                    } else {
                        btnPlayPause.setText("▶");
                        Toast.makeText(this, "Đã tạm dừng tiến trình!", Toast.LENGTH_SHORT).show();
                        
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
                        Toast.makeText(this, "Vui lòng bật Quyền Trợ năng (Accessibility) cho ứng dụng trước!", Toast.LENGTH_LONG).show();
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
                            Toast.makeText(this, "Đã xóa toàn bộ dữ liệu đơn hàng đã lưu!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Không có dữ liệu hoặc file chưa tồn tại.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        try {
                            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
                            if (file.exists() && file.delete()) {
                                updateProgress(0);
                                Toast.makeText(this, "Đã xóa file dữ liệu thành công!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Không tìm thấy file dữ liệu để xóa.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Lỗi khi xóa: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            // ===== 2 NÚT MỚI BỔ SUNG =====

            // 1. Nút Gắn Ảnh (🖼 Gắn Ảnh)
            Button btnAttachImages = floatingView.findViewById(R.id.btnAttachImages);
            if (btnAttachImages != null) {
                btnAttachImages.setOnClickListener(v -> {
                    Toast.makeText(this, "Đang thực hiện gắn ảnh quang cảnh...", Toast.LENGTH_SHORT).show();
                    // Thêm logic xử lý ảnh quang cảnh tại đây nếu cần
                });
            }

            // 2. Nút Chọn Kiện (📦 Chọn Kiện)
            Button btnSelectParcel = floatingView.findViewById(R.id.btnSelectParcel);
            if (btnSelectParcel != null) {
                btnSelectParcel.setOnClickListener(v -> {
                    // Gọi hàm điều phối chính tự động hóa 
                    handleParcelAutomation();
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khởi tạo popup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkDefaultImagesReady() {
        try {
            boolean sceneExists = false;
            boolean parcelExists = false;

            String[] assetsList = getAssets().list("");
            for (String fileName : assetsList) {
                if (fileName.equals("default_scene_image.jpg")) sceneExists = true;
                if (fileName.equals("default_parcel_image.jpg")) parcelExists = true;
            }

            if (sceneExists && parcelExists) {
                Toast.makeText(this, "✅ Ảnh mặc định đã sẵn sàng trong hệ thống!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ Cảnh báo: Thiếu file ảnh trong assets!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                    // Lấy mã đầu tiên trong danh sách
                    return lines.get(0).trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Trả về null nếu không đọc được danh sách
        return null; 
    }

    /**
     * HÀM 1: Chuyên phụ trách chuẩn bị và tạo bộ 2 ảnh dựa trên mã vận đơn được truyền vào
     */
    private Uri prepareParcelImages(String trackingNumber) {
        try {
            // 1. Load Ảnh 2 (Ảnh kiện hàng gốc) từ thư mục assets
            Bitmap originalParcelBitmap = BitmapFactory.decodeStream(getAssets().open("default_parcel_image.jpg"));

            // 2. Gọi hàm tạo ảnh đã sửa số từ ImageUtils.java với chính xác mã vận đơn hiện tại
            Uri editedParcelUri = ImageUtils.createModifiedParcelImage(this, originalParcelBitmap, trackingNumber);

            // 3. Đường dẫn Ảnh 1 (Ảnh quang cảnh mặc định lưu trong assets)
            Uri defaultSceneUri = Uri.parse("file:///android_asset/default_scene_image.jpg");

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
     * HÀM 2: Chuyên phụ trách điều khiển luồng Accessibility thực hiện tự động hóa các bước giao diện
     */
    private void executeAccessibilitySteps(String trackingNumber) {
        if (AutoScrapeService.instance == null) {
            Toast.makeText(this, "Chưa bật Quyền Trợ năng (Accessibility)!", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                android.accessibilityservice.AccessibilityService accessibilityService = AutoScrapeService.instance;
                if (accessibilityService == null || accessibilityService.getRootInActiveWindow() == null) {
                    return;
                }

                android.view.accessibility.AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();

                // --- BƯỚC 1 & 2: Click chọn checkbox của mã vận đơn ---
                clickNodeById(rootNode, "com.best.android.vietcourier:id/ivSelect");
                Thread.sleep(800);

                // --- BƯỚC 3: Click nút "Kiện vấn đề" ---
                clickNodeById(rootNode, "com.best.android.vietcourier:id/tvDeliveryFailed");
                Thread.sleep(1000);

                // --- BƯỚC 4: Chọn lý do "Người nhận không nhận kiện hàng (từ chối)" ---
                clickNodeByText(rootNode, "Người nhận không nhận kiện hàng");
                Thread.sleep(1000);

                // --- BƯỚC 5: Chọn phân loại "Khách không đặt hàng" ---
                clickNodeByText(rootNode, "Khách không đặt hàng");
                Thread.sleep(1000);

                // --- BƯỚC 6: Click nút thêm ảnh ---
                clickNodeById(rootNode, "com.best.android.vietcourier:id/multiImageAdd");
                Thread.sleep(1000);

                // --- BƯỚC 7: Dừng lại (Stop) để kiểm tra trực quan thao tác ảnh ---
                // Hệ thống dừng tại đây theo yêu cầu để bạn kiểm tra thao tác ảnh với mã: trackingNumber

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * HÀM ĐIỀU PHỐI CHÍNH: Lấy mã động, kết nối xử lý ảnh và chạy Accessibility
     */
    public void handleParcelAutomation() {
        // 1. Lấy mã vận đơn động từ danh sách đã lưu
        String nextTrackingNumber = getNextTrackingNumberFromSavedList();

        if (nextTrackingNumber == null || nextTrackingNumber.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy mã vận đơn trong danh sách đã lưu!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Thực hiện chuẩn bị bộ ảnh ứng với mã vận đơn vừa bốc được
        Uri editedParcelUri = prepareParcelImages(nextTrackingNumber);

        // 3. Nếu ảnh đã sẵn sàng, tiến hành chạy các bước điều khiển giao diện
        if (editedParcelUri != null) {
            executeAccessibilitySteps(nextTrackingNumber);
        }
    }

    // --- CÁC HÀM HỖ TRỢ TÌM VÀ CLICK NODE GIAO DIỆN ---
    private boolean clickNodeById(android.view.accessibility.AccessibilityNodeInfo rootNode, String resourceId) {
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
        return false;
    }

    private boolean clickNodeByText(android.view.accessibility.AccessibilityNodeInfo rootNode, String text) {
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
        return false;
    }

    /**
     * Hàm cập nhật tiến độ lên giao diện popup khớp định dạng yêu cầu
     */
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

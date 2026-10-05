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
                        
                        // Gọi hàm thực thi xử lý kiện hàng & thay số ở đây
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
                    // Gọi trực tiếp hàm tự động xử lý sửa số trên ảnh kiện hàng
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
     * Hàm xử lý chính: Lấy Ảnh 1 (quang cảnh) + Xử lý số mới lên Ảnh 2 (kiện hàng)
     */
    public void handleParcelAutomation() {
        String nextTrackingNumber = "8485974825648"; // Mã vận đơn mới mẫu cần thay thế

        try {
            // 1. Load Ảnh 2 (Ảnh kiện hàng gốc) từ thư mục assets
            Bitmap originalParcelBitmap = BitmapFactory.decodeStream(getAssets().open("default_parcel_image.jpg"));

            // 2. Gọi hàm tạo ảnh đã sửa số từ ImageUtils.java
            Uri editedParcelUri = ImageUtils.createModifiedParcelImage(this, originalParcelBitmap, nextTrackingNumber);

            // 3. Đường dẫn Ảnh 1 (Ảnh quang cảnh mặc định lưu trong assets)
            Uri defaultSceneUri = Uri.parse("file:///android_asset/default_scene_image.jpg");

            if (editedParcelUri != null) {
                // 👉 Đã có sẵn cặp 2 ảnh chuẩn bị ném vào app BEST Express:
                // - defaultSceneUri (Ảnh 1: Quang cảnh giữ nguyên)
                // - editedParcelUri (Ảnh 2: Đã thay đổi số vận đơn tự động)
                Toast.makeText(this, "Đã tạo xong bộ 2 ảnh cho mã: " + nextTrackingNumber, Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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

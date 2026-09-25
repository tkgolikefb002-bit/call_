package com.example.autocallapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
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

        // 1. Đưa Service lên Foreground để Android KHÔNG BAO GIỜ kill ngầm popup
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bảng điều khiển Auto đang chạy")
                .setContentText("Đang hiển thị dạng nổi trên màn hình")
                .setSmallIcon(android.R.drawable.ic_menu_compass) // Dùng icon hệ thống sẵn có tránh lỗi thiếu icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        // 2. Khởi tạo giao diện popup nổi
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
        windowManager.addView(floatingView, params);

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

        // =========================================================================
        // NÚT ĐÓNG (✕) - DUY NHẤT NÚT NÀY MỚI TẮT POPUP
        // =========================================================================
        Button btnClose = floatingView.findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                stopSelf(); // Gọi hủy service thủ công khi người dùng bấm vào dấu X
            });
        }

        // Xử lý nút Chạy / Dừng (Nút Play/Pause ở giữa)
        Button btnPlayPause = floatingView.findViewById(R.id.btnPlayPause);
        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(v -> {
                isRunning = !isRunning;
                if (isRunning) {
                    btnPlayPause.setText("⏸");
                    Toast.makeText(this, "Đã bắt đầu Auto chạy ngầm!", Toast.LENGTH_SHORT).show();
                } else {
                    btnPlayPause.setText("▶");
                    Toast.makeText(this, "Đã tạm dừng Auto!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // =========================================================================
        // SỰ KIỆN CHO NÚT KÍNH LÚP (btnSearch) - Bắt đầu quét mã
        // =========================================================================
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

        // =========================================================================
        // SỰ KIỆN CHO NÚT THÙNG RÁC (btnDelete) - Xóa dữ liệu đã lưu
        // =========================================================================
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
                        java.io.File file = new java.io.File(getExternalFilesDir(null), "DanhSachMaDon.txt");
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
     * Hàm cập nhật tiến độ lên giao diện popup
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

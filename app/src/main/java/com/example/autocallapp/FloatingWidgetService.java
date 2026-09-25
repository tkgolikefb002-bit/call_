package com.example.autocallapp;

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

public class FloatingWidgetService extends Service {
    public static FloatingWidgetService instance; // Instance để service khác gọi cập nhật giao diện
    private WindowManager windowManager;
    private View floatingView;
    private boolean isRunning = false; 
    private TextView tvProgress; // TextView hiển thị tiến độ quét

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; // Gán instance khi service được tạo

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_popup, null);

        // Ánh xạ TextView hiển thị tiến độ từ layout XML
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

        // Nút Đóng popup (dấu X)
        floatingView.findViewById(R.id.btnClose).setOnClickListener(v -> stopSelf());

        // Xử lý nút Chạy / Dừng (Nút Play/Pause ở giữa)
        Button btnPlayPause = floatingView.findViewById(R.id.btnPlayPause);
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

        // =========================================================================
        // SỰ KIỆN CHO NÚT KÍNH LÚP (btnSearch) - Bắt đầu quét mã
        // =========================================================================
        Button btnSearch = floatingView.findViewById(R.id.btnSearch);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                if (AutoScrapeService.instance != null) {
                    // Reset lại hiển thị tiến độ về 0 khi bắt đầu quét mới
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
                        updateProgress(0); // Reset tiến độ về 0 khi xóa dữ liệu
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

    /**
     * Hàm công khai để AutoScrapeService gọi cập nhật số lượng mã quét được lên giao diện
     */
    public void updateProgress(int count) {
        if (tvProgress != null) {
            tvProgress.post(() -> tvProgress.setText("Đã quét: " + count));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null; // Xóa instance khi service bị hủy
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
    }
}

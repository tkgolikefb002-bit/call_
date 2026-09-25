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
import android.widget.Toast;

public class FloatingWidgetService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private boolean isRunning = false; // Trạng thái chạy ngầm

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_popup, null);

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
        // THÊM SỰ KIỆN CHO NÚT KÍNH LÚP (btnSearch) Ở ĐÂY
        // =========================================================================
        Button btnSearch = floatingView.findViewById(R.id.btnSearch);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                // Kiểm tra xem dịch vụ trợ năng (Accessibility Service) đã được bật chưa
                if (AutoScrapeService.instance != null) {
                    // Kích hoạt tiến trình tự động quét mã đơn hàng và cuộn màn hình
                    AutoScrapeService.instance.startScraping();
                } else {
                    Toast.makeText(this, "Vui lòng bật Quyền Trợ năng (Accessibility) cho ứng dụng trước!", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
    }
}

package com.example.autocallapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
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
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
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

            Button btnSearch = floatingView.findViewById(R.id.btnSearch);
            if (btnSearch != null) {
                btnSearch.setOnClickListener(v -> {
                    if (AutoCallAccessibilityService.instance != null) {
                        updateProgress(0);
                    } else {
                        showToastOnMainThread("Vui lòng bật Quyền Trợ năng (Accessibility) trước!");
                    }
                });
            }

            Button btnDelete = floatingView.findViewById(R.id.btnDelete);
            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> clearSavedDataFile());
            }

            Button btnAttachImages = floatingView.findViewById(R.id.btnAttachImages);
            if (btnAttachImages != null) {
                btnAttachImages.setOnClickListener(v -> handleParcelAutomationFullSequence());
            }

            Button btnSelectParcel = floatingView.findViewById(R.id.btnSelectParcel);
            if (btnSelectParcel != null) {
                btnSelectParcel.setOnClickListener(v -> handleParcelAutomationFullSequence());
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

    private Uri copyAssetImageToCache(String assetFileName) {
        try {
            File imageDir = new File(getCacheDir(), "ImageDir");
            if (!imageDir.exists()) {
                imageDir.mkdirs();
            }

            File cacheFile = new File(imageDir, assetFileName);
            
            try (InputStream in = getAssets().open(assetFileName);
                 OutputStream out = new FileOutputStream(cacheFile)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            return FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    cacheFile
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
                    showToastOnMainThread("Đã chạy xong toàn bộ danh sách đơn!");
                    break;
                }

                if (AutoCallAccessibilityService.instance == null || AutoCallAccessibilityService.instance.getRootInActiveWindow() == null) {
                    try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }
                    continue;
                }

                AccessibilityNodeInfo rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                if (typeTrackingNumberIntoApp(rootNode, trackingNumber)) {
                    try { Thread.sleep(1500); } catch (InterruptedException e) { e.printStackTrace(); }
                }

                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                triggerCallAction(rootNode);
                try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }

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

    private void executeFullAutomationSteps(String trackingNumber, Uri imageUri) {
        if (AutoCallAccessibilityService.instance == null) {
            showToastOnMainThread("Chưa bật Quyền Trợ năng (Accessibility) trước!");
            return;
        }

        new Thread(() -> {
            try {
                Thread.sleep(1000);
                AccessibilityNodeInfo rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                if (rootNode == null) return;

                typeTrackingNumberIntoApp(rootNode, trackingNumber);
                Thread.sleep(1500);

                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                triggerCallAction(rootNode);
                Thread.sleep(2500);

                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/ivSelect", 3, 1000);
                Thread.sleep(1000);

                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/tvDeliveryFailed", 3, 1000);
                Thread.sleep(1000);

                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                clickNodeByTextWithRetry(rootNode, "Người nhận không nhận kiện hàng", 3, 1000);
                Thread.sleep(1000);

                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                clickNodeByTextWithRetry(rootNode, "Khách không đặt hàng", 3, 1000);
                Thread.sleep(1000);

                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/multiImageAdd", 3, 1000);
                Thread.sleep(1000);

                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                boolean clickedLib = clickNodeByTextWithRetry(rootNode, "Thư viện", 3, 1000);
                if (!clickedLib) {
                    clickedLib = clickNodeByTextWithRetry(rootNode, "Album", 3, 1000);
                }
                if (!clickedLib) {
                    clickNodeByTextWithRetry(rootNode, "Chọn từ thiết bị", 3, 1000);
                }
                
                Thread.sleep(2000);
                
                rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
                if (!clickNodeByIdWithRetry(rootNode, "com.best.android.vietcourier:id/vAdd", 3, 1000)) {
                    clickNodeByTextWithRetry(rootNode, "Thêm", 3, 1000);
                }
                
                showToastOnMainThread("Đã hoàn tất 8 bước xử lý kiện hàng!");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void handleParcelAutomationFullSequence() {
        String nextTrackingNumber = getNextTrackingNumberFromSavedList();

        if (nextTrackingNumber == null || nextTrackingNumber.isEmpty()) {
            showToastOnMainThread("Không tìm thấy mã vận đơn trong danh sách đã lưu!");
            return;
        }

        Uri assetImageUri = copyAssetImageToCache("default_parcel_image.jpg");
        if (assetImageUri != null) {
            executeFullAutomationSteps(nextTrackingNumber, assetImageUri);
        } else {
            showToastOnMainThread("Không thể lấy ảnh từ assets!");
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
                if (AutoCallAccessibilityService.instance != null) {
                    rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
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
                if (AutoCallAccessibilityService.instance != null) {
                    rootNode = AutoCallAccessibilityService.instance.getRootInActiveWindow();
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

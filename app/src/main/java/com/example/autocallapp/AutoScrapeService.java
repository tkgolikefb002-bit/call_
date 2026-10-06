package com.example.autocallapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AutoScrapeService extends AccessibilityService {
    private static final String TAG = "AutoScrapeService";
    public static AutoScrapeService instance;
    private boolean isScraping = false;
    private boolean isCallingProcessActive = false;
    
    private final Set<String> collectedWaybills = new LinkedHashSet<>();
    private final List<String> waybillQueueList = new ArrayList<>();
    private int currentCallIndex = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility Service đã kết nối thành công!");
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        isScraping = false;
        isCallingProcessActive = false;
    }

    // =========================================================================
    // PHẦN 1: QUÉT MÃ VẬN ĐƠN TRONG TAB HIỆN TẠI (VUỐT DỌC CHUẨN XÁC)
    // =========================================================================
    public void startScraping() {
        if (isScraping) {
            Toast.makeText(this, "Đang trong quá trình quét mã vận đơn...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        clearSavedData();
        isScraping = true;
        updatePopupProgress(0);
        
        Toast.makeText(this, "Bắt đầu quét mã vận đơn...", Toast.LENGTH_SHORT).show();

        Runnable scanRunnable = new Runnable() {
            int noNewDataCount = 0;
            
            @Override
            public void run() {
                if (!isScraping) return;

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                int previousSize = collectedWaybills.size();
                
                if (rootNode != null) {
                    List<AccessibilityNodeInfo> billNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCode");
                    if (billNodes != null && !billNodes.isEmpty()) {
                        for (AccessibilityNodeInfo billNode : billNodes) {
                            if (billNode != null && billNode.getText() != null) {
                                if (billNode.isVisibleToUser()) {
                                    String code = billNode.getText().toString().trim();
                                    if (!code.isEmpty()) {
                                        collectedWaybills.add(code);
                                    }
                                }
                            }
                            if (billNode != null) billNode.recycle();
                        }
                    }
                    rootNode.recycle();
                }

                updatePopupProgress(collectedWaybills.size());

                if (collectedWaybills.size() > previousSize) {
                    noNewDataCount = 0;
                } else {
                    noNewDataCount++;
                }

                if (noNewDataCount >= 4) {
                    isScraping = false;
                    saveWaybillsToFile();
                    updatePopupProgress(collectedWaybills.size());
                    Toast.makeText(getApplicationContext(), "Đã quét xong! Tổng số mã đơn: " + collectedWaybills.size(), Toast.LENGTH_LONG).show();
                    return;
                }

                performVerticalSwipe(handler, this);
            }
        };

        handler.post(scanRunnable);
    }

    private void performVerticalSwipe(Handler handler, Runnable nextRunnable) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(500, 1400);
            path.lineTo(500, 500);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 200));
            
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    handler.postDelayed(nextRunnable, 300);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    handler.postDelayed(nextRunnable, 300);
                }
            }, null);
        } else {
            handler.postDelayed(nextRunnable, 400);
        }
    }

    // =========================================================================
    // PHẦN 2: TIẾN TRÌNH XỬ LÝ (TÌM Ô TÌM KIẾM, DÁN VÀ BẤM XỬ LÝ)
    // =========================================================================
    public void startAutoCallingSequence() {
        if (isCallingProcessActive) return;

        loadWaybillsForProcessing();

        if (waybillQueueList.isEmpty()) {
            Toast.makeText(this, "Không có mã vận đơn nào trong danh sách! Hãy quét trước.", Toast.LENGTH_LONG).show();
            return;
        }

        isCallingProcessActive = true;
        currentCallIndex = 0;
        Toast.makeText(this, "Bắt đầu tiến trình tự động (" + waybillQueueList.size() + " mã)...", Toast.LENGTH_SHORT).show();
        executeNextCallStep();
    }

    private void executeNextCallStep() {
        if (!isCallingProcessActive) return;

        if (currentCallIndex >= waybillQueueList.size()) {
            Toast.makeText(this, "Đã hoàn thành toàn bộ danh sách mã vận đơn!", Toast.LENGTH_LONG).show();
            isCallingProcessActive = false;
            return;
        }

        String targetCode = waybillQueueList.get(currentCallIndex);
        currentCallIndex++;
        
        if (FloatingWidgetService.instance != null) {
            handler.post(() -> FloatingWidgetService.instance.updateProgress(currentCallIndex));
        }

        inputCodeToSearchBox(targetCode);
    }

    private void inputCodeToSearchBox(String codeText) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            List<AccessibilityNodeInfo> searchBoxes = new ArrayList<>();
            findEditTextByHintRecursive(rootNode, searchBoxes);

            if (searchBoxes.isEmpty()) {
                List<AccessibilityNodeInfo> byId = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/searchEditText");
                if (byId != null) searchBoxes.addAll(byId);
            }

            boolean filled = false;
            if (!searchBoxes.isEmpty()) {
                for (AccessibilityNodeInfo box : searchBoxes) {
                    if (box != null) {
                        box.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        box.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        
                        Rect rect = new Rect();
                        box.getBoundsInScreen(rect);
                        if (rect.width() > 0 && rect.height() > 0) {
                            clickAtCoordinates(rect.centerX(), rect.centerY());
                        }
                        
                        filled = true;
                        break;
                    }
                }
            }

            rootNode.recycle();

            if (filled) {
                handler.postDelayed(() -> performClipboardPasteAndSearch(codeText), 400);
            } else {
                clickAtCoordinates(500, 150);
                handler.postDelayed(() -> performClipboardPasteAndSearch(codeText), 400);
            }
        } else {
            handler.postDelayed(this::executeNextCallStep, 500);
        }
    }

    private void performClipboardPasteAndSearch(String codeText) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("WaybillCode", codeText);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            AccessibilityNodeInfo focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focusedNode != null) {
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, codeText);
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                
                focusedNode.recycle();
            }
            rootNode.recycle();
        }

        clickAtCoordinates(500, 150);
        Log.d(TAG, "Đã dán mã: " + codeText + ", chờ 1s để app lọc kết quả...");

        handler.postDelayed(() -> verifyAndClickItemButton(codeText), 1000);
    }

    private void clickAtCoordinates(float x, float y) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            dispatchGesture(builder.build(), null, null);
        }
    }

    private void findEditTextByHintRecursive(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;
        
        CharSequence hint = node.getHintText();
        CharSequence className = node.getClassName();
        
        if (className != null && className.toString().contains("EditText")) {
            if (hint != null && (hint.toString().contains("Nhập mã") || hint.toString().contains("vận đơn") || hint.toString().contains("Người nhận"))) {
                results.add(node);
                return;
            } else if (results.isEmpty()) {
                results.add(node);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            findEditTextByHintRecursive(child, results);
            if (child != null) child.recycle();
        }
    }

    private void verifyAndClickItemButton(String codeText) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            boolean clicked = false;
            
            List<AccessibilityNodeInfo> billNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCode");
            if (billNodes != null && !billNodes.isEmpty()) {
                for (AccessibilityNodeInfo node : billNodes) {
                    if (node != null && node.getText() != null && node.getText().toString().contains(codeText)) {
                        if (node.isVisibleToUser()) {
                            Rect rect = new Rect();
                            node.getBoundsInScreen(rect);
                            if (rect.width() > 0 && rect.height() > 0) {
                                clickAtCoordinates(rect.centerX(), rect.centerY());
                                clicked = true;
                            }
                        }
                        node.recycle();
                        if (clicked) break;
                    } else {
                        if (node != null) node.recycle();
                    }
                }
            }

            rootNode.recycle();

            if (clicked) {
                Toast.makeText(this, "Đang xử lý mã: " + codeText, Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Không tìm thấy nút mã vận đơn cho mã: " + codeText + ", chuyển sang mã tiếp theo.");
                handler.postDelayed(this::executeNextCallStep, 400);
            }
        } else {
            handler.postDelayed(this::executeNextCallStep, 500);
        }
    }

    public void onCallFinished() {
        if (!isCallingProcessActive) return;
        handler.postDelayed(this::executeNextCallStep, 1000);
    }

    public void stopAutoCallingSequence() {
        isCallingProcessActive = false;
        handler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "Đã dừng tiến trình tự động!", Toast.LENGTH_SHORT).show();
    }

    private void saveWaybillsToFile() {
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            FileOutputStream fos = new FileOutputStream(file, false);
            for (String code : collectedWaybills) {
                fos.write((code + "\n").getBytes(StandardCharsets.UTF_8));
            }
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean clearSavedData() {
        collectedWaybills.clear();
        waybillQueueList.clear();
        updatePopupProgress(0);
        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists()) {
                return file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // =========================================================================
    // HÀM XỬ LÝ CHỌN ẢNH THÔNG MINH: TỰ ĐỘNG TÌM THƯ MỤC "ImageDir" TRONG BỘ CHỌN TỆP
    // =========================================================================
    public void clickAddPhotoButton() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            boolean clicked = false;
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/ivAddPhoto");
            if (nodes == null || nodes.isEmpty()) {
                nodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/btnTakePhoto");
            }
            
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node != null && node.isVisibleToUser()) {
                        Rect rect = new Rect();
                        node.getBoundsInScreen(rect);
                        if (rect.width() > 0 && rect.height() > 0) {
                            clickAtCoordinates(rect.centerX(), rect.centerY());
                            clicked = true;
                            node.recycle();
                            break;
                        }
                    }
                    if (node != null) node.recycle();
                }
            }
            rootNode.recycle();

            if (clicked) {
                Toast.makeText(this, "Đang mở giao diện chọn ảnh...", Toast.LENGTH_SHORT).show();
                
                // Chờ 1 giây để hệ thống mở bảng chọn file/thư mục lên, sau đó quét tìm thư mục ImageDir
                handler.postDelayed(this::findAndClickImageDirFolder, 1000);
            } else {
                Log.w(TAG, "Không tìm thấy nút thêm ảnh trên màn hình hiện tại.");
            }
        }
    }

    // Tự động quét và bấm vào thư mục "ImageDir" trên màn hình quản lý file
    private void findAndClickImageDirFolder() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            boolean clicked = false;
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByText("ImageDir");
            
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node != null && node.isVisibleToUser()) {
                        clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        if (!clicked) {
                            Rect rect = new Rect();
                            node.getBoundsInScreen(rect);
                            if (rect.width() > 0 && rect.height() > 0) {
                                clickAtCoordinates(rect.centerX(), rect.centerY());
                                clicked = true;
                            }
                        }
                        node.recycle();
                        break;
                    }
                    if (node != null) node.recycle();
                }
            }
            rootNode.recycle();

            if (clicked) {
                Toast.makeText(this, "Đã mở thư mục ImageDir thành công!", Toast.LENGTH_SHORT).show();
                // Chờ 1 giây để danh sách ảnh bên trong load lên, sau đó tiến hành chọn ảnh đầu tiên
                handler.postDelayed(this::selectFirstImageInCurrentFolder, 1000);
            } else {
                Log.w(TAG, "Chưa tìm thấy thư mục ImageDir, thử tìm cách chọn trực tiếp ảnh gần nhất...");
                // Phương án dự phòng: Nếu không thấy thư mục ImageDir, quét chọn ô ảnh đầu tiên có sẵn
                handler.postDelayed(this::selectFirstImageInCurrentFolder, 1000);
            }
        }
    }

    // Quét và chọn tấm ảnh đầu tiên xuất hiện trong thư mục hiện tại
    private void selectFirstImageInCurrentFolder() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            boolean selected = false;
            List<AccessibilityNodeInfo> imageNodes = new ArrayList<>();
            findImageViewsRecursive(rootNode, imageNodes);

            for (AccessibilityNodeInfo imgNode : imageNodes) {
                if (imgNode != null && imgNode.isVisibleToUser()) {
                    Rect rect = new Rect();
                    imgNode.getBoundsInScreen(rect);
                    // Lọc ô ảnh có kích thước hợp lệ trên màn hình
                    if (rect.width() > 100 && rect.height() > 100) {
                        selected = imgNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        if (!selected) {
                            clickAtCoordinates(rect.centerX(), rect.centerY());
                            selected = true;
                        }
                        imgNode.recycle();
                        break;
                    }
                }
                if (imgNode != null) imgNode.recycle();
            }
            rootNode.recycle();

            if (selected) {
                Toast.makeText(this, "Đã chọn ảnh, chuẩn bị xác nhận đơn...", Toast.LENGTH_SHORT).show();
                // Đợi 1.5 giây sau khi chọn ảnh xong thì bấm nút xác nhận/submit hoàn tất đơn
                handler.postDelayed(this::clickSubmitButton, 1500);
            } else {
                Log.w(TAG, "Không tìm thấy ảnh để chọn trong thư mục.");
            }
        }
    }

    // Hàm đệ quy phụ trợ giúp quét toàn bộ các ImageView/View chứa ảnh trên màn hình chọn tệp
    private void findImageViewsRecursive(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;
        CharSequence className = node.getClassName();
        if (className != null && (className.toString().contains("ImageView") || className.toString().contains("Image") || className.toString().contains("Grid"))) {
            results.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            findImageViewsRecursive(child, results);
            if (child != null) child.recycle();
        }
    }

    public void triggerVirtualCamera() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.example.virtualcamera");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Toast.makeText(this, "Đã mở máy ảnh ảo thành công!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Không tìm thấy ứng dụng máy ảnh ảo!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Lỗi mở máy ảnh ảo: " + e.getMessage());
        }
    }

    public void clickSubmitButton() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            boolean clicked = false;
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/btnSubmit");
            if (nodes == null || nodes.isEmpty()) {
                nodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/btnConfirm");
            }
            if (nodes == null || nodes.isEmpty()) {
                nodes = rootNode.findAccessibilityNodeInfosByText("Xác nhận");
            }
            if (nodes == null || nodes.isEmpty()) {
                nodes = rootNode.findAccessibilityNodeInfosByText("Hoàn tất");
            }

            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node != null && node.isVisibleToUser()) {
                        Rect rect = new Rect();
                        node.getBoundsInScreen(rect);
                        if (rect.width() > 0 && rect.height() > 0) {
                            clickAtCoordinates(rect.centerX(), rect.centerY());
                            clicked = true;
                            node.recycle();
                            break;
                        }
                    }
                    if (node != null) node.recycle();
                }
            }
            rootNode.recycle();

            if (clicked) {
                Toast.makeText(this, "Đã bấm xác nhận đơn, chuyển sang mã tiếp theo...", Toast.LENGTH_SHORT).show();
                handler.postDelayed(this::onCallFinished, 2000);
            } else {
                Log.w(TAG, "Không tìm thấy nút xác nhận trên màn hình.");
                handler.postDelayed(this::onCallFinished, 1500);
            }
        } else {
            handler.postDelayed(this::onCallFinished, 1500);
        }
    }

    private void loadWaybillsForProcessing() {
        waybillQueueList.clear();
        if (!collectedWaybills.isEmpty()) {
            waybillQueueList.addAll(collectedWaybills);
        }

        try {
            File file = new File(getExternalFilesDir(null), "DanhSachMaDon.txt");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !waybillQueueList.contains(trimmed)) {
                        waybillQueueList.add(trimmed);
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePopupProgress(int count) {
        if (FloatingWidgetService.instance != null) {
            handler.post(() -> FloatingWidgetService.instance.updateProgress(count));
        }
    }
}

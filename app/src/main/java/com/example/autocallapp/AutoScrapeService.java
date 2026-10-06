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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
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
        handler.removeCallbacksAndMessages(null);
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
                    try {
                        List<AccessibilityNodeInfo> billNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCode");
                        if (billNodes != null && !billNodes.isEmpty()) {
                            for (AccessibilityNodeInfo billNode : billNodes) {
                                if (billNode != null) {
                                    try {
                                        if (billNode.isVisibleToUser() && billNode.getText() != null) {
                                            String code = billNode.getText().toString().trim();
                                            if (!code.isEmpty()) {
                                                collectedWaybills.add(code);
                                            }
                                        }
                                    } finally {
                                        billNode.recycle();
                                    }
                                }
                            }
                        }
                    } finally {
                        rootNode.recycle();
                    }
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
    // PHẦN 2: XỬ LÝ NÚT PLAY (TỰ ĐỘNG GÁN MÃ, TÌM KIẾM VÀ GỌI ĐƠN)
    // =========================================================================
    public void startAutoCallingSequence() {
        if (isCallingProcessActive) {
            Toast.makeText(this, "Tiến trình tự động đang chạy...", Toast.LENGTH_SHORT).show();
            return;
        }

        loadWaybillsForProcessing();

        if (waybillQueueList.isEmpty()) {
            Toast.makeText(this, "Không có mã vận đơn nào! Hãy quét mã trước.", Toast.LENGTH_LONG).show();
            return;
        }

        isCallingProcessActive = true;
        currentCallIndex = 0;
        Toast.makeText(this, "Bắt đầu chạy tự động (" + waybillQueueList.size() + " mã)...", Toast.LENGTH_SHORT).show();
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

        Log.d(TAG, "Đang xử lý mã thứ [" + currentCallIndex + "/" + waybillQueueList.size() + "]: " + targetCode);
        
        inputCodeToSearchBoxWithRetry(targetCode, 5);
    }

    private void inputCodeToSearchBoxWithRetry(String codeText, int retryCount) {
        if (!isCallingProcessActive) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            try {
                List<AccessibilityNodeInfo> searchBoxes = new ArrayList<>();
                
                findEditTextByHintRecursive(rootNode, searchBoxes);

                if (searchBoxes.isEmpty()) {
                    List<AccessibilityNodeInfo> byId = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/searchEditText");
                    if (byId != null) searchBoxes.addAll(byId);
                }
                
                if (searchBoxes.isEmpty()) {
                    List<AccessibilityNodeInfo> allEdits = new ArrayList<>();
                    findAllEditTextsRecursive(rootNode, allEdits);
                    if (!allEdits.isEmpty()) searchBoxes.addAll(allEdits);
                }

                if (!searchBoxes.isEmpty()) {
                    AccessibilityNodeInfo box = searchBoxes.get(0);
                    if (box != null && box.isVisibleToUser()) {
                        try {
                            Rect rect = new Rect();
                            box.getBoundsInScreen(rect);
                            if (rect.width() > 0 && rect.height() > 0) {
                                clickAtCoordinates(rect.centerX(), rect.centerY());
                            } else {
                                box.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                                box.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            }

                            handler.postDelayed(() -> performClipboardPasteAndSearch(codeText), 800);
                            return;
                        } finally {
                            box.recycle();
                        }
                    }
                }
            } finally {
                rootNode.recycle();
            }
        }

        if (retryCount > 0) {
            handler.postDelayed(() -> inputCodeToSearchBoxWithRetry(codeText, retryCount - 1), 500);
        } else {
            Log.w(TAG, "Dùng tọa độ fallback để mở khung tìm kiếm cho mã: " + codeText);
            clickAtCoordinates(500, 250);
            handler.postDelayed(() -> performClipboardPasteAndSearch(codeText), 1000);
        }
    }

    private void performClipboardPasteAndSearch(String codeText) {
        if (!isCallingProcessActive) return;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("WaybillCode", codeText);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            try {
                boolean textSet = false;
                
                List<AccessibilityNodeInfo> editBoxes = new ArrayList<>();
                findAllEditTextsRecursive(rootNode, editBoxes);

                if (!editBoxes.isEmpty()) {
                    for (AccessibilityNodeInfo box : editBoxes) {
                        if (box != null && box.isVisibleToUser()) {
                            try {
                                box.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                                box.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                
                                box.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                                
                                Bundle arguments = new Bundle();
                                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, codeText);
                                box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                                
                                textSet = true;
                                Log.d(TAG, "Đã điền thành công mã vào ô EditText: " + codeText);
                                break;
                            } finally {
                                box.recycle();
                            }
                        }
                    }
                }

                if (!textSet) {
                    AccessibilityNodeInfo focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    if (focusedNode != null) {
                        try {
                            focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                            Bundle arguments = new Bundle();
                            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, codeText);
                            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                        } finally {
                            focusedNode.recycle();
                        }
                    }
                }
            } finally {
                rootNode.recycle();
            }
        }

        handler.postDelayed(() -> verifyAndClickItemButton(codeText), 1500);
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
            if (hint != null && (hint.toString().toLowerCase().contains("nhập") || hint.toString().toLowerCase().contains("vận đơn") || hint.toString().toLowerCase().contains("tìm kiếm"))) {
                results.add(node);
                return;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findEditTextByHintRecursive(child, results);
                child.recycle();
            }
        }
    }

    private void findAllEditTextsRecursive(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;
        
        CharSequence className = node.getClassName();
        if (className != null && className.toString().contains("EditText")) {
            if (node.isVisibleToUser()) {
                results.add(node);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findAllEditTextsRecursive(child, results);
                child.recycle();
            }
        }
    }

    private void verifyAndClickItemButton(String codeText) {
        if (!isCallingProcessActive) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            try {
                boolean clicked = false;
                
                List<AccessibilityNodeInfo> billNodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/tvBillCode");
                if (billNodes != null && !billNodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : billNodes) {
                        if (node != null) {
                            try {
                                if (node.getText() != null && node.getText().toString().contains(codeText)) {
                                    if (node.isVisibleToUser()) {
                                        Rect rect = new Rect();
                                        node.getBoundsInScreen(rect);
                                        if (rect.width() > 0 && rect.height() > 0) {
                                            clickAtCoordinates(rect.centerX(), rect.centerY());
                                            clicked = true;
                                        }
                                    }
                                }
                            } finally {
                                node.recycle();
                            }
                            if (clicked) break;
                        }
                    }
                }

                if (!clicked) {
                    List<AccessibilityNodeInfo> textNodes = rootNode.findAccessibilityNodeInfosByText(codeText);
                    if (textNodes != null && !textNodes.isEmpty()) {
                        for (AccessibilityNodeInfo node : textNodes) {
                            if (node != null) {
                                try {
                                    if (node.isVisibleToUser()) {
                                        Rect rect = new Rect();
                                        node.getBoundsInScreen(rect);
                                        if (rect.width() > 0 && rect.height() > 0) {
                                            clickAtCoordinates(rect.centerX(), rect.centerY());
                                            clicked = true;
                                            break;
                                        }
                                    }
                                } finally {
                                    node.recycle();
                                }
                            }
                        }
                    }
                }

                if (clicked) {
                    Toast.makeText(this, "Đã chọn mã: " + codeText, Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Không tìm thấy nút hoặc dòng hiển thị mã: " + codeText + ", bỏ qua và chuyển mã tiếp theo.");
                    handler.postDelayed(this::executeNextCallStep, 500);
                }
            } finally {
                rootNode.recycle();
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
    // PHẦN 3: XỬ LÝ CHÈN ẢNH VÀ XÁC NHẬN (NÚT CHỌN KIỆN)
    // =========================================================================
    
    public void clickAddPhotoButton() {
        copyAssetToFile("default_parcel_image.jpg");
        copyAssetToFile("default_scene_image.jpg");

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            try {
                boolean clicked = false;
                List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/ivAddPhoto");
                if (nodes == null || nodes.isEmpty()) {
                    nodes = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/btnTakePhoto");
                }
                if (nodes == null || nodes.isEmpty()) {
                    nodes = rootNode.findAccessibilityNodeInfosByText("Thêm ảnh");
                }
                
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node != null) {
                            try {
                                if (node.isVisibleToUser()) {
                                    Rect rect = new Rect();
                                    node.getBoundsInScreen(rect);
                                    if (rect.width() > 0 && rect.height() > 0) {
                                        clickAtCoordinates(rect.centerX(), rect.centerY());
                                        clicked = true;
                                        break;
                                    }
                                }
                            } finally {
                                node.recycle();
                            }
                        }
                    }
                }

                if (clicked) {
                    Toast.makeText(this, "Đã mở khung ảnh, chuẩn bị điền mã vận đơn...", Toast.LENGTH_SHORT).show();
                    
                    String currentCode = "";
                    if (currentCallIndex > 0 && currentCallIndex <= waybillQueueList.size()) {
                        currentCode = waybillQueueList.get(currentCallIndex - 1);
                    }
                    final String finalCode = currentCode;

                    handler.postDelayed(() -> performEditWaybillOnPhoto(finalCode), 1500);
                } else {
                    Log.w(TAG, "Không tìm thấy nút thêm ảnh trên màn hình.");
                }
            } finally {
                rootNode.recycle();
            }
        }
    }

    private File copyAssetToFile(String assetFileName) {
        File outFile = new File(getExternalFilesDir(null), assetFileName);
        try {
            if (!outFile.exists()) {
                InputStream in = getAssets().open(assetFileName);
                FileOutputStream out = new FileOutputStream(outFile);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.flush();
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return outFile;
    }

    private void performEditWaybillOnPhoto(String codeText) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null && !codeText.isEmpty()) {
            try {
                List<AccessibilityNodeInfo> editBoxes = new ArrayList<>();
                findAllEditTextsRecursive(rootNode, editBoxes);

                if (editBoxes.isEmpty()) {
                    List<AccessibilityNodeInfo> byId = rootNode.findAccessibilityNodeInfosByViewId("com.best.android.vietcourier:id/etEditWaybill");
                    if (byId != null) editBoxes.addAll(byId);
                }

                boolean edited = false;
                if (!editBoxes.isEmpty()) {
                    for (AccessibilityNodeInfo box : editBoxes) {
                        if (box != null) {
                            try {
                                if (box.isVisibleToUser()) {
                                    box.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                                    box.performAction(AccessibilityNodeInfo.ACTION_CLICK);

                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    ClipData clip = ClipData.newPlainText("EditWaybill", codeText);
                                    if (clipboard != null) {
                                        clipboard.setPrimaryClip(clip);
                                    }
                                    box.performAction(AccessibilityNodeInfo.ACTION_PASTE);

                                    Bundle arguments = new Bundle();
                                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, codeText);
                                    box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);

                                    edited = true;
                                    break;
                                }
                            } finally {
                                box.recycle();
                            }
                        }
                    }
                }

                if (edited) {
                    Toast.makeText(this, "Đã điền mã " + codeText + " lên ảnh!", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Không tìm thấy ô sửa mã trên ảnh, tiếp tục tiến trình...");
                }
            } finally {
                rootNode.recycle();
            }
        }

        handler.postDelayed(this::clickSubmitButton, 2000);
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
            try {
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
                        if (node != null) {
                            try {
                                if (node.isVisibleToUser()) {
                                    Rect rect = new Rect();
                                    node.getBoundsInScreen(rect);
                                    if (rect.width() > 0 && rect.height() > 0) {
                                        clickAtCoordinates(rect.centerX(), rect.centerY());
                                        clicked = true;
                                        break;
                                    }
                                }
                            } finally {
                                node.recycle();
                            }
                        }
                    }
                }

                if (clicked) {
                    Toast.makeText(this, "Đã bấm xác nhận đơn, chuyển sang mã tiếp theo...", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this::onCallFinished, 2000);
                } else {
                    Log.w(TAG, "Không tìm thấy nút xác nhận trên màn hình.");
                    handler.postDelayed(this::onCallFinished, 1500);
                }
            } finally {
                rootNode.recycle();
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

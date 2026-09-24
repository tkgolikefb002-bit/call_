package com.example.autocallapp;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;
import java.util.Random;

public class MyInCallService extends InCallService {
    public static Call activeCall = null;
    private boolean isHandled = false;

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        isHandled = false;

        // 1. Tự động bật màn hình giao diện ảo ngay khi có tiến trình gọi
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // 2. Lắng nghe trạng thái thay đổi của cuộc gọi
        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                // Ngay khi cuộc gọi chuyển sang trạng thái kết nối hoặc đang gọi
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    // Ngắt cuộc gọi TỨC THÌ để bên kia không bị đổ chuông
                    call.disconnect();

                    // Sinh thời gian ngẫu nhiên từ 20 đến 30 giây
                    int randomDuration = new Random().nextInt(11) + 20;

                    // TĂNG thời gian delay lên 2.5 giây (2500ms) để chờ hệ thống Android ghi xong log 0s đầu tiên
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        updateLatestCallLogDuration(randomDuration);
                    }, 2500); 
                }
            }
        });
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        if (activeCall == call) {
            activeCall = null;
        }
    }

    // Hàm cập nhật đè thời gian vào lịch sử nhật ký
    private void updateLatestCallLogDuration(int targetDurationSeconds) {
        try {
            // Lấy ra bản ghi cuộc gọi mới nhất vừa được hệ thống sinh ra
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE},
                null,
                null,
                CallLog.Calls.DATE + " DESC LIMIT 1"
            );

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int idColumnIndex = cursor.getColumnIndex(CallLog.Calls._ID);
                    if (idColumnIndex != -1) {
                        long callId = cursor.getLong(idColumnIndex);

                        // Tiến hành cập nhật thời lượng mới
                        ContentValues values = new ContentValues();
                        values.put(CallLog.Calls.DURATION, targetDurationSeconds);

                        Uri updateUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, String.valueOf(callId));
                        int rowsUpdated = getContentResolver().update(updateUri, values, null, null);
                        
                        Log.d("CallLogUpdate", "Đã cập nhật thành công thời lượng: " + targetDurationSeconds + "s (Rows: " + rowsUpdated + ")");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("CallLogUpdate", "Lỗi cập nhật thời lượng nhật ký: " + e.getMessage());
        }
    }
}

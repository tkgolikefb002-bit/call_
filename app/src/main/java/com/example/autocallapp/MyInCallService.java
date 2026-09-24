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

        // Tự động bật giao diện ảo cuộc gọi
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    // Ngắt cuộc gọi vật lý ngay lập tức
                    call.disconnect();

                    // Sinh ngẫu nhiên thời lượng từ 20 đến 30 giây
                    int randomDuration = new Random().nextInt(11) + 20;

                    // Chờ 2.5 giây để hệ thống ghi xong log 0s rồi tiến hành ghi đè
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

    // Hàm cập nhật nhật ký: sửa thời lượng thành 20-30s và ép trạng thái thành cuộc gọi đi thành công
    private void updateLatestCallLogDuration(int targetDurationSeconds) {
        try {
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

                        ContentValues values = new ContentValues();
                        values.put(CallLog.Calls.DURATION, targetDurationSeconds);
                        values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE); // Tránh bị hiện lỗi "Gọi nhỡ"

                        Uri updateUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, String.valueOf(callId));
                        getContentResolver().update(updateUri, values, null, null);
                        
                        Log.d("CallLogUpdate", "Đã cập nhật thành công thời lượng: " + targetDurationSeconds + "s");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("CallLogUpdate", "Lỗi cập nhật thời lượng nhật ký: " + e.getMessage());
        }
    }
}

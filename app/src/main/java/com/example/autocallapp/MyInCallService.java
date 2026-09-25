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
    private static final String TAG = "MyInCallService";

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        isHandled = false;

        // Bật màn hình giao diện ảo lên
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                // Khi cuộc gọi sang trạng thái chuẩn bị gọi hoặc đang kết nối
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    // Sinh thời gian ngẫu nhiên từ 20 đến 35 giây cho CallLog
                    int randomDuration = new Random().nextInt(16) + 20;

                    // ĐỢI ĐÚNG 700ms (0.7 giây) RỒI MỚI NGẮT KẾT NỐI
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            if (call != null) {
                                call.disconnect();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi khi gọi call.disconnect(): " + e.getMessage());
                        }

                        // Cập nhật lại thời lượng vào lịch sử cuộc gọi ở luồng nền để chống nghẽn (ANR)
                        new Thread(() -> {
                            updateLatestCallLogDuration(randomDuration);
                        }).start();

                    }, 700); 
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

    // Hàm cập nhật nhật ký thành công (giả lập thời lượng gọi thành công)
    private void updateLatestCallLogDuration(int targetDurationSeconds) {
        try {
            // Chờ hệ thống ghi nhận log thô xuống database thiết bị
            Thread.sleep(500); 

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
                        values.put(CallLog.Calls.DURATION, targetDurationSeconds); // Gán thời lượng ngẫu nhiên từ 20s - 35s
                        values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE); // Ép hiển thị là cuộc gọi đi

                        Uri updateUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, String.valueOf(callId));
                        getContentResolver().update(updateUri, values, null, null);
                        
                        Log.d(TAG, "Đã cập nhật thành công thời lượng CallLog: " + targetDurationSeconds + "s");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi cập nhật nhật ký cuộc gọi: " + e.getMessage());
        }
    }
}

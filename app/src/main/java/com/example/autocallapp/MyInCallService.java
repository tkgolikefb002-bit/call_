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

        // 1. Bật ngay màn hình giao diện ảo lên để che đậy
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    // Sinh thời gian ngẫu nhiên từ 20 đến 35 giây cho CallLog
                    int randomDuration = new Random().nextInt(16) + 20;

                    // 2. CHỜ ĐÚNG 700ms RỒI NGẮT LUÔN CUỘC GỌI
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            if (call != null) {
                                call.disconnect(); // Tắt cuộc gọi trong 700ms
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi khi ngắt cuộc gọi: " + e.getMessage());
                        }

                        // 3. Cập nhật lịch sử cuộc gọi (CallLog) thành thời lượng ngẫu nhiên 20s - 35s
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

    private void updateLatestCallLogDuration(int targetDurationSeconds) {
        try {
            Thread.sleep(600); // Chờ hệ thống ghi nhận log thô xuống database

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
                        values.put(CallLog.Calls.DURATION, targetDurationSeconds); // Gán thời lượng ngẫu nhiên
                        values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE); 

                        Uri updateUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, String.valueOf(callId));
                        getContentResolver().update(updateUri, values, null, null);
                        
                        Log.d(TAG, "Đã chỉnh sửa CallLog thành công: " + targetDurationSeconds + "s");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi cập nhật nhật ký: " + e.getMessage());
        }
    }
}

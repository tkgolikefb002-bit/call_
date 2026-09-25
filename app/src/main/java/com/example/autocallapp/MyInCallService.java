package com.example.autocallapp;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.Random;

public class MyInCallService extends InCallService {
    public static Call activeCall = null;
    private boolean isHandled = false;
    private static final String TAG = "MyInCallService";

    // Số điện thoại ảo / số mặc định hệ thống bạn muốn chuyển hướng tới
    private static final String TARGET_VIRTUAL_NUMBER = "0123456789";

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        isHandled = false;

        // Lấy số điện thoại của cuộc gọi vừa được tạo
        Uri handleUri = call.getDetails().getHandle();
        String phoneNumber = (handleUri != null) ? handleUri.getSchemeSpecificPart() : "";

        // QUAN TRỌNG: Nếu đây là cuộc gọi tới số ảo (0123456789) do chính app gọi, 
        // thì BỎ QUA NGAY LẬP TỨC, không can thiệp để nó chạy bình thường đến khi kết thúc.
        if (phoneNumber != null && phoneNumber.contains(TARGET_VIRTUAL_NUMBER)) {
            Log.d(TAG, "Đây là cuộc gọi tới số ảo, để chạy tự nhiên không can thiệp.");
            return;
        }

        // 1. Nếu là số thực của người dùng gọi đi -> Bật giao diện ảo ngay lập tức
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    // Thời gian ngẫu nhiên từ 20 đến 35 giây cho CallLog
                    int randomDuration = new Random().nextInt(16) + 20;

                    // 2. CHỜ 700ms RỒI NGẮT CUỘC GỌI THỰC (Bảo mật số điện thoại không bị lộ)
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            if (call != null) {
                                call.disconnect(); 
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi khi ngắt cuộc gọi thực: " + e.getMessage());
                        }

                        // 3. Sau khi ngắt thành công, tiến hành gọi sang số ảo (0123456789)
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            makeCallToVirtualNumber(TARGET_VIRTUAL_NUMBER);
                        }, 300);

                        // 4. Cập nhật lịch sử cuộc gọi (CallLog) thành 20s - 35s ở luồng nền
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

    private void makeCallToVirtualNumber(String phoneNumber) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null) {
                Uri uri = Uri.parse("tel:" + phoneNumber);
                Bundle extras = new Bundle();
                try {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        telecomManager.placeCall(uri, extras);
                        Log.d(TAG, "Đã gọi sang số ảo: " + phoneNumber);
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Lỗi gọi số ảo: " + e.getMessage());
                }
            }
        }
    }

    private void updateLatestCallLogDuration(int targetDurationSeconds) {
        try {
            Thread.sleep(600); 

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
                        values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE); 

                        Uri updateUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, String.valueOf(callId));
                        getContentResolver().update(updateUri, values, null, null);
                        
                        Log.d(TAG, "Đã cập nhật CallLog thành công: " + targetDurationSeconds + "s");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi cập nhật nhật ký: " + e.getMessage());
        }
    }
}

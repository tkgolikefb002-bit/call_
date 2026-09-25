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

    // Số điện thoại ảo / số mặc định hệ thống bạn muốn chuyển hướng tới sau khi ngắt 700ms
    private static final String TARGET_VIRTUAL_NUMBER = "0123456789";

    // Biến cờ đánh dấu xem có phải chính app đang tự gọi đi số ảo hay không để tránh lặp vô hạn
    private static boolean isAppSelfCalling = false;

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        isHandled = false;

        // Lấy thông tin số điện thoại của cuộc gọi hiện tại
        Uri handleUri = call.getDetails().getHandle();
        String phoneNumber = (handleUri != null) ? handleUri.getSchemeSpecificPart() : "";

        // NẾU ĐÂY LÀ CUỘC GỌI DO CHÍNH APP TỰ GỌI SANG SỐ ẢO/MẶC ĐỊNH => BỎ QUA KHÔNG CAN THIỆP NỮA
        if (isAppSelfCalling) {
            Log.d(TAG, "Đây là cuộc gọi tự động từ app sang số ảo, để chạy tự nhiên.");
            return;
        }

        // 1. Bật ngay màn hình giao diện ảo lên để che đậy
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                // Khi cuộc gọi chuẩn bị đi hoặc đang kết nối
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    // Sinh thời gian ngẫu nhiên từ 20 đến 35 giây cho CallLog
                    int randomDuration = new Random().nextInt(16) + 20;

                    // 2. ĐỢI ĐÚNG 700ms RỒI NGẮT CUỘC GỌI GỐC (Tránh bên kia kịp nhận diện số thật)
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            if (call != null) {
                                call.disconnect(); // Cúp máy ngay lập tức trong 700ms
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi khi gọi call.disconnect(): " + e.getMessage());
                        }

                        // 3. Sau khi ngắt thành công, tiến hành gọi sang số ảo/mặc định (nếu muốn gọi tiếp)
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            isAppSelfCalling = true; // Bật cờ đánh dấu app đang tự gọi
                            makeCallToVirtualNumber(TARGET_VIRTUAL_NUMBER);
                            
                            // Sau 2 giây reset lại cờ để chuẩn bị cho lần gọi tiếp theo của người dùng
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                isAppSelfCalling = false;
                            }, 2000);
                        }, 300);

                        // 4. Cập nhật lịch sử cuộc gọi (CallLog) thành thời lượng ngẫu nhiên 20s - 35s ở luồng nền
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

    // Hàm thực hiện gọi sang số ảo/mặc định
    private void makeCallToVirtualNumber(String phoneNumber) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null) {
                Uri uri = Uri.parse("tel:" + phoneNumber);
                Bundle extras = new Bundle();
                try {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        telecomManager.placeCall(uri, extras);
                        Log.d(TAG, "Đã chuyển hướng sang số ảo: " + phoneNumber);
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Lỗi bảo mật khi gọi số ảo: " + e.getMessage());
                }
            }
        }
    }

    // Hàm cập nhật nhật ký cuộc gọi (giả lập thời lượng gọi từ 20s - 35s)
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
                        values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE); // Ép hiển thị là cuộc gọi đi

                        Uri updateUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, String.valueOf(callId));
                        getContentResolver().update(updateUri, values, null, null);
                        
                        Log.d(TAG, "Đã chỉnh sửa CallLog thành công: " + targetDurationSeconds + "s");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi cập nhật nhật ký cuộc gọi: " + e.getMessage());
        }
    }
}

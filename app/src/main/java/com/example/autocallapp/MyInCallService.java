package com.example.autocallapp;

import android.content.ContentValues;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;
import java.util.Random;

public class MyInCallService extends InCallService {
    public static Call activeCall = null;
    private boolean isHandled = false; // Tránh gọi xử lý nhiều lần cho cùng một cuộc gọi

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        isHandled = false;

        // 1. Tự động bật màn hình hiển thị số điện thoại và giao diện ảo ngay lập tức
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // 2. Lắng nghe trạng thái cuộc gọi để ngắt ngay từ khi bắt đầu kết nối/quay số
        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                // Ngay khi cuộc gọi chuyển sang trạng thái đang quay số (DIALING) hoặc kết nối
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    String phoneNumber = "Unknown";
                    if (call.getDetails() != null && call.getDetails().getHandle() != null) {
                        phoneNumber = call.getDetails().getHandle().getSchemeSpecificPart();
                    }

                    // Thời gian ngẫu nhiên từ 20 đến 30 giây cho lịch sử giả lập
                    int randomDuration = new Random().nextInt(11) + 20;
                    
                    // Ghi lại lịch sử cuộc gọi giả lập vào hệ thống
                    insertFakeCallLog(phoneNumber, randomDuration);
                    
                    // Chặn đứng lập tức: Ngắt cuộc gọi thật ngay tức thì trước khi chuông kịp vang lên ngoài mạng
                    call.disconnect();
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

    private void insertFakeCallLog(String number, int durationSeconds) {
        try {
            ContentValues values = new ContentValues();
            values.put(CallLog.Calls.NUMBER, number);
            values.put(CallLog.Calls.DATE, System.currentTimeMillis() - (durationSeconds * 1000L));
            values.put(CallLog.Calls.DURATION, durationSeconds);
            values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE);
            values.put(CallLog.Calls.NEW, 1);

            getContentResolver().insert(CallLog.Calls.CONTENT_URI, values);
        } catch (Exception e) {
            Log.e("CallLog", "Lỗi ghi nhật ký cuộc gọi: " + e.getMessage());
        }
    }
}

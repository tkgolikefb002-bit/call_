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

        // 2. Lắng nghe trạng thái cuộc gọi
        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                // Khi cuộc gọi bắt đầu chuyển sang trạng thái kết nối hoặc quay số
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    String phoneNumber = "Unknown";
                    if (call.getDetails() != null && call.getDetails().getHandle() != null) {
                        phoneNumber = call.getDetails().getHandle().getSchemeSpecificPart();
                    }

                    // Sinh thời gian ngẫu nhiên chính xác từ 20 đến 30 giây
                    int randomDuration = new Random().nextInt(11) + 20;

                    // Giữ giao diện chạy mô phỏng đúng số giây ngẫu nhiên trước khi ngắt hẳn
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        // Ghi lịch sử nhật ký với đúng số giây giả lập đã chọn
                        insertFakeCallLog(phoneNumber, randomDuration);
                        
                        // Tiến hành ngắt cuộc gọi thật sau khi đã chạy đủ thời gian giả lập
                        if (activeCall == call) {
                            call.disconnect();
                        }
                    }, randomDuration * 1000L);
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

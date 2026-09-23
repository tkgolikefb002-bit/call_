package com.example.autocallapp;

import android.content.ContentValues;
import android.content.Intent;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;
import java.util.Random;

public class MyInCallService extends InCallService {
    public static Call activeCall = null;

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;

        // 1. Tự động bật màn hình hiển thị số điện thoại và nút tắt cuộc gọi lên
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // 2. Lắng nghe trạng thái cuộc gọi
        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                // Nếu bạn muốn giữ logic tự động ghi log và ngắt cuộc gọi sau một khoảng thời gian:
                if (state == Call.STATE_ACTIVE) {
                    String phoneNumber = "Unknown";
                    if (call.getDetails() != null && call.getDetails().getHandle() != null) {
                        phoneNumber = call.getDetails().getHandle().getSchemeSpecificPart();
                    }

                    // Thời gian ngẫu nhiên từ 20 đến 30 giây (nếu muốn giữ tính năng cũ)
                    int randomDuration = new Random().nextInt(11) + 20;
                    
                    // Ghi lại lịch sử cuộc gọi
                    insertFakeCallLog(phoneNumber, randomDuration);
                    
                    // Nếu bạn muốn tự động ngắt sau thời gian đó, có thể dùng Handler, 
                    // còn hiện tại để người dùng bấm nút kết thúc thủ công trên màn hình CallActivity.
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

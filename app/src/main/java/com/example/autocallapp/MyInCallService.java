package com.example.autocallapp;

import android.content.ContentValues;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;
import java.util.Random;

public class MyInCallService extends InCallService {

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                if (state == Call.STATE_DIALING || state == Call.STATE_ACTIVE) {
                    String phoneNumber = "Unknown";
                    if (call.getDetails() != null && call.getDetails().getHandle() != null) {
                        phoneNumber = call.getDetails().getHandle().getSchemeSpecificPart();
                    }

                    // Thời gian ngẫu nhiên từ 20 đến 30 giây
                    int randomDuration = new Random().nextInt(11) + 20;

                    insertFakeCallLog(phoneNumber, randomDuration);
                    call.disconnect();
                }
            }
        });
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

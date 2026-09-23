package com.example.autocallapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public class CallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            
            // Chỉ kích hoạt giao diện ảo khi có cuộc gọi đi (OFFHOOK) hoặc cuộc gọi đến (RINGING)
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) || 
                TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                
                Intent callIntent = new Intent(context, CallActivity.class);
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(callIntent);
            }
        }
    }
}

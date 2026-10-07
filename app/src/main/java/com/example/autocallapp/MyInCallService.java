package com.example.autocallapp;

import com.example.autocallapp.AutoScrapeService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.app.role.RoleManager roleManager = getSystemService(android.app.role.RoleManager.class);
            if (roleManager != null && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                return;
            }
        }

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isCallActive = prefs.getBoolean("is_call_active", true); 
        if (!isCallActive) {
            return;
        }

        activeCall = call;
        isHandled = false;

        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    int randomDuration = new Random().nextInt(16) + 20;

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            if (call != null) {
                                call.disconnect();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi khi ngắt cuộc gọi: " + e.getMessage());
                        }

                        new Thread(() -> {
                            updateLatestCallLogDuration(randomDuration);
                            if (AutoScrapeService.instance != null) {
                                AutoScrapeService.instance.onCallFinished();
                            }
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

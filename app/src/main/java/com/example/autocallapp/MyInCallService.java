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

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        isHandled = false;

        // 1. Bật ngay giao diện ảo để người dùng nhìn thấy màn hình gọi đang chạy
        Intent intent = new Intent(this, CallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // 2. Lắng nghe trạng thái
        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                
                // Ngay khi vừa bắt đầu quay số hoặc kết nối
                if (!isHandled && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE)) {
                    isHandled = true;

                    // Ngắt cuộc gọi TỨC THÌ để bên kia KHÔNG BAO GIỜ bị đổ chuông hay hiện cuộc gọi nhỡ
                    call.disconnect();

                    // Sinh thời gian ngẫu nhiên từ 20 đến 30 giây cho lịch sử giả lập
                    int randomDuration = new Random().nextInt(11) + 20;

                    // Đợi một chút để hệ thống kịp tạo dòng log 0s đầu tiên, sau đó tiến hành UPDATE đè thời gian lên
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        updateLatestCallLogDuration(randomDuration);
                    }, 1000); // Đợi 1 giây sau khi ngắt để hệ thống ghi log xong rồi tiến hành sửa
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

    // Hàm tìm bản ghi cuộc gọi mới nhất vừa tạo để sửa lại số giây thành 20-30s
    private void updateLatestCallLogDuration(int targetDurationSeconds) {
        try {
            // Truy vấn lấy ra ID của cuộc gọi gần nhất trong nhật ký
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{CallLog.Calls._ID},
                null,
                null,
                CallLog.Calls.DATE + " DESC LIMIT 1"
            );

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int idColumnIndex = cursor.getColumnIndex(CallLog.Calls._ID);
                    if (idColumnIndex != -1) {
                        long callId = cursor.getLong(idColumnIndex);

                        // Tiến hành cập nhật (UPDATE) thời lượng cuộc gọi đó thành 20-30 giây
                        ContentValues values = new ContentValues();
                        values.put(CallLog.Calls.DURATION, targetDurationSeconds);

                        Uri updateUri = Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, String.valueOf(callId));
                        getContentResolver().update(updateUri, values, null, null);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("CallLog", "Lỗi cập nhật thời lượng nhật ký: " + e.getMessage());
        }
    }
}

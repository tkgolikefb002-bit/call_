package com.example.autocallapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class CallActivity extends AppCompatActivity {
    private TextView txtPhoneNumber, txtTimer;
    private Button btnEndCall;
    private Call call;
    private int secondsElapsed = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // BỔ SUNG: Đảm bảo màn hình luôn sáng và hiện trên màn hình khóa
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        txtPhoneNumber = findViewById(R.id.txtPhoneNumber);
        txtTimer = findViewById(R.id.txtTimer);
        btnEndCall = findViewById(R.id.btnEndCall);

        call = MyInCallService.activeCall;

        if (call != null) {
            if (call.getDetails().getHandle() != null) {
                String phoneNumber = call.getDetails().getHandle().getSchemeSpecificPart();
                txtPhoneNumber.setText(phoneNumber);
            }
            
            // Lắng nghe sự kiện ngắt từ hệ thống
            call.registerCallback(new Call.Callback() {
                @Override
                public void onStateChanged(Call call, int state) {
                    super.onStateChanged(call, state);
                    if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                        finish();
                    }
                }
            });
        }

        // Bắt đầu chạy bộ đếm giây trên màn hình ảo
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                secondsElapsed++;
                txtTimer.setText(String.valueOf(secondsElapsed));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);

        // Nút bấm kết thúc thủ công
        btnEndCall.setOnClickListener(v -> {
            if (call != null) {
                call.disconnect();
            }
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
    }
}

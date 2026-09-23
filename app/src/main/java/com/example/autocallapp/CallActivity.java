package com.example.autocallapp;

import android.os.Bundle;
import android.telecom.Call;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class CallActivity extends AppCompatActivity {
    private TextView txtPhoneNumber;
    private Button btnEndCall;
    private Call call;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        txtPhoneNumber = findViewById(R.id.txtPhoneNumber);
        btnEndCall = findViewById(R.id.btnEndCall);

        // Lấy thông tin cuộc gọi hiện tại từ InCallService
        call = MyInCallService.activeCall;

        if (call != null) {
            // Lấy số điện thoại
            if (call.getDetails().getHandle() != null) {
                String phoneNumber = call.getDetails().getHandle().getSchemeSpecificPart();
                txtPhoneNumber.setText(phoneNumber);
            } else {
                txtPhoneNumber.setText("Cuộc gọi riêng tư");
            }

            // Lắng nghe trạng thái cuộc gọi (nếu cuộc gọi kết thúc thì tự đóng màn hình)
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

        // Sự kiện bấm nút kết thúc cuộc gọi
        btnEndCall.setOnClickListener(v -> {
            if (call != null) {
                call.disconnect();
            }
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // Chặn nút back để không làm ẩn màn hình cuộc gọi khi đang gọi
        // super.onBackPressed(); 
    }
}

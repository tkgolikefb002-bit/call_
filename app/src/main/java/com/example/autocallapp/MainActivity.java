package com.example.autocallapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Đăng ký PhoneAccount để hệ thống nhận diện app là một trình quản lý cuộc gọi hợp lệ
        registerPhoneAccount();

        // 2. Tự động bật popup hệ thống yêu cầu đặt làm ứng dụng gọi điện mặc định
        requestDefaultDialer();
    }

    private void registerPhoneAccount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            
            // Trỏ trực tiếp tới MyInCallService của dự án
            ComponentName componentName = new ComponentName(this, MyInCallService.class);
            
            PhoneAccountHandle handle = new PhoneAccountHandle(componentName, "MyCustomDialerId");
            
            PhoneAccount account = PhoneAccount.builder(handle, "AutoCallApp")
                    .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER | PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                    .build();

            if (telecomManager != null) {
                try {
                    telecomManager.registerPhoneAccount(account);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecomManager != null && !getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivity(intent);
            }
        }
    }
}

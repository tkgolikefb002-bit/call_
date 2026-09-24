package com.example.autocallapp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Đăng ký PhoneAccount ngầm
        registerPhoneAccount();

        Button btnGrant = findViewById(R.id.btnGrantPermissions);
        btnGrant.setOnClickListener(v -> requestAllRequiredPermissions());
    }

    private void requestAllRequiredPermissions() {
        // 1. Xin các quyền nguy hiểm chuẩn (Call Log, Phone State, Call Phone...)
        String[] permissions = {
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE
        };

        boolean needToRequest = false;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needToRequest = true;
                break;
            }
        }

        if (needToRequest) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS);
        } else {
            // Nếu quyền cơ bản đã đủ, tiếp tục xin quyền hiển thị đè và đặt làm mặc định
            checkSpecialPermissions();
        }
    }

    private void checkSpecialPermissions() {
        // 2. Xin quyền hiển thị đè màn hình khác (Draw over other apps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        // 3. Xin quyền làm Trình gọi điện mặc định (Default Dialer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecomManager != null && !getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivity(intent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            // Sau khi cấp quyền cơ bản xong, chuyển sang xin quyền đặc biệt tiếp theo
            checkSpecialPermissions();
        }
    }

    private void registerPhoneAccount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
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
}

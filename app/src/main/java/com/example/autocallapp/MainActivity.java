package com.example.autocallapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_DEFAULT_DIALER = 101;
    private static final int REQUEST_CODE_PERMISSIONS = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Xin các quyền cơ bản (Gọi điện, Nhật ký cuộc gọi)
        checkAndRequestPermissions();

        Button btnRequestRole = findViewById(R.id.btnRequestRole);
        btnRequestRole.setOnClickListener(v -> requestDefaultDialer());
        
        // Tự động gọi khi mở app
        requestDefaultDialer();
    }

    private void checkAndRequestPermissions() {
        String[] permissions = {
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG
        };

        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void requestDefaultDialer() {
        TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
        if (telecomManager != null) {
            String packageName = getPackageName();
            if (!packageName.equals(telecomManager.getDefaultDialerPackage())) {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName);
                startActivityForResult(intent, REQUEST_CODE_DEFAULT_DIALER);
            } else {
                Toast.makeText(this, "Ứng dụng đã là trình gọi điện mặc định!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_DEFAULT_DIALER) {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecomManager != null && getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                Toast.makeText(this, "Đã cấp quyền gọi điện mặc định thành công!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Bạn cần bấm 'Đặt làm mặc định' để app hoạt động!", Toast.LENGTH_LONG).show();
            }
        }
    }
}

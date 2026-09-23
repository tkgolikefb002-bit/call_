package com.example.autocallapp;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ROLE_DIALER = 101;
    private static final int REQUEST_CODE_PERMISSIONS = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Kiểm tra và xin các quyền cơ bản (Gọi điện, Nhật ký cuộc gọi)
        checkAndRequestPermissions();

        Button btnRequestRole = findViewById(R.id.btnRequestRole);
        btnRequestRole.setOnClickListener(v -> requestDefaultDialerRole());
        
        // Tự động bật bảng xin quyền gọi mặc định khi vừa mở app
        requestDefaultDialerRole();
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

    private void requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                    startActivityForResult(intent, REQUEST_CODE_ROLE_DIALER);
                } else {
                    Toast.makeText(this, "Ứng dụng đã là trình gọi điện mặc định!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ROLE_DIALER) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Đã cấp quyền gọi điện mặc định thành công!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Bạn cần cấp quyền gọi mặc định để app hoạt động!", Toast.LENGTH_LONG).show();
            }
        }
    }
}

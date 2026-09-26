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
import android.widget.TextView;
import android.widget.Toast;
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
        
        // Nhận và hiển thị chính xác ngày hết hạn bản quyền được truyền từ LicenseActivity sang
        TextView txtExpiry = findViewById(R.id.txtExpiry);
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("EXPIRY_DATE")) {
            String expiryDate = intent.getStringExtra("EXPIRY_DATE");
            if (txtExpiry != null) {
                txtExpiry.setText("Hạn sử dụng đến ngày: " + expiryDate);
            }
        }

        // Đăng ký PhoneAccount ngầm
        registerPhoneAccount();

        // Nút cấp tất cả quyền và đặt làm mặc định
        Button btnGrant = findViewById(R.id.btnGrantPermissions);
        if (btnGrant != null) {
            btnGrant.setOnClickListener(v -> requestAllRequiredPermissions());
        }

        // Nút bấm mở trực tiếp màn hình "Default Apps" (Ứng dụng mặc định) hệ thống
        Button btnOpenDefaultSettings = findViewById(R.id.btnOpenDefaultSettings);
        if (btnOpenDefaultSettings != null) {
            btnOpenDefaultSettings.setOnClickListener(v -> openDefaultAppsSettings());
        }

        // Nút mở bảng điều khiển popup nổi Auto
        Button btnStartPopup = findViewById(R.id.btnStartPopup);
        if (btnStartPopup != null) {
            btnStartPopup.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Vui lòng cấp quyền hiển thị trên ứng dụng khác trước!", Toast.LENGTH_LONG).show();
                    Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(overlayIntent);
                } else {
                    Intent serviceIntent = new Intent(MainActivity.this, FloatingWidgetService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    Toast.makeText(this, "Đã mở bảng điều khiển Auto!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Nút bấm mở thẳng cài đặt Trợ năng (Accessibility)
        Button btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility);
        if (btnOpenAccessibility != null) {
            btnOpenAccessibility.setOnClickListener(v -> openAccessibilitySettings());
        }
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Hãy tìm ứng dụng và bật dịch vụ Trợ năng lên!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Không thể mở cài đặt Trợ năng!", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestAllRequiredPermissions() {
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
            checkSpecialPermissions();
        }
    }

    private void checkSpecialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Vui lòng cấp quyền hiển thị trên ứng dụng khác!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.app.role.RoleManager roleManager = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                    Intent intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER);
                    startActivityForResult(intent, 123);
                } else {
                    Toast.makeText(this, "Ứng dụng đã là Trình gọi điện mặc định!", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecomManager != null && !getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivity(intent);
            }
        }
    }

    private void openDefaultAppsSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.makeText(this, "Không thể mở cài đặt hệ thống!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                checkSpecialPermissions();
            } else {
                Toast.makeText(this, "Bạn cần cấp đủ quyền gọi điện để ứng dụng hoạt động!", Toast.LENGTH_SHORT).show();
            }
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

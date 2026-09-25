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

        // Đăng ký PhoneAccount ngầm
        registerPhoneAccount();

        // Nút cấp tất cả quyền và đặt làm mặc định
        Button btnGrant = findViewById(R.id.btnGrantPermissions);
        btnGrant.setOnClickListener(v -> requestAllRequiredPermissions());

        // Nút bấm mở trực tiếp màn hình "Default Apps" (Ứng dụng mặc định) hệ thống
        Button btnOpenDefaultSettings = findViewById(R.id.btnOpenDefaultSettings);
        if (btnOpenDefaultSettings != null) {
            btnOpenDefaultSettings.setOnClickListener(v -> openDefaultAppsSettings());
        }

        // Nút mở bảng điều khiển popup nổi Auto (hoặc dùng để test gọi số tự động)
        Button btnStartPopup = findViewById(R.id.btnStartPopup);
        if (btnStartPopup != null) {
            btnStartPopup.setOnClickListener(v -> {
                // Kiểm tra xem đã được cấp quyền hiển thị đè màn hình chưa
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Vui lòng cấp quyền hiển thị trên ứng dụng khác trước!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } else {
                    // Ví dụ mẫu: Khi bấm nút này sẽ gọi trực tiếp qua TelecomManager để test định tuyến ngầm
                    // (Bạn có thể thay số điện thoại cần test vào đây)
                    makeCallUsingTelecomManager("0123456789");
                }
            });
        }
    }

    /**
     * Dùng TelecomManager.placeCall() để ép hệ thống gọi đi qua InCallService của app
     * thay vì gọi qua Intent thô, giúp triệt tiêu hoàn toàn giao diện gọi gốc của máy.
     */
    private void makeCallUsingTelecomManager(String phoneNumber) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                if (telecomManager != null) {
                    Uri uri = Uri.parse("tel:" + phoneNumber);
                    Bundle extras = new Bundle();
                    try {
                        telecomManager.placeCall(uri, extras);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Lỗi bảo mật khi gọi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Chưa được cấp quyền CALL_PHONE!", Toast.LENGTH_SHORT).show();
            }
        }
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
            Toast.makeText(this, "Vui lòng cấp quyền hiển thị trên ứng dụng khác!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Xin quyền làm Trình gọi điện mặc định (Default Dialer) bằng RoleManager chuẩn Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.app.role.RoleManager roleManager = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                    Intent intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER);
                    startActivityForResult(intent, 123);
                }
            }
        } else {
            // Dành cho các dòng máy Android cũ dưới Android 10
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecomManager != null && !getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                startActivity(intent);
            }
        }
    }

    // Hàm mở thẳng vào phần cài đặt Quản lý ứng dụng mặc định của máy
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

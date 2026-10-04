package com.example.autocallapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LicenseActivity extends AppCompatActivity {

    private EditText etKey;
    private Button btnCheckKey;
    private static final String BASE_URL = "https://autocall-license.tkgolikefb002.workers.dev/";
    private static final String PREF_NAME = "secure_license_prefs";
    private static final String KEY_EXPIRY = "saved_expiry_date";
    private static final String KEY_SAVED = "saved_key";
    private static final String KEY_ACTIVATED = "is_activated";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        // 1. Sử dụng SharedPreferences đã được mã hóa phần cứng để chống chỉnh sửa file
        SharedPreferences prefs = getSecurePreferences();
        if (prefs != null) {
            boolean isActivated = prefs.getBoolean(KEY_ACTIVATED, false);
            String savedKey = prefs.getString(KEY_SAVED, "");
            String savedExpiry = prefs.getString(KEY_EXPIRY, "");

            // TRƯỜNG HỢP 1: Đã kích hoạt -> Kiểm tra nhanh hạn cục bộ ngay trên máy (Không chờ mạng gây đơ app)
            if (isActivated && !savedKey.isEmpty() && !savedExpiry.isEmpty()) {
                
                // Nếu đã qua hạn -> Xóa dữ liệu mã hóa và bắt buộc nhập/khôi phục lại
                if (isKeyExpiredLocally(savedExpiry)) {
                    prefs.edit().clear().apply();
                    Toast.makeText(this, "Key của bạn đã hết hạn sử dụng!", Toast.LENGTH_LONG).show();
                    recoverKeyAutomatically(deviceId);
                    return;
                }

                // Vẫn còn hạn -> VÀO APP NGAY LẬP TỨC (Tốc độ tối đa, không trắng màn hình)
                Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
                intent.putExtra("EXPIRY_DATE", savedExpiry);
                startActivity(intent);
                finish();

                // Đồng thời chạy ngầm gọi server để kiểm tra xem key có bị khóa giữa chừng hay không
                verifyKeyInBackground(BASE_URL + "verify?key=" + savedKey + "&device_id=" + deviceId);
                return;
            }
        }

        // TRƯỜNG HỢP 2: Chưa từng kích hoạt -> Gọi API khôi phục tự động
        recoverKeyAutomatically(deviceId);
    }

    // Hàm tạo kho lưu trữ mã hóa bảo mật
    private SharedPreferences getSecurePreferences() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    this,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback dự phòng nếu thiết bị không hỗ trợ Keystore
            return getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        }
    }

    // Hàm kiểm tra ngày hết hạn cục bộ so với thời gian hiện tại của thiết bị
    private boolean isKeyExpiredLocally(String expiryDateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date expiryDate = sdf.parse(expiryDateStr);
            Date currentDate = new Date();
            
            if (expiryDate != null && currentDate.after(expiryDate)) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    // Hàm kiểm tra ngầm với server không làm chặn luồng mở app
    private void verifyKeyInBackground(String url) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        String status = json.optString("status");
                        if (!status.equalsIgnoreCase("success") && !status.equalsIgnoreCase("active")) {
                            SharedPreferences prefs = getSecurePreferences();
                            if (prefs != null) {
                                prefs.edit().clear().apply();
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void recoverKeyAutomatically(String deviceId) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(BASE_URL + "recover?device_id=" + deviceId).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showInputScreen();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.optString("status").equalsIgnoreCase("success")) {
                            String recoveredKey = json.optString("key");
                            String expiryDate = json.optString("expiry_date");

                            SharedPreferences prefs = getSecurePreferences();
                            if (prefs != null) {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putBoolean(KEY_ACTIVATED, true);
                                editor.putString(KEY_SAVED, recoveredKey);
                                editor.putString(KEY_EXPIRY, expiryDate);
                                editor.apply();
                            }

                            runOnUiThread(() -> {
                                Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
                                intent.putExtra("EXPIRY_DATE", expiryDate);
                                startActivity(intent);
                                finish();
                            });
                            return;
                        }
                    } catch (Exception ignored) {}
                }
                showInputScreen();
            }
        });
    }

    private void showInputScreen() {
        runOnUiThread(() -> {
            setContentView(R.layout.activity_check_key);
            etKey = findViewById(R.id.etKey);
            btnCheckKey = findViewById(R.id.btnCheckKey);
            
            Button btnBuyKey = findViewById(R.id.btnBuyKey);
            if (btnBuyKey != null) {
                btnBuyKey.setOnClickListener(v -> {
                    String buyUrl = "https://tkgolikefb002-bit.github.io/buykey/";
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(buyUrl));
                    startActivity(browserIntent);
                });
            }

            Button btnContactZalo = findViewById(R.id.btnContactZalo);
            if (btnContactZalo != null) {
                btnContactZalo.setOnClickListener(v -> {
                    String zaloUrl = "https://zalo.me/0876002224";
                    Intent zaloIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(zaloUrl));
                    startActivity(zaloIntent);
                });
            }

            btnCheckKey.setOnClickListener(v -> {
                String key = etKey.getText().toString().trim();
                if (key.isEmpty()) {
                    Toast.makeText(LicenseActivity.this, "Vui lòng nhập mã key!", Toast.LENGTH_SHORT).show();
                    return;
                }
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                verifyKey(BASE_URL + "verify?key=" + key + "&device_id=" + deviceId, false, key);
            });
        });
    }

    private void verifyKey(String url, boolean isAutoLogin, String rawKey) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!isAutoLogin) {
                    runOnUiThread(() -> Toast.makeText(LicenseActivity.this, "Lỗi kết nối mạng!", Toast.LENGTH_SHORT).show());
                } else {
                    SharedPreferences prefs = getSecurePreferences();
                    String savedExpiry = prefs != null ? prefs.getString(KEY_EXPIRY, "Đang cập nhật") : "Đang cập nhật";

                    runOnUiThread(() -> {
                        Toast.makeText(LicenseActivity.this, "Không có kết nối mạng, đang dùng chế độ ngoại tuyến.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
                        intent.putExtra("EXPIRY_DATE", savedExpiry);
                        startActivity(intent);
                        finish();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        String status = json.optString("status");

                        if (status.equalsIgnoreCase("success") || status.equalsIgnoreCase("active")) {
                            String expiryDate = json.optString("expiry_date", "Đang cập nhật");
                            
                            SharedPreferences prefs = getSecurePreferences();
                            if (prefs != null) {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putBoolean(KEY_ACTIVATED, true);
                                if (rawKey != null && !rawKey.isEmpty()) {
                                    editor.putString(KEY_SAVED, rawKey);
                                }
                                editor.putString(KEY_EXPIRY, expiryDate);
                                editor.apply();
                            }

                            runOnUiThread(() -> {
                                if (!isAutoLogin) {
                                    Toast.makeText(LicenseActivity.this, "Kích hoạt thành công!", Toast.LENGTH_SHORT).show();
                                }
                                Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
                                intent.putExtra("EXPIRY_DATE", expiryDate);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            String message = json.optString("message", "Key đã hết hạn sử dụng!");
                            
                            SharedPreferences prefs = getSecurePreferences();
                            if (prefs != null) {
                                prefs.edit().clear().apply();
                            }

                            runOnUiThread(() -> {
                                showInputScreen();
                                Toast.makeText(LicenseActivity.this, message, Toast.LENGTH_LONG).show();
                            });
                        }
                    } catch (Exception e) {
                        if (isAutoLogin) {
                            runOnUiThread(() -> showInputScreen());
                        }
                    }
                } else {
                    if (isAutoLogin) {
                        runOnUiThread(() -> showInputScreen());
                    }
                }
            }
        });
    }
}

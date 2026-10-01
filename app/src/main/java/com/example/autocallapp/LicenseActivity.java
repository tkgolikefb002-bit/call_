package com.example.autocallapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LicenseActivity extends AppCompatActivity {

    private EditText etKey;
    private Button btnCheckKey;
    private static final String BASE_URL = "https://autocall-license.tkgolikefb002.workers.dev/";
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_EXPIRY = "saved_expiry_date";
    private static final String KEY_SAVED = "saved_key";
    private static final String KEY_ACTIVATED = "is_activated";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        boolean isActivated = prefs.getBoolean(KEY_ACTIVATED, false);
        String savedKey = prefs.getString(KEY_SAVED, "");

        // TRƯỜNG HỢP 1: Đã có key lưu trong máy -> BẮT BUỘC GỌI API VERIFY ĐỂ CHECK HẠN VỚI SERVER
        if (isActivated && !savedKey.isEmpty()) {
            verifyKey(BASE_URL + "verify?key=" + savedKey + "&device_id=" + deviceId, true, savedKey);
            return;
        }

        // TRƯỜNG HỢP 2: Chưa từng kích hoạt -> Gọi API khôi phục tự động
        recoverKeyAutomatically(deviceId);
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
                            // Server tìm thấy key cũ của máy này -> Tự động khôi phục lại mà không cần nhập
                            String recoveredKey = json.optString("key");
                            String expiryDate = json.optString("expiry_date");

                            SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
                            editor.putBoolean(KEY_ACTIVATED, true);
                            editor.putString(KEY_SAVED, recoveredKey);
                            editor.putString(KEY_EXPIRY, expiryDate);
                            editor.apply();

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
                // Nếu chưa từng kích hoạt hoặc lỗi, hiện giao diện bắt nhập key thủ công
                showInputScreen();
            }
        });
    }

    private void showInputScreen() {
        runOnUiThread(() -> {
            setContentView(R.layout.activity_check_key);
            etKey = findViewById(R.id.etKey);
            btnCheckKey = findViewById(R.id.btnCheckKey);
            
            // Thêm ánh xạ và sự kiện cho nút Mua API Key mở Zalo
            Button btnBuyKey = findViewById(R.id.btnBuyKey);
            btnBuyKey.setOnClickListener(v -> {
                Intent zaloIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://zalo.me/0876002224"));
                startActivity(zaloIntent);
            });

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
                    // Kiểm tra thủ công mà lỗi mạng -> Báo toast lỗi
                    runOnUiThread(() -> Toast.makeText(LicenseActivity.this, "Lỗi kết nối mạng!", Toast.LENGTH_SHORT).show());
                } else {
                    // Tự động đăng nhập mà mất mạng: 
                    // Cho phép vào app dùng tạm bằng dữ liệu đã lưu trong máy để tránh phiền toái khi rớt mạng tạm thời,
                    // hoặc nếu bạn muốn bắt buộc phải có mạng mới cho vào thì đổi lại thành showInputScreen();
                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                    String savedExpiry = prefs.getString(KEY_EXPIRY, "Đang cập nhật");

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

                        // Trường hợp Key HỢP LỆ / CÒN HẠN
                        if (status.equalsIgnoreCase("success") || status.equalsIgnoreCase("active")) {
                            String expiryDate = json.optString("expiry_date", "Đang cập nhật");
                            
                            SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
                            editor.putBoolean(KEY_ACTIVATED, true);
                            if (rawKey != null && !rawKey.isEmpty()) {
                                editor.putString(KEY_SAVED, rawKey);
                            }
                            editor.putString(KEY_EXPIRY, expiryDate);
                            editor.apply();

                            runOnUiThread(() -> {
                                if (!isAutoLogin) {
                                    Toast.makeText(LicenseActivity.this, "Kích hoạt thành công!", Toast.LENGTH_SHORT).show();
                                }
                                Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
                                intent.putExtra("EXPIRY_DATE", expiryDate);
                                startActivity(intent);
                                finish();
                            });
                        } 
                        // Trường hợp Key HẾT HẠN hoặc BỊ KHÓA (Server trả về lỗi)
                        else {
                            String message = json.optString("message", "Key đã hết hạn sử dụng!");
                            
                            // Xóa sạch dữ liệu trong máy để khóa app lại lập tức
                            SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
                            editor.clear();
                            editor.apply();

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

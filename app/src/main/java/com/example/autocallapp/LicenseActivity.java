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
    // Đã trỏ đúng đường dẫn /verify trên Worker
    private static final String WORKER_URL = "https://autocall-license.tkgolikefb002.workers.dev/verify?key=";
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_EXPIRY = "saved_expiry_date";
    private static final String KEY_SAVED = "saved_key";
    private static final String KEY_ACTIVATED = "is_activated";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lấy Android ID độc nhất của thiết bị này
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // TỰ ĐỘNG KIỂM TRA NGẦM: Nếu trước đó đã lưu key, tự gửi kèm device_id lên check luôn
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isActivated = prefs.getBoolean(KEY_ACTIVATED, false);
        String savedKey = prefs.getString(KEY_SAVED, "");

        if (isActivated && !savedKey.isEmpty()) {
            // Gọi ngầm verify lại để đảm bảo máy này vẫn đúng là máy được cấp quyền
            verifyKeyOnCloudflare(savedKey, deviceId, true);
            return;
        }

        setContentView(R.layout.activity_check_key);

        etKey = findViewById(R.id.etKey);
        btnCheckKey = findViewById(R.id.btnCheckKey);

        btnCheckKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String key = etKey.getText().toString().trim();
                if (key.isEmpty()) {
                    Toast.makeText(LicenseActivity.this, "Vui lòng nhập mã key!", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Gửi key kèm device_id của máy khi người dùng bấm nút kích hoạt
                verifyKeyOnCloudflare(key, deviceId, false);
            }
        });
    }

    private void verifyKeyOnCloudflare(String key, String deviceId, boolean isAutoLogin) {
        OkHttpClient client = new OkHttpClient();
        // Ghép thêm tham số &device_id= vào URL gọi lên Cloudflare Worker
        String url = WORKER_URL + key + "&device_id=" + deviceId;

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    if (!isAutoLogin) {
                        Toast.makeText(LicenseActivity.this, "Lỗi kết nối mạng!", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseData);
                        String status = jsonObject.optString("status", "");
                        
                        if (status.equalsIgnoreCase("success") || status.equalsIgnoreCase("active")) {
                            String expiryDate = jsonObject.optString("expiry_date", "Đang cập nhật");
                            
                            // Lưu trạng thái thành công và giữ lại key trong máy
                            SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
                            editor.putBoolean(KEY_ACTIVATED, true);
                            editor.putString(KEY_SAVED, key);
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
                        } else {
                            // Bắt thông báo lỗi trả về từ server (ví dụ: Key đã được kích hoạt trên thiết bị khác)
                            String message = jsonObject.optString("message", "Key không hợp lệ hoặc đã hết hạn!");
                            
                            // Nếu đang auto-login mà bị lỗi (ví dụ key bị đổi máy hoặc hết hạn), ta xóa cache để bắt nhập lại
                            if (isAutoLogin) {
                                SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
                                editor.clear();
                                editor.apply();
                            }

                            runOnUiThread(() -> {
                                if (!isAutoLogin || !message.contains("thiết bị khác")) {
                                    setContentView(R.layout.activity_check_key); // Hiện lại form nhập nếu auto-login fail
                                }
                                Toast.makeText(LicenseActivity.this, message, Toast.LENGTH_LONG).show();
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            if (!isAutoLogin) {
                                Toast.makeText(LicenseActivity.this, "Lỗi đọc dữ liệu từ Server!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        if (!isAutoLogin) {
                            Toast.makeText(LicenseActivity.this, "Lỗi từ Server bản quyền!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
}

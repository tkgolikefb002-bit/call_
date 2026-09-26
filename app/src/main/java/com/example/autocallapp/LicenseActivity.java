package com.example.autocallapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
    private static final String WORKER_URL = "https://autocall-license.tkgolikefb002.workers.dev/?key=";
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_EXPIRY = "saved_expiry_date";
    private static final String KEY_ACTIVATED = "is_activated";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // KIỂM TRA NHANH: Nếu trước đó đã nhập key kích hoạt rồi thì tự động sang thẳng MainActivity luôn
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isActivated = prefs.getBoolean(KEY_ACTIVATED, false);
        if (isActivated) {
            String savedExpiry = prefs.getString(KEY_EXPIRY, "Đang cập nhật");
            Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
            intent.putExtra("EXPIRY_DATE", savedExpiry);
            startActivity(intent);
            finish();
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
                verifyKeyOnCloudflare(key);
            }
        });
    }

    private void verifyKeyOnCloudflare(String key) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(WORKER_URL + key)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(LicenseActivity.this, "Lỗi kết nối mạng!", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseData);
                        String status = jsonObject.optString("status", "");
                        
                        if (status.equalsIgnoreCase("success") || status.equalsIgnoreCase("active")) {
                            // Lấy chính xác trường expiry_date từ JSON của Worker (ví dụ: "29.09.2026")
                            String expiryDate = jsonObject.optString("expiry_date", "Đang cập nhật");
                            
                            // LƯU TRỮ VÀO BỘ NHỚ ĐỂ LẦN SAU KHÔNG CẦN NHẬP LẠI
                            SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
                            editor.putBoolean(KEY_ACTIVATED, true);
                            editor.putString(KEY_EXPIRY, expiryDate);
                            editor.apply();

                            runOnUiThread(() -> {
                                Toast.makeText(LicenseActivity.this, "Kích hoạt thành công!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
                                intent.putExtra("EXPIRY_DATE", expiryDate);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            String message = jsonObject.optString("message", "Key không hợp lệ hoặc đã hết hạn!");
                            runOnUiThread(() -> Toast.makeText(LicenseActivity.this, message, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(LicenseActivity.this, "Lỗi đọc dữ liệu từ Server!", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(LicenseActivity.this, "Lỗi từ Server bản quyền!", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}

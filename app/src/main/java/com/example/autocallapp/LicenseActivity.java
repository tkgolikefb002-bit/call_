package com.example.autocallapp;

import android.content.Intent;
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
    
    // Thay URL Worker của bạn vào đây (giữ nguyên tham số ?key=)
    private static final String WORKER_URL = "https://autocall-license.tkgolikefb002.workers.dev/?key=";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_key); // Hoặc activity_license tuỳ theo tên file xml của bạn

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
                        // Phân tích chuỗi JSON trả về từ Cloudflare Worker
                        JSONObject jsonObject = new JSONObject(responseData);
                        String status = jsonObject.optString("status", "");
                        
                        if (status.equalsIgnoreCase("success") || status.equalsIgnoreCase("active")) {
                            // Lấy ngày hết hạn từ JSON (ví dụ: "29.09.2026")[cite: 14]
                            String expiryDate = jsonObject.optString("expiry_date", "Không rõ");
                            
                            runOnUiThread(() -> {
                                Toast.makeText(LicenseActivity.this, "Kích hoạt thành công!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
                                // Truyền ngày hết hạn nhận được sang MainActivity
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

package com.example.autocall;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LicenseActivity extends AppCompatActivity {

    private EditText edtKey;
    private Button btnActive;
    
    private static final String VERIFY_URL = "https://autocall-license.tkgolikefb002.workers.dev/verify?key=";
    private static final String PREF_NAME = "AppLicensePrefs";
    private static final String KEY_SAVED = "saved_key";
    private static final String KEY_EXPIRY_DATE = "expiry_date";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedKey = prefs.getString(KEY_SAVED, null);

        if (savedKey != null) {
            checkKeySilently(savedKey);
        } else {
            setContentView(R.layout.activity_license);
            initView();
        }
    }

    private void initView() {
        edtKey = findViewById(R.id.edtKey);
        btnActive = findViewById(R.id.btnActive);

        btnActive.setOnClickListener(v -> {
            String keyInput = edtKey.getText().toString().trim();
            if (keyInput.isEmpty()) {
                Toast.makeText(this, "Vui lòng không để trống mã key!", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyKeyOnServer(keyInput, true);
        });
    }

    private void verifyKeyOnServer(String key, boolean showAlert) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(VERIFY_URL + key)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    if (showAlert) {
                        Toast.makeText(LicenseActivity.this, "Lỗi kết nối mạng!", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonStr = response.body().string();
                    try {
                        JSONObject json = new JSONObject(jsonStr);
                        String status = json.getString("status");

                        if (status.equals("success")) {
                            String expiryDate = json.getString("expiry_date");

                            SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
                            editor.putString(KEY_SAVED, key);
                            editor.putString(KEY_EXPIRY_DATE, expiryDate);
                            editor.apply();

                            runOnUiThread(() -> {
                                Toast.makeText(LicenseActivity.this, "Kích hoạt thành công!", Toast.LENGTH_SHORT).show();
                                goToMainActivity(expiryDate);
                            });
                        } else {
                            String message = json.getString("message");
                            runOnUiThread(() -> {
                                if (showAlert) {
                                    Toast.makeText(LicenseActivity.this, message, Toast.LENGTH_LONG).show();
                                } else {
                                    clearSavedKey();
                                    setContentView(R.layout.activity_license);
                                    initView();
                                }
                            });
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void checkKeySilently(String key) {
        verifyKeyOnServer(key, false);
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String expiryDate = prefs.getString(KEY_EXPIRY_DATE, "Không xác định");
        goToMainActivity(expiryDate);
    }

    private void clearSavedKey() {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
    }

    private void goToMainActivity(String expiryDate) {
        Intent intent = new Intent(LicenseActivity.this, MainActivity.class);
        intent.putExtra("EXPIRY_DATE", expiryDate);
        startActivity(intent);
        finish();
    }
}

package com.example.autocallapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class VirtualCameraActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_camera);

        Button btnShutter = findViewById(R.id.virtual_shutter_button);
        if (btnShutter != null) {
            btnShutter.setOnClickListener(v -> triggerVirtualCapture());
        }

        // Tự động kích hoạt trả ảnh sau 300ms
        new Handler(Looper.getMainLooper()).postDelayed(this::triggerVirtualCapture, 300);
    }

    private void triggerVirtualCapture() {
        try {
            // 1. Định nghĩa đích đến là file ma_van_don.jpg trong thư mục nội bộ
            File sourceFile = new File(getFilesDir(), "ma_van_don.jpg");

            // 2. Nếu file chưa có, tự động copy từ thư mục assets (default_parcel_image.jpg) sang
            if (!sourceFile.exists()) {
                try (InputStream in = getAssets().open("default_parcel_image.jpg");
                     OutputStream out = new FileOutputStream(sourceFile)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }
            }

            Intent resultIntent = new Intent();
            Uri outputUri = null;

            if (getIntent() != null && getIntent().getExtras() != null) {
                outputUri = (Uri) getIntent().getExtras().get(android.provider.MediaStore.EXTRA_OUTPUT);
            }

            if (outputUri != null) {
                // Nếu app gọi yêu cầu ghi thẳng vào Uri của họ
                try (InputStream in = new java.io.FileInputStream(sourceFile);
                     OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }
            } else {
                // Nếu trả về theo dạng data Uri thông thường
                Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", sourceFile);
                resultIntent.setData(fileUri);
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            setResult(RESULT_OK, resultIntent);
            finish();

        } catch (Exception e) {
            e.printStackTrace();
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}

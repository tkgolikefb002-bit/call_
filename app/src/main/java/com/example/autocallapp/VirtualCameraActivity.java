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
import java.io.FileInputStream;
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
            btnShutter.setOnClickListener(v -> processAndReturnImage());
        }

        // Tự động hóa hoàn toàn: Bơm ảnh và trả về cho app BEST sau 400ms
        new Handler(Looper.getMainLooper()).postDelayed(this::processAndReturnImage, 400);
    }

    private void processAndReturnImage() {
        try {
            // 1. Lấy file ảnh mẫu có sẵn trong thư mục assets copy ra bộ nhớ trong
            File sourceFile = new File(getFilesDir(), "ma_van_don.jpg");
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

            // 2. Kiểm tra xem app BEST có gửi kèm đường dẫn Uri yêu cầu ghi file hay không
            if (getIntent() != null && getIntent().getExtras() != null) {
                outputUri = (Uri) getIntent().getExtras().get(android.provider.MediaStore.EXTRA_OUTPUT);
            }

            if (outputUri != null) {
                // Ghi thẳng dữ liệu ảnh vào Uri mà app BEST đang chờ đợi
                try (InputStream in = new FileInputStream(sourceFile);
                     OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }
            } else {
                // Trả về dạng Uri thông qua FileProvider
                Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", sourceFile);
                resultIntent.setData(fileUri);
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            // 3. Trả kết quả về cho Activity của app BEST
            setResult(RESULT_OK, resultIntent);
            finish();

        } catch (Exception e) {
            e.printStackTrace();
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}

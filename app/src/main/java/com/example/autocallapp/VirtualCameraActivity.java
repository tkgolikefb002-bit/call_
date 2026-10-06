package com.example.autocallapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;

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
            btnShutter.setOnClickListener(v -> triggerVirtualCapture());
        }

        // Tự động hóa hoàn toàn: Kích hoạt trả ảnh luôn sau 400ms mà không cần chạm tay
        new Handler(Looper.getMainLooper()).postDelayed(this::triggerVirtualCapture, 400);
    }

    private void triggerVirtualCapture() {
        try {
            // Lấy file ảnh vận đơn đã được chuẩn bị sẵn từ trước trong thư mục nội bộ
            File sourceFile = new File(getFilesDir(), "ma_van_don.jpg");
            
            if (!sourceFile.exists()) {
                Toast.makeText(this, "Lỗi: Không tìm thấy file ma_van_don.jpg!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            Intent resultIntent = new Intent();
            Uri outputUri = null;

            if (getIntent() != null && getIntent().getExtras() != null) {
                outputUri = (Uri) getIntent().getExtras().get(android.provider.MediaStore.EXTRA_OUTPUT);
            }

            if (outputUri != null) {
                // Nếu app gọi yêu cầu ghi thẳng vào Uri của họ
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

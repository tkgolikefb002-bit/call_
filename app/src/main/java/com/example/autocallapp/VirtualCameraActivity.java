package com.example.autocallapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;

public class VirtualCameraActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tạo một giao diện nút bấm đơn giản cho camera ảo
        Button btnFakeCapture = new Button(this);
        btnFakeCapture.setText("Chụp ảnh mã vận đơn (Ảo)");
        setContentView(btnFakeCapture);

        btnFakeCapture.setOnClickListener(v -> {
            // Trỏ đến file ảnh mã vận đơn đã được chuẩn bị sẵn trong thư mục nội bộ của app
            File preparedImageFile = new File(getFilesDir(), "ma_van_don.jpg");

            if (preparedImageFile.exists()) {
                // Tạo Uri an toàn bằng FileProvider (khớp với provider cấu hình của bạn)
                Uri imageUri = FileProvider.getUriForFile(
                    this, 
                    getPackageName() + ".fileprovider", 
                    preparedImageFile
                );

                Intent resultIntent = new Intent();
                resultIntent.setData(imageUri);
                // Cấp quyền đọc file tạm cho app gọi camera (app BEST Express)
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                setResult(RESULT_OK, resultIntent);
            } else {
                setResult(RESULT_CANCELED);
            }
            // Đóng camera ảo ngay lập tức để trả kết quả về cho app BEST
            finish();
        });
    }
}

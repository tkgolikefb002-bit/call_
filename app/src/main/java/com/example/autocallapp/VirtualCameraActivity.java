package com.example.autocallapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.core.content.FileProvider;
import java.io.File;

public class VirtualCameraActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_camera);

        ImageView imgPreview = findViewById(R.id.imgPreview);
        ImageButton btnShutter = findViewById(R.id.btnShutter);

        // Nạp sẵn file ma_van_don.jpg vào khung ngắm giả lập
        File imageFile = new File(getFilesDir(), "ma_van_don.jpg");
        if (imageFile.exists()) {
            imgPreview.setImageURI(Uri.fromFile(imageFile));
        }

        // Khi bấm nút chụp trên máy ảnh ảo
        btnShutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (imageFile.exists()) {
                        Uri imageUri = FileProvider.getUriForFile(
                                VirtualCameraActivity.this,
                                getPackageName() + ".fileprovider",
                                imageFile
                        );

                        Intent resultIntent = new Intent();
                        resultIntent.setData(imageUri);
                        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        
                        setResult(RESULT_OK, resultIntent);
                    } else {
                        setResult(RESULT_CANCELED);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    setResult(RESULT_CANCELED);
                }
                
                finish(); // Trả kết quả về cho app BEST ngay lập tức
            }
        });
    }
}

package com.example.autocallapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;

public class ImageUtils {

    /**
     * Hàm xử lý Ảnh kiện hàng (Ảnh 2):
     * - Vẽ hình chữ nhật màu trắng che số cũ.
     * - In dãy số mới lên đúng vị trí đó.
     * - Lưu ra thư mục cache tạm để truyền vào app BEST Express.
     */
    public static Uri createModifiedParcelImage(Context context, Bitmap sourceBitmap, String newTrackingNumber) {
        try {
            // Tạo bản sao bitmap có thể chỉnh sửa
            Bitmap editableBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(editableBitmap);
            Paint paint = new Paint();

            // 1. VẼ HÌNH CHỮ NHẬT MÀU TRẮNG ĐỂ CHE SỐ CŨ
            // (Bạn có thể tinh chỉnh lại tọa độ left, top, right, bottom cho khớp với tem thực tế)
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            
            float rectLeft = 280f;
            float rectTop = 1140f;
            float rectRight = 780f;
            float rectBottom = 1240f;
            
            canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, paint);

            // 2. VẼ DÃY SỐ MỚI LÊN VÙNG VỪA CHE (Màu đen, in đậm giống máy in nhiệt)
            paint.setColor(Color.BLACK);
            paint.setTextSize(45f);
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            // Tọa độ đặt chữ mới bên trong vùng màu trắng
            float textX = 295f;
            float textY = 1210f;

            canvas.drawText(newTrackingNumber, textX, textY, paint);

            // 3. LƯU ẢNH ĐÃ SỬA RA THƯ MỤC CACHE CỦA ỨNG DỤNG
            File cacheFile = new File(context.getCacheDir(), "edited_parcel_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            editableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.flush();
            outputStream.close();

            // Trả về Uri của file ảnh tạm
            return Uri.fromFile(cacheFile);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

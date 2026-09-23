package com.example.autocallapp;

import android.net.Uri;
import android.os.Build;
import android.telecom.CallRedirectionService;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class MyCallRedirectionService extends CallRedirectionService {

    // Số điện thoại ảo/tổng đài ảo mà bạn muốn chuyển hướng đến
    private static final String VIRTUAL_PHONE_NUMBER = "09894525506454"; 

    @Override
    onPlaceCall(@NonNull Uri handle, @NonNull PhoneAccountHandle initialPhoneAccount, boolean allowFullScreenIntent) {
        // Kiểm tra xem số đang gọi đi có phải là số ảo hay chưa để tránh lặp vô tận
        String currentNumber = handle.getSchemeSpecificPart();
        
        if (currentNumber.equals(VIRTUAL_PHONE_NUMBER)) {
            // Nếu đã là số ảo thì cho phép gọi luôn
            placeCallUnmodified();
        } else {
            // Ngược lại, âm thầm thay đổi số gọi đi thành số ảo đã liên kết trong ứng dụng
            Uri redirectedUri = Uri.fromParts("tel", VIRTUAL_PHONE_NUMBER, null);
            
            // Ra lệnh cho hệ thống chuyển hướng cuộc gọi sang số ảo này
            redirectCall(redirectedUri, initialPhoneAccount, false);
        }
    }
}

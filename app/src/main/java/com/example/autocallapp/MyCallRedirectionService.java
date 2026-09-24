package com.example.autocallapp;

import android.net.Uri;
import android.os.Build;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class MyCallRedirectionService extends CallRedirectionService {

    // SỐ ẢO MÀ BẠN MUỐN CHUYỂN HƯỚNG TỚI THAY CHO SỐ GỐC
    private static final String VIRTUAL_PHONE_NUMBER = "09012345670";

    @Override
    public void onPlaceCall(@NonNull Uri handle, @NonNull PhoneAccountHandle initialPhoneAccount, boolean allowFullScreenIntent) {
        String currentNumber = handle.getSchemeSpecificPart();
        
        // Nếu số hiện tại đã là số ảo thì cho đi thẳng để tránh lặp vô tận
        if (currentNumber != null && currentNumber.equals(VIRTUAL_PHONE_NUMBER)) {
            placeCallUnmodified();
        } else {
            // Bẻ lái toàn bộ các số gọi khác sang SỐ ẢO
            Uri redirectedUri = Uri.fromParts("tel", VIRTUAL_PHONE_NUMBER, null);
            redirectCall(redirectedUri, initialPhoneAccount, false);
        }
    }
}

package com.example.autocallapp;

import android.net.Uri;
import android.os.Build;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class MyCallRedirectionService extends CallRedirectionService {

    // Số điện thoại mặc định bạn muốn chuyển hướng tới
    private static final String TARGET_PHONE_NUMBER = "09012345670";

    @Override
    public void onPlaceCall(@NonNull Uri handle, @NonNull PhoneAccountHandle initialPhoneAccount, boolean allowFullScreenIntent) {
        String currentNumber = handle.getSchemeSpecificPart();
        
        // Nếu số điện thoại đang gọi chính là số đích thì cho phép đi thẳng (tránh lặp vô tận)
        if (currentNumber != null && currentNumber.equals(TARGET_PHONE_NUMBER)) {
            placeCallUnmodified();
        } else {
            // Ngược lại, ép chuyển hướng toàn bộ các số khác sang số mặc định
            Uri redirectedUri = Uri.fromParts("tel", TARGET_PHONE_NUMBER, null);
            redirectCall(redirectedUri, initialPhoneAccount, false);
        }
    }
}

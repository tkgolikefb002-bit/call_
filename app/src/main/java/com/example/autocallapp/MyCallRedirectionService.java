package com.example.autocallapp;

import android.net.Uri;
import android.os.Build;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class MyCallRedirectionService extends CallRedirectionService {

    // Số giả lập không gọi được để chặn đường truyền vật lý
    private static final String VIRTUAL_PHONE_NUMBER = "10989452550";

    @Override
    public void onPlaceCall(@NonNull Uri handle, @NonNull PhoneAccountHandle initialPhoneAccount, boolean allowFullScreenIntent) {
        String currentNumber = handle.getSchemeSpecificPart();
        
        if (currentNumber != null && currentNumber.equals(VIRTUAL_PHONE_NUMBER)) {
            placeCallUnmodified();
        } else {
            // Ép chuyển hướng cuộc gọi đi sang số ảo không gọi được
            Uri redirectedUri = Uri.fromParts("tel", VIRTUAL_PHONE_NUMBER, null);
            redirectCall(redirectedUri, initialPhoneAccount, false);
        }
    }
}

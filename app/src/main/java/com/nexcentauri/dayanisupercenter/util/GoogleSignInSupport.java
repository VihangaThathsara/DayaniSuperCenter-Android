package com.nexcentauri.dayanisupercenter.util;

import androidx.credentials.GetCredentialRequest;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException;
import androidx.credentials.exceptions.GetCredentialUnsupportedException;
import androidx.credentials.exceptions.NoCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;

public final class GoogleSignInSupport {
    private GoogleSignInSupport() { }

    public static GetCredentialRequest buttonRequest(String webClientId) {
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(webClientId.trim()).build();
        return new GetCredentialRequest.Builder().addCredentialOption(option).build();
    }

    public static String errorMessage(Exception error) {
        if (error instanceof GetCredentialCancellationException)
            return "Google sign-in cancelled. You can try again anytime.";
        if (error instanceof NoCredentialException) return "Add or sign in to a Google account on this phone, then try again.";
        if (error instanceof GetCredentialProviderConfigurationException || error instanceof GetCredentialUnsupportedException)
            return "Google sign-in is unavailable. Update Google Play services and try again.";
        if (error instanceof FirebaseNetworkException) return "Check your connection and try Google sign-in again.";
        if (error instanceof FirebaseAuthException) {
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(((FirebaseAuthException) error).getErrorCode()))
                return "Google sign-in is not enabled for this app. Contact the administrator.";
            if ("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL".equals(((FirebaseAuthException) error).getErrorCode()))
                return "Use your existing sign-in method for this email address.";
        }
        return "Couldn't sign in with Google. Please try again.";
    }
}

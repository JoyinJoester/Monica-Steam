package takagi.ru.monica.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager

/**
 * Match the strong-authenticator requirement used by our BiometricPrompt.
 * Core 1.19's retired FingerprintManagerCompat always reports false. Let BiometricManager
 * check enrollment and readiness instead; its current implementation owns the legacy fallback.
 * The fingerprint hardware gate remains. Android chooses the eligible strong modality;
 * the public biometric API does not offer a fingerprint-only enrollment query.
 */
internal fun fingerprintBiometricStatus(context: Context): Int {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
        return BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }
    return BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
}

package com.example.yemenport.core

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface BiometricAuthAvailability {
    data object Available : BiometricAuthAvailability
    data object NoHardware : BiometricAuthAvailability
    data object NotEnrolled : BiometricAuthAvailability
    data class Unavailable(val reason: String) : BiometricAuthAvailability
}

/**
 * Helper class encapsulating androidx.biometric verification for sovereign officer sessions
 * (startup verification, idle-timeout re-authentication, and sensitive settings toggles).
 */
class BiometricAuthentication(
    private val allowedAuthenticators: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
) {

    fun checkAvailability(context: Context): BiometricAuthAvailability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAuthAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAuthAvailability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAuthAvailability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAuthAvailability.Unavailable("مستشعر البصمة غير متاح حالياً")
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAuthAvailability.Unavailable("يتطلب الجهاز تحديثاً أمنياً لتشغيل البصمة")
            else -> BiometricAuthAvailability.Unavailable("التحقق البيومتري غير متاح على هذا الجهاز")
        }
    }

    fun canAuthenticate(context: Context): Boolean =
        checkAvailability(context) is BiometricAuthAvailability.Available

    fun authenticate(
        activity: FragmentActivity,
        title: String = "تأكيد هوية الضابط بالبصمة",
        subtitle: String = "نظام المنافذ والجوازات الذكي — حماية الجلسة الميدانية",
        description: String? = "تحقق من هويتك للمتابعة والوصول إلى سجلات المسافرين والبلاغات",
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {},
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val availability = checkAvailability(activity)
        if (availability !is BiometricAuthAvailability.Available) {
            val msg = when (availability) {
                BiometricAuthAvailability.NoHardware -> "لا يدعم هذا الجهاز المصادقة البيومترية أو قفل الشاشة"
                BiometricAuthAvailability.NotEnrolled -> "يرجى تفعيل بصمة الإصبع أو رمز قفل الجهاز في إعدادات النظام"
                is BiometricAuthAvailability.Unavailable -> availability.reason
                BiometricAuthAvailability.Available -> ""
            }
            onError(msg)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancelled()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
        if (!description.isNullOrBlank()) {
            builder.setDescription(description)
        }
        prompt.authenticate(builder.build())
    }

    suspend fun authenticateOfficer(
        activity: FragmentActivity,
        title: String = "تأكيد هوية الضابط بالبصمة",
        subtitle: String = "نظام المنافذ والجوازات الذكي — حماية الجلسة الميدانية"
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = {
                if (cont.isActive) cont.resume(Result.success(Unit))
            },
            onError = { msg ->
                if (cont.isActive) cont.resume(Result.failure(SecurityException(msg)))
            },
            onCancelled = {
                if (cont.isActive) cont.resume(Result.failure(SecurityException("تم إلغاء التحقق البيومتري")))
            }
        )
    }

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS: Long = 3 * 60 * 1000L // 3 minutes field idle lock
    }
}

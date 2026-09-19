// SPDX-FileCopyrightText: 2026 amurcanov (original manual-captcha design), 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// Ported from amurcanov/csqtt v2.1.9's ManlCaptchaWebViewManager.kt: the visible-WebView
// fallback used when the auto solver (CsqttCaptchaWebViewManager) hits a slider or times
// out. Shows a heads-up notification if the app isn't in the foreground.

package io.nekohasekai.sfa.zgrnk.csqtt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

object CsqttManualCaptchaWebViewManager {
    private const val CAPTCHA_TIMEOUT_MS = CsqttConstants.CAPTCHA_MANUAL_TIMEOUT_MS
    private const val NOTIFICATION_ID = CsqttConstants.CAPTCHA_NOTIFICATION_ID
    private const val CHANNEL_ID = CsqttConstants.CAPTCHA_NOTIFICATION_CHANNEL_ID

    val pendingResult = AtomicReference<CompletableDeferred<Result<String>>?>(null)
    private val captchaMutex = Mutex()
    private val activeActivityRef = AtomicReference<WeakReference<CsqttManualCaptchaActivity>?>(null)

    var activeActivity: CsqttManualCaptchaActivity?
        get() = activeActivityRef.get()?.get()
        set(value) { activeActivityRef.set(value?.let(::WeakReference)) }

    fun cancelCaptcha() {
        pendingResult.get()?.completeExceptionally(CancellationException("Cancelled by system"))
    }

    private fun showCaptchaNotification(context: Context, redirectUri: String) {
        if (AppLifecycleObserver.isForeground.value) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "CSQTT: капча", NotificationManager.IMPORTANCE_HIGH),
        )
        val openIntent = Intent(context, CsqttManualCaptchaActivity::class.java).apply {
            putExtra("redirectUri", redirectUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("CSQTT: требуется капча")
            .setContentText("ВК запросил проверку безопасности. Нажмите для решения.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun clearCaptchaNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    suspend fun solveCaptchaAsync(context: Context, redirectUri: String, sessionToken: String): String {
        if (sessionToken.isBlank()) throw IllegalArgumentException("Пустой session token captcha")
        return captchaMutex.withLock {
            val deferred = CompletableDeferred<Result<String>>()
            pendingResult.getAndSet(deferred)?.cancel()

            showCaptchaNotification(context, redirectUri)

            val intent = Intent(context, CsqttManualCaptchaActivity::class.java).apply {
                putExtra("redirectUri", redirectUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (AppLifecycleObserver.isForeground.value) context.startActivity(intent)

            try {
                withTimeout(CAPTCHA_TIMEOUT_MS) { deferred.await().getOrThrow() }
            } finally {
                pendingResult.set(null)
                clearCaptchaNotification(context)
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    runCatching { activeActivity?.finish() }
                    activeActivity = null
                }
            }
        }
    }

    fun notifyResult(result: Result<String>) {
        val deferred = pendingResult.getAndSet(null) ?: return
        if (!deferred.isCompleted) deferred.complete(result)
    }
}

// SPDX-FileCopyrightText: 2026 amurcanov (original TUN handoff design), 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// Adapted from amurcanov/csqtt v2.1.9's TunVpnService.kt: establishes the Android VPN
// interface and hands its fd to the CSQTT Rust client over an abstract LocalSocket
// (CsqttConstants.TUN_UDS_NAME) — the client owns the TUN, this service only owns the fd.
// Unlike upstream, this also carries the tunnel's persistent foreground notification
// (upstream splits that into a separate TunnelService.kt this fork doesn't port).

package io.nekohasekai.sfa.zgrnk.csqtt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import io.nekohasekai.sfa.compose.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CsqttVpnService : VpnService() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val vpnMutex = Mutex()
    private var vpnInterface: ParcelFileDescriptor? = null
    private var sendJob: Job? = null
    @Volatile
    private var stopRequested = false

    companion object {
        private const val TAG = "CsqttVpnService"
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(CsqttConstants.NOTIFICATION_ID, buildNotification("Подключение..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            "START" -> {
                stopRequested = false
                val clientIp = intent.getStringExtra("client_ip") ?: "10.66.66.2"
                val dns = intent.getStringExtra("dns") ?: "1.1.1.1"
                serviceScope.launch { startVpn(clientIp, dns) }
            }
            "STOP" -> {
                stopRequested = true
                serviceScope.launch {
                    vpnMutex.withLock { stopVpn() }
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startVpn(clientIp: String, dns: String) {
        vpnMutex.withLock {
            try {
                if (VpnService.prepare(this) != null) {
                    failVpn("разрешение Android VPN не выдано")
                    return
                }

                val builder = Builder()
                    .setSession("CSQTT")
                    .setMtu(CsqttConstants.DEFAULT_MTU)
                    .addAddress(clientIp, 32)
                    .addRoute("0.0.0.0", 0)

                var validDnsServers = 0
                dns.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { dnsServer ->
                    try {
                        builder.addDnsServer(dnsServer)
                        validDnsServers++
                    } catch (_: Exception) {
                        Log.w(TAG, "Invalid DNS server: $dnsServer")
                    }
                }
                if (validDnsServers == 0) {
                    failVpn("сервер передал некорректный DNS: $dns")
                    return
                }

                try {
                    builder.addDisallowedApplication(applicationContext.packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to exclude own package from VPN", e)
                    failVpn("Android не разрешил исключить приложение из собственного VPN")
                    return
                }
                for (pkg in listOf("com.vkontakte.android", "com.vk.calls")) {
                    if (isPackageInstalled(pkg)) {
                        runCatching { builder.addDisallowedApplication(pkg) }
                    }
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    failVpn("Android не создал VPN-интерфейс; проверьте разрешение VPN и Always-on VPN")
                    return
                }

                val previous = vpnInterface
                vpnInterface = pfd
                sendJob?.cancel()
                if (previous !== pfd) runCatching { previous?.close() }
                Log.d(TAG, "VPN interface established: IP=$clientIp DNS=$dns fd=${pfd.fd}")
                updateNotification("Активен · $clientIp")

                sendTunFd(pfd)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN: ${e.message}", e)
                failVpn(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun sendTunFd(pfd: ParcelFileDescriptor) {
        sendJob?.cancel()
        sendJob = serviceScope.launch {
            var success = false
            for (i in 1..20) {
                val socket = LocalSocket()
                try {
                    socket.connect(LocalSocketAddress(CsqttConstants.TUN_UDS_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                    socket.setFileDescriptorsForSend(arrayOf(pfd.fileDescriptor))
                    socket.outputStream.write(1)
                    socket.outputStream.flush()
                    socket.soTimeout = 3_000
                    if (socket.inputStream.read() != 1) {
                        throw IllegalStateException("Rust-клиент не подтвердил TUN-интерфейс")
                    }
                    Log.d(TAG, "Sent TUN fd to Rust client successfully")
                    success = true
                    CsqttProcess.onVpnInterfaceReady()
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to connect to Rust client UDS, retrying: ${e.message}")
                    delay(500)
                } finally {
                    runCatching { socket.close() }
                }
            }
            if (!success) {
                failVpn("не удалось передать TUN fd Rust-клиенту после повторных попыток")
            }
        }
    }

    private fun failVpn(message: String) {
        stopRequested = true
        stopVpn()
        CsqttProcess.onVpnTerminalFailure(message)
    }

    private fun stopVpn() {
        sendJob?.cancel()
        sendJob = null
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        CsqttProcess.onVpnInterfaceStopped()
        Log.d(TAG, "VPN interface closed")
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    private fun buildNotification(text: String): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CsqttConstants.NOTIFICATION_CHANNEL_ID,
            "CSQTT",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CsqttConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("CSQTT")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(CsqttConstants.NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        val unexpected = !stopRequested
        serviceJob.cancel()
        stopVpn()
        if (unexpected) {
            CsqttProcess.onVpnTerminalFailure("VPN-сервис был остановлен Android")
        }
        super.onDestroy()
    }

    override fun onRevoke() {
        stopRequested = true
        serviceJob.cancel()
        stopVpn()
        CsqttProcess.onVpnTerminalFailure("разрешение Android VPN было отозвано")
        stopSelf()
    }
}

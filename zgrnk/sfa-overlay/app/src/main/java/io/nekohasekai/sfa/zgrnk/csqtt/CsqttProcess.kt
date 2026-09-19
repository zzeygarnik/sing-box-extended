// SPDX-FileCopyrightText: 2026 amurcanov (original protocol/process design), 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// Minimal port of amurcanov/csqtt v2.1.9's TunnelManager.kt: argv build, ProcessBuilder,
// stdout event reader, stdin command writer, clean stop (STOP, wait, destroy), and a plain
// restart-on-crash policy. Deliberately does not port upstream's chaos-recovery machinery
// (worker-zero watchdog, Wi-Fi auto-pause, panel-restart scheduling, VK-reachability probes,
// auto_js hash rotation) — out of scope for this fork's v1, see PROMPT_sonnet_csqtt_step4.md.

package io.nekohasekai.sfa.zgrnk.csqtt

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import io.nekohasekai.sfa.bg.BoxService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.lang.ref.WeakReference

object CsqttProcess {

    enum class HashStatus { READY, UNAVAILABLE }

    data class State(
        val running: Boolean = false,
        val starting: Boolean = false,
        val activeWorkers: Int = 0,
        val statsText: String = "",
        val hashStatus: Map<String, HashStatus> = emptyMap(),
        val logTail: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startStopMutex = Mutex()

    private var process: Process? = null
    private var generation = 0L
    @Volatile
    private var desiredRunning = false
    private var crashStreak = 0
    private var appContextRef: WeakReference<Context>? = null
    private var currentParams: CsqttParams? = null

    private fun activeContext(): Context? = appContextRef?.get()

    fun start(context: Context, params: CsqttParams) {
        val appContext = context.applicationContext
        appContextRef = WeakReference(appContext)
        currentParams = params
        desiredRunning = true
        crashStreak = 0
        scope.launch {
            startStopMutex.withLock {
                if (process != null) return@withLock
                BoxService.stop()
                delay(300L)
                launchProcessLocked(appContext, params)
            }
        }
    }

    fun stop() {
        desiredRunning = false
        scope.launch {
            startStopMutex.withLock { stopLocked() }
        }
    }

    private fun stopLocked() {
        generation++
        val p = process
        process = null
        activeContext()?.let { ctx ->
            runCatching {
                ctx.startService(Intent(ctx, CsqttVpnService::class.java).apply { action = "STOP" })
            }
        }
        if (p != null) terminateProcess(p)
        CsqttCaptchaWebViewManager.onTunnelStop()
        _state.value = State()
    }

    private fun terminateProcess(p: Process) {
        runCatching {
            p.outputStream.write("STOP\n".toByteArray(Charsets.UTF_8))
            p.outputStream.flush()
        }
        runCatching { CsqttProcessCompat.waitFor(p, 3_000) }
        if (CsqttProcessCompat.isAlive(p)) {
            runCatching { p.destroy() }
            runCatching { CsqttProcessCompat.waitFor(p, 500) }
        }
        if (CsqttProcessCompat.isAlive(p)) runCatching { CsqttProcessCompat.destroyForcibly(p) }
    }

    private fun launchProcessLocked(context: Context, params: CsqttParams) {
        if (params.peer.isBlank()) { failStart("Адрес сервера (peer) не указан"); return }
        if (params.connectionPassword.isBlank()) { failStart("Пароль подключения не указан"); return }
        if (params.vkHashes.isEmpty()) { failStart("Хотя бы один VK-хеш обязателен"); return }

        val binaryPath = context.applicationInfo.nativeLibraryDir + "/" + CsqttConstants.BINARY_NAME
        if (!File(binaryPath).exists()) { failStart("Бинарный файл CSQTT не найден в APK"); return }

        val totalWorkers = CsqttWorkerCountPolicy.normalizeForHashValues(params.workersPerHash, params.vkHashes)
        val cmd = mutableListOf(
            binaryPath,
            "-peer", params.peer,
            "-n", totalWorkers.toString(),
            "-listen", "${CsqttConstants.LOCAL_LISTEN_HOST}:0",
            "-tun-uds", CsqttConstants.TUN_UDS_NAME,
            "-vk", params.vkHashes.joinToString(","),
            "-vk-hash-mode", CsqttConstants.VK_HASH_MODE_MANUAL,
        )
        if (params.fingerprint.isNotEmpty()) {
            cmd.add("-fingerprint"); cmd.add(params.fingerprint)
        }
        if (params.clientIds.isNotEmpty()) {
            cmd.add("-client-ids"); cmd.add(params.clientIds)
        }
        cmd.add("-obfs"); cmd.add(params.obfsMode)
        cmd.add("-turn-transport"); cmd.add(params.turnTransport)
        cmd.add("-vk-auth-mode"); cmd.add("vkcalls")
        cmd.add("-device-id"); cmd.add(readDeviceId(context))
        cmd.add("-password"); cmd.add(params.connectionPassword)
        cmd.add("-captcha-mode"); cmd.add(CsqttConstants.CAPTCHA_MODE_AUTO)

        val pb = ProcessBuilder(cmd)
        pb.directory(context.filesDir)
        pb.redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
        pb.environment()[CsqttConstants.PROCESS_ENV_EVENTS] = "1"
        pb.environment()["RAYON_NUM_THREADS"] = "2"

        val startedProcess = try {
            pb.start()
        } catch (e: Exception) {
            failStart("Не удалось запустить процесс: ${e.message}")
            return
        }

        process = startedProcess
        val myGeneration = ++generation
        CsqttCaptchaWebViewManager.onTunnelStart(context)
        _state.value = State(
            running = true,
            starting = true,
            statsText = "Ожидание данных...",
            hashStatus = params.vkHashes.associateWith { HashStatus.READY },
        )
        startLogReader(context, startedProcess, myGeneration, params)
    }

    private fun failStart(message: String) {
        desiredRunning = false
        pushLog("[ОШИБКА] $message")
        _state.value = _state.value.copy(running = false, starting = false)
    }

    private fun startLogReader(context: Context, targetProcess: Process, myGeneration: Long, params: CsqttParams) {
        scope.launch {
            val reader = targetProcess.inputStream.bufferedReader()
            try {
                reader.forEachLine { line ->
                    if (generation != myGeneration) return@forEachLine
                    val trimmed = line.trim()
                    val event = CsqttEventParser.parse(trimmed)
                    if (event != null) {
                        handleEvent(context, event, myGeneration)
                    } else if (trimmed.isNotEmpty()) {
                        // Raw stdout/stderr the client didn't emit as a structured
                        // event — this is the only place the real crash reason
                        // (bad password, panic, missing arg, ...) ever shows up.
                        pushLog("[RAW] $trimmed")
                    }
                }
            } catch (_: Exception) {
                // reader unwinds on process death or stop(); handled below
            } finally {
                runCatching { reader.close() }
                startStopMutex.withLock {
                    if (generation != myGeneration) return@withLock
                    process = null
                    if (desiredRunning) {
                        crashStreak++
                        if (crashStreak > CsqttConstants.CRASH_RESTART_MAX_STREAK) {
                            failStart("Rust-клиент падает подряд — перезапуск остановлен, проверьте пароль/хеши/сеть")
                        } else {
                            pushLog("Rust-клиент завершился, перезапуск через 2с...")
                            _state.value = _state.value.copy(running = false, starting = true)
                            delay(CsqttConstants.CRASH_RESTART_DELAY_MS)
                            if (desiredRunning && generation == myGeneration) {
                                launchProcessLocked(context, currentParams ?: params)
                            }
                        }
                    } else {
                        _state.value = State()
                    }
                }
            }
        }
    }

    private fun handleEvent(context: Context, event: CsqttEventParser.Event, myGeneration: Long) {
        when (event) {
            is CsqttEventParser.Event.Process -> Unit
            is CsqttEventParser.Event.Ready -> {
                _state.value = _state.value.copy(starting = false)
            }
            is CsqttEventParser.Event.Stats -> {
                crashStreak = 0
                val totalMB = (event.bytesUp + event.bytesDown) / (1024.0 * 1024.0)
                _state.value = _state.value.copy(
                    starting = false,
                    activeWorkers = event.active,
                    statsText = "Активных: ${event.active} | Трафик: %.2f МБ".format(totalMB),
                )
            }
            is CsqttEventParser.Event.ActiveZero -> {
                _state.value = _state.value.copy(activeWorkers = 0)
            }
            is CsqttEventParser.Event.CallUnavailable -> {
                pushLog("[VK] Хеш …${event.hash.takeLast(6)} недоступен (код ${event.code})")
                _state.value = _state.value.copy(
                    hashStatus = _state.value.hashStatus + (event.hash to HashStatus.UNAVAILABLE),
                )
            }
            is CsqttEventParser.Event.Config -> {
                val configStr = event.config.trim()
                if (configStr.startsWith("TUNCONF:")) {
                    val parts = configStr.removePrefix("TUNCONF:").split(":", limit = 3)
                    val clientIp = parts.getOrNull(0) ?: "10.66.66.2"
                    val dns = parts.getOrNull(1) ?: "1.1.1.1"
                    runCatching {
                        androidx.core.content.ContextCompat.startForegroundService(
                            context,
                            Intent(context, CsqttVpnService::class.java).apply {
                                action = "START"
                                putExtra("client_ip", clientIp)
                                putExtra("dns", dns)
                            },
                        )
                    }.onFailure { e ->
                        pushLog("[VPN] Не удалось запустить VPN-сервис: ${e.message}")
                    }
                }
            }
            is CsqttEventParser.Event.Error -> {
                if (event.fatal) {
                    pushLog("[СТОП] ${event.message}")
                    stop()
                } else {
                    pushLog("[ОШИБКА] ${event.message}")
                }
            }
            is CsqttEventParser.Event.CaptchaRequest -> {
                scope.launch { solveCaptcha(context, event, myGeneration) }
            }
        }
    }

    private suspend fun solveCaptcha(context: Context, event: CsqttEventParser.Event.CaptchaRequest, myGeneration: Long) {
        if (generation != myGeneration) return
        val method = currentParams?.captchaSolveMethod ?: CsqttConstants.CAPTCHA_MODE_AUTO
        val requestMode = event.mode.lowercase()
        val token = try {
            when {
                requestMode == CsqttConstants.CAPTCHA_MODE_MANUAL ->
                    CsqttManualCaptchaWebViewManager.solveCaptchaAsync(context, event.redirectUri, event.sessionToken)
                requestMode == CsqttConstants.CAPTCHA_MODE_AUTO || method == CsqttConstants.CAPTCHA_MODE_AUTO -> {
                    try {
                        pushLog("[КАПЧА] Авто WebView...")
                        CsqttCaptchaWebViewManager.solveCaptchaAsync(event.redirectUri, event.sessionToken) { step ->
                            pushLog("[КАПЧА] $step")
                        }
                    } catch (e: IllegalStateException) {
                        pushLog("[КАПЧА] Авто не удалось (${e.message}), открываю ручной WebView")
                        CsqttManualCaptchaWebViewManager.solveCaptchaAsync(context, event.redirectUri, event.sessionToken)
                    } catch (e: TimeoutCancellationException) {
                        pushLog("[КАПЧА] Авто таймаут, открываю ручной WebView")
                        CsqttManualCaptchaWebViewManager.solveCaptchaAsync(context, event.redirectUri, event.sessionToken)
                    }
                }
                else -> CsqttManualCaptchaWebViewManager.solveCaptchaAsync(context, event.redirectUri, event.sessionToken)
            }
        } catch (e: Exception) {
            writeCommand("CAPTCHA_RESULT|error:${e.message ?: "unknown"}")
            return
        }
        if (generation == myGeneration) writeCommand("CAPTCHA_RESULT|$token")
    }

    private fun writeCommand(command: String) {
        val p = process ?: return
        runCatching {
            p.outputStream.write("$command\n".toByteArray(Charsets.UTF_8))
            p.outputStream.flush()
        }
    }

    fun onVpnInterfaceReady() {
        pushLog("[VPN] Туннель поднят ✓")
        _state.value = _state.value.copy(starting = false)
    }

    fun onVpnInterfaceStopped() {
        // No-op: the VPN service's own 20-retry fd handoff loop and the process
        // read-loop's crash-restart already cover reconnection in v1.
    }

    fun onVpnTerminalFailure(message: String) {
        pushLog("[VPN ОШИБКА] $message")
        stop()
    }

    private fun pushLog(line: String) {
        _state.value = _state.value.copy(logTail = (_state.value.logTail + line).takeLast(200))
    }

    @SuppressLint("HardwareIds")
    private fun readDeviceId(context: Context): String =
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: "unknown"
}

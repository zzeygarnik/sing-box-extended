// SPDX-FileCopyrightText: 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// java.lang.Process#waitFor(long, TimeUnit)/isAlive/destroyForcibly are all API 26+.
// This app's minSdk is 24 (SFA's own default), so these need runtime-gated fallbacks
// rather than being called directly.

package io.nekohasekai.sfa.zgrnk.csqtt

import android.os.Build

internal object CsqttProcessCompat {
    fun waitFor(process: Process, timeoutMs: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive(process)) return true
            Thread.sleep(50)
        }
        return !isAlive(process)
    }

    fun isAlive(process: Process): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return process.isAlive
        return try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
    }

    fun destroyForcibly(process: Process) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.destroyForcibly()
        } else {
            process.destroy()
        }
    }
}

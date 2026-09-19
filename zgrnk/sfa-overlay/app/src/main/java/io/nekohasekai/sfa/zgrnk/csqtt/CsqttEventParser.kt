// SPDX-FileCopyrightText: 2026 amurcanov (original protocol), 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// Ported near-verbatim from amurcanov/csqtt v2.1.9's TunnelEventParser.kt — the stdout
// event wire format (`__CSQTT_EVENT__|TYPE|{json}`) is the Rust client's own protocol,
// unrelated to sing-box, so this parses the same way regardless of host app.

package io.nekohasekai.sfa.zgrnk.csqtt

import org.json.JSONObject

object CsqttEventParser {
    private const val PREFIX = CsqttConstants.EVENT_PREFIX

    sealed class Event {
        data class Process(val pid: Int) : Event()
        data class Ready(val worker: Int) : Event()
        data class ActiveZero(val message: String = "") : Event()
        data class CallUnavailable(val hash: String, val code: Int) : Event()
        data class Config(val config: String) : Event()
        data class Stats(val active: Int, val bytesUp: Long, val bytesDown: Long) : Event()
        data class Error(val code: String, val message: String, val fatal: Boolean) : Event()
        data class CaptchaRequest(val mode: String, val redirectUri: String, val sessionToken: String) : Event()

        companion object
    }

    fun parse(line: String): Event? {
        if (!line.startsWith(PREFIX)) return null
        val withoutPrefix = line.substring(PREFIX.length)
        val typeEnd = withoutPrefix.indexOf('|')
        if (typeEnd == -1) return null

        val type = withoutPrefix.substring(0, typeEnd)
        val payload = try {
            JSONObject(withoutPrefix.substring(typeEnd + 1))
        } catch (_: Exception) {
            return null
        }

        return when (type) {
            "PROCESS" -> payload.optInt("pid", 0).takeIf { it > 0 }?.let { Event.Process(it) }
            "READY" -> Event.Ready(payload.optInt("worker", 0).coerceIn(0, CsqttConstants.MAX_WORKERS))
            "ACTIVE_ZERO" -> Event.ActiveZero(payload.optString("message", ""))
            "CALL_UNAVAILABLE" -> {
                val hash = payload.optString("hash", "").trim()
                if (hash.isBlank()) null else Event.CallUnavailable(
                    hash = hash,
                    code = payload.optInt("code", 951).coerceAtLeast(0),
                )
            }
            "CONFIG" -> Event.Config(payload.optString("config", ""))
            "STATS" -> Event.Stats(
                active = payload.optInt("active", 0).coerceAtLeast(0),
                bytesUp = payload.optLong("bytes_up", 0L).coerceAtLeast(0L),
                bytesDown = payload.optLong("bytes_down", 0L).coerceAtLeast(0L),
            )
            "ERROR" -> Event.Error(
                code = payload.optString("code", ""),
                message = payload.optString("message", ""),
                fatal = payload.optBoolean("fatal", false),
            )
            "CAPTCHA_REQUEST" -> Event.CaptchaRequest(
                mode = payload.optString("mode", "auto"),
                redirectUri = payload.optString("redirect_uri", ""),
                sessionToken = payload.optString("session_token", ""),
            )
            else -> null
        }
    }
}

// SPDX-FileCopyrightText: 2026 amurcanov (original policy), 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package io.nekohasekai.sfa.zgrnk.csqtt

import java.net.URI

object CsqttCaptchaUriPolicy {
    private val allowedDomains = setOf("vk.com", "vk.ru", "ok.ru", "okcdn.ru")

    fun isAllowed(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return allowedDomains.any { host == it || host.endsWith(".$it") }
    }
}

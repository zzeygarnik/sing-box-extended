// SPDX-FileCopyrightText: 2026 amurcanov (original protocol/client), 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// Minimal constants for the ZGRNK fork's CSQTT tunnel mode. Ported and trimmed from
// amurcanov/csqtt v2.1.9's Constants.kt — only the fields this overlay actually uses.

package io.nekohasekai.sfa.zgrnk.csqtt

object CsqttConstants {
    const val EVENT_PREFIX = "__CSQTT_EVENT__|"
    const val BINARY_NAME = "libcsqtt.so"
    const val PROCESS_ENV_EVENTS = "CSQTT_EVENTS"
    const val LOCAL_LISTEN_HOST = "127.0.0.1"
    const val DEFAULT_SERVER_PEER_PORT = 46000

    const val DEFAULT_FINGERPRINT = "firefox"
    const val DEFAULT_CLIENT_IDS = "8202606,6287487"
    const val DEFAULT_OBFS_MODE = "video"
    const val DEFAULT_TURN_TRANSPORT = "udp"
    const val VK_HASH_MODE_MANUAL = "manual"
    const val CAPTCHA_MODE_AUTO = "auto"
    const val CAPTCHA_MODE_MANUAL = "manual"

    const val WORKERS_PER_GROUP = 9
    const val GROUPS_PER_VK_HASH = 3
    const val MAX_VK_HASHES = 6
    const val DEFAULT_MAX_WORKERS = 72
    const val MAX_WORKERS = 126

    const val DEFAULT_MTU = 1300

    const val CAPTCHA_AUTO_TIMEOUT_MS = 10_000L
    const val CAPTCHA_MANUAL_TIMEOUT_MS = 60_000L
    const val CAPTCHA_WV_CREATE_TIMEOUT_MS = 3_000L

    const val CRASH_RESTART_DELAY_MS = 2_000L
    const val CRASH_RESTART_MAX_STREAK = 5

    const val TUN_UDS_NAME = "csqtt_tun_uds"

    const val NOTIFICATION_CHANNEL_ID = "csqtt_tunnel"
    const val NOTIFICATION_ID = 46000

    const val CAPTCHA_NOTIFICATION_CHANNEL_ID = "csqtt_captcha"
    const val CAPTCHA_NOTIFICATION_ID = 46001

    const val ERROR_SLIDER_DETECTED = "slider_detected"
    val AUTO_VIEWPORT_WIDTHS = intArrayOf(356, 358, 360, 362, 364, 366, 368)
    val AUTO_VIEWPORT_HEIGHTS = intArrayOf(376, 378, 380, 382, 384, 386, 388)

    object SecureStore {
        const val KEY_ALIAS = "csqtt.zgrnk.settings.secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val VERSION_PREFIX = "v1:"
    }
}

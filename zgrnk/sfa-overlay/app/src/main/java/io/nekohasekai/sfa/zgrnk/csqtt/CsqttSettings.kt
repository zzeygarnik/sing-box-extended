// SPDX-FileCopyrightText: 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// Settings storage for the CSQTT tunnel mode. Plain SharedPreferences (no new Gradle
// dependency) for non-secret fields; connection password and VK call hashes go through
// CsqttSecureStore (Android Keystore AES-GCM) before hitting disk.

package io.nekohasekai.sfa.zgrnk.csqtt

import android.content.Context

data class CsqttParams(
    val peer: String,
    val connectionPassword: String,
    val vkHashes: List<String>,
    val fingerprint: String,
    val clientIds: String,
    val obfsMode: String,
    val turnTransport: String,
    val workersPerHash: Int,
    val captchaSolveMethod: String,
)

class CsqttSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("csqtt_settings", Context.MODE_PRIVATE)
    private val secureStore = CsqttSecureStore(appContext)

    var peer: String
        get() = prefs.getString(KEY_PEER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PEER, value).apply()

    var connectionPassword: String
        get() = secureStore.decrypt(prefs.getString(KEY_PASSWORD, null)) ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, secureStore.encrypt(value)).apply()

    /** Up to [CsqttConstants.MAX_VK_HASHES] call-link hashes, one slot can die without killing the others. */
    var vkHashes: List<String>
        get() {
            val raw = secureStore.decrypt(prefs.getString(KEY_HASHES, null)) ?: return emptyList()
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(CsqttConstants.MAX_VK_HASHES)
        }
        set(value) {
            val joined = value.map { it.trim() }.filter { it.isNotEmpty() }.take(CsqttConstants.MAX_VK_HASHES).joinToString(",")
            prefs.edit().putString(KEY_HASHES, secureStore.encrypt(joined)).apply()
        }

    var fingerprint: String
        get() = prefs.getString(KEY_FINGERPRINT, CsqttConstants.DEFAULT_FINGERPRINT) ?: CsqttConstants.DEFAULT_FINGERPRINT
        set(value) = prefs.edit().putString(KEY_FINGERPRINT, value).apply()

    var clientIds: String
        get() = prefs.getString(KEY_CLIENT_IDS, CsqttConstants.DEFAULT_CLIENT_IDS) ?: CsqttConstants.DEFAULT_CLIENT_IDS
        set(value) = prefs.edit().putString(KEY_CLIENT_IDS, value).apply()

    var obfsMode: String
        get() = prefs.getString(KEY_OBFS, CsqttConstants.DEFAULT_OBFS_MODE) ?: CsqttConstants.DEFAULT_OBFS_MODE
        set(value) = prefs.edit().putString(KEY_OBFS, value).apply()

    var turnTransport: String
        get() = prefs.getString(KEY_TURN_TRANSPORT, CsqttConstants.DEFAULT_TURN_TRANSPORT) ?: CsqttConstants.DEFAULT_TURN_TRANSPORT
        set(value) = prefs.edit().putString(KEY_TURN_TRANSPORT, value).apply()

    var workersPerHash: Int
        get() = prefs.getInt(KEY_WORKERS, CsqttConstants.WORKERS_PER_GROUP * CsqttConstants.GROUPS_PER_VK_HASH)
        set(value) = prefs.edit().putInt(KEY_WORKERS, value).apply()

    var captchaSolveMethod: String
        get() = prefs.getString(KEY_CAPTCHA_METHOD, CsqttConstants.CAPTCHA_MODE_AUTO) ?: CsqttConstants.CAPTCHA_MODE_AUTO
        set(value) = prefs.edit().putString(KEY_CAPTCHA_METHOD, value).apply()

    fun toParams(): CsqttParams = CsqttParams(
        peer = peer,
        connectionPassword = connectionPassword,
        vkHashes = vkHashes,
        fingerprint = fingerprint,
        clientIds = clientIds,
        obfsMode = obfsMode,
        turnTransport = turnTransport,
        workersPerHash = workersPerHash,
        captchaSolveMethod = captchaSolveMethod,
    )

    companion object {
        private const val KEY_PEER = "peer"
        private const val KEY_PASSWORD = "password_enc"
        private const val KEY_HASHES = "vk_hashes_enc"
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_CLIENT_IDS = "client_ids"
        private const val KEY_OBFS = "obfs_mode"
        private const val KEY_TURN_TRANSPORT = "turn_transport"
        private const val KEY_WORKERS = "workers_per_hash"
        private const val KEY_CAPTCHA_METHOD = "captcha_solve_method"
    }
}

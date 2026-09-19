// SPDX-FileCopyrightText: 2026 amurcanov (original policy), 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// Ported from amurcanov/csqtt v2.1.9's WorkerCountPolicy.kt, trimmed to the manual-hash
// path this overlay uses (no auto_js hash mode in v1).

package io.nekohasekai.sfa.zgrnk.csqtt

internal object CsqttWorkerCountPolicy {
    private fun normalize(requested: Int, maximum: Int): Int {
        val group = CsqttConstants.WORKERS_PER_GROUP
        val normalizedMaximum = (maximum.coerceAtLeast(group) / group) * group
        return (requested.coerceIn(group, normalizedMaximum) / group) * group
    }

    private fun maximumForHashes(hashCount: Int): Int {
        val requested = hashCount.coerceIn(1, CsqttConstants.MAX_VK_HASHES) *
            CsqttConstants.GROUPS_PER_VK_HASH * CsqttConstants.WORKERS_PER_GROUP
        return normalize(requested, CsqttConstants.MAX_WORKERS)
    }

    fun normalizeForHashValues(requested: Int, hashes: Iterable<String>): Int {
        val hashCount = hashes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(CsqttConstants.MAX_VK_HASHES)
            .count()
        return normalize(requested, maximumForHashes(hashCount))
    }
}

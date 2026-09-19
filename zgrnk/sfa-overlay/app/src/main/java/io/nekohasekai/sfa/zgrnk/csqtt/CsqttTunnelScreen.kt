// SPDX-FileCopyrightText: 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// CSQTT tunnel mode screen: connection fields, Connect/Disconnect, live status (worker
// count, per-hash READY/UNAVAILABLE, traffic stats) and a scrolling log tail. Reachable
// from Tools ("tools/csqtt"). Uses SFA's own MaterialTheme (Theme.kt) — no new design
// tokens, no new colors; this screen is plain Material3 components on the app's existing
// scheme, matching the rest of the Tools section (ListItem rows, OutlinedTextField, plain
// vertical Column layout) rather than porting amurcanov/csqtt's own bespoke design system.

package io.nekohasekai.sfa.zgrnk.csqtt

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsqttTunnelScreen(navController: NavHostController) {
    val context = LocalContext.current
    val settings = remember { CsqttSettings(context) }
    val scope = rememberCoroutineScope()
    val state by CsqttProcess.state.collectAsState()

    var peer by remember { mutableStateOf(settings.peer) }
    var password by remember { mutableStateOf(settings.connectionPassword) }
    var hashesText by remember { mutableStateOf(settings.vkHashes.joinToString("\n")) }
    var fingerprint by remember { mutableStateOf(settings.fingerprint) }
    var obfsMode by remember { mutableStateOf(settings.obfsMode) }
    var turnTransport by remember { mutableStateOf(settings.turnTransport) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch { startTunnel(context, settings, peer, password, hashesText, fingerprint, obfsMode, turnTransport) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CSQTT") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("TURN/RTP транспорт через VK-звонки. См. README проекта.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = peer,
                onValueChange = { peer = it },
                label = { Text("Сервер (host:port)") },
                singleLine = true,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль подключения") },
                singleLine = true,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = hashesText,
                onValueChange = { hashesText = it },
                label = { Text("VK-хеши звонков (до 6, по одному на строку)") },
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            OutlinedTextField(
                value = fingerprint,
                onValueChange = { fingerprint = it },
                label = { Text("Fingerprint") },
                singleLine = true,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = obfsMode,
                    onValueChange = { obfsMode = it },
                    label = { Text("Obfs") },
                    singleLine = true,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                OutlinedTextField(
                    value = turnTransport,
                    onValueChange = { turnTransport = it },
                    label = { Text("TURN transport") },
                    singleLine = true,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }

            Button(
                onClick = {
                    if (state.running) {
                        CsqttProcess.stop()
                    } else {
                        val prepareIntent = VpnService.prepare(context)
                        if (prepareIntent != null) {
                            vpnPermissionLauncher.launch(prepareIntent)
                        } else {
                            scope.launch { startTunnel(context, settings, peer, password, hashesText, fingerprint, obfsMode, turnTransport) }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.running) "Отключить" else "Подключить")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when {
                            state.starting -> "Подключение..."
                            state.running -> "Активно"
                            else -> "Остановлено"
                        },
                    )
                    if (state.statsText.isNotEmpty()) Text(state.statsText)
                    if (state.hashStatus.isNotEmpty()) {
                        Text("Хеши:")
                        state.hashStatus.forEach { (hash, status) ->
                            val label = if (status == CsqttProcess.HashStatus.READY) "READY" else "UNAVAILABLE"
                            Text("  ...${hash.takeLast(6)} — $label")
                        }
                    }
                }
            }

            Text("Лог:", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                items(state.logTail.asReversed()) { line ->
                    Text(line, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun startTunnel(
    context: android.content.Context,
    settings: CsqttSettings,
    peer: String,
    password: String,
    hashesText: String,
    fingerprint: String,
    obfsMode: String,
    turnTransport: String,
) {
    settings.peer = peer
    settings.connectionPassword = password
    settings.vkHashes = hashesText.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
    settings.fingerprint = fingerprint
    settings.obfsMode = obfsMode
    settings.turnTransport = turnTransport
    CsqttProcess.start(context, settings.toParams())
}

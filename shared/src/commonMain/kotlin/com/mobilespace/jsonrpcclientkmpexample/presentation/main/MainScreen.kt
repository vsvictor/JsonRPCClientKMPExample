package com.mobilespace.jsonrpcclientkmpexample.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.presentation.components.ChoiceField
import com.mobilespace.jsonrpcclientkmpexample.presentation.components.DemoCard
import com.mobilespace.jsonrpcclientkmpexample.presentation.components.DemoField

@Composable
fun MainScreen(state: MainUiState, onAction: (MainAction) -> Unit) {
    val form = state.form
    val onFormChange: (MainForm) -> Unit = { onAction(MainAction.FormChanged(it)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("JSON-RPC KMP client", style = MaterialTheme.typography.headlineSmall)
        Text(
            "One shared Kotlin API backed by the json-rpc-2 Rust FFI on Android and iOS.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ConnectionBuilderSection(state, form, onFormChange)
        TransportOptionsSection(form, onFormChange)
        ConnectionControls(state, onAction)
        RequestsSection(state, form, onFormChange, onAction)
        RuntimeControlsSection(state, form, onFormChange, onAction)

        DemoCard("Last output") {
            Text(state.status, style = MaterialTheme.typography.titleSmall)
            if (state.result.isNotEmpty()) {
                Text(state.result, style = MaterialTheme.typography.bodyMedium)
            }
        }

        DemoCard("Connection and error log") {
            OutlinedButton(
                onClick = { onAction(MainAction.ClearLog) },
                enabled = state.logEntries.isNotEmpty(),
            ) { Text("Clear log") }
            Text(
                state.logEntries.joinToString("\n").ifEmpty { "No log entries" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ConnectionBuilderSection(
    state: MainUiState,
    form: MainForm,
    onFormChange: (MainForm) -> Unit,
) = DemoCard("Connection builder") {
    ChoiceField(
        label = "Transport (${state.transports.size} available)",
        value = form.transportId,
        options = state.transports.map { it.id },
        onSelected = { onFormChange(form.copy(transportId = it)) },
    )
    Text(
        "All raw, secure, broker, HTTP and WebSocket FFI transports are selectable. " +
            "UDP-Lite, DCCP and SCTP are Android/Linux only.",
        style = MaterialTheme.typography.bodySmall,
    )
    DemoField("Host", form.host, { onFormChange(form.copy(host = it)) }, singleLine = true)
    DemoField("Port", form.port, { onFormChange(form.copy(port = it)) }, singleLine = true)
    DemoField(
        "TLS server name (optional)",
        form.serverName,
        { onFormChange(form.copy(serverName = it)) },
        singleLine = true,
    )
    ChoiceField(
        label = "CA certificate source",
        value = form.certificateSource,
        options = CertificateSourceOption.entries,
        onSelected = { onFormChange(form.copy(certificateSource = it)) },
        optionLabel = CertificateSourceOption::label,
    )
    if (form.certificateSource != CertificateSourceOption.NONE) {
        ChoiceField(
            label = "Certificate encoding",
            value = form.certificateEncoding,
            options = CertificateEncoding.entries,
            onSelected = { onFormChange(form.copy(certificateEncoding = it)) },
            optionLabel = CertificateEncoding::name,
        )
        DemoField(
            form.certificateSource.inputLabel,
            form.certificateInput,
            { onFormChange(form.copy(certificateInput = it)) },
            singleLine = form.certificateSource.singleLineInput,
            minLines = form.certificateSource.inputMinLines,
        )
        Text(
            "Compose resources are shared: add the certificate to shared/src/commonMain/composeResources/files. " +
                "File path and app asset use platform-specific locations.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TransportOptionsSection(
    form: MainForm,
    onFormChange: (MainForm) -> Unit,
) = DemoCard("Transport-specific options") {
    DemoField(
        "SRT passphrase",
        form.srtPassphrase,
        { onFormChange(form.copy(srtPassphrase = it)) },
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DemoField(
            "UDP-Lite send coverage",
            form.udpLiteSendCoverage,
            { onFormChange(form.copy(udpLiteSendCoverage = it)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        DemoField(
            "UDP-Lite receive coverage",
            form.udpLiteReceiveCoverage,
            { onFormChange(form.copy(udpLiteReceiveCoverage = it)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    DemoField(
        "Broker URL (MQTT/AMQP)",
        form.brokerUrl,
        { onFormChange(form.copy(brokerUrl = it)) },
        singleLine = true,
    )
    DemoField(
        "Broker route",
        form.brokerRoute,
        { onFormChange(form.copy(brokerRoute = it)) },
        singleLine = true,
    )
    DemoField(
        "Broker CA PEM",
        form.brokerCaPem,
        { onFormChange(form.copy(brokerCaPem = it)) },
        minLines = 3,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DemoField(
            "Broker operation timeout (ms)",
            form.brokerOperationTimeout,
            { onFormChange(form.copy(brokerOperationTimeout = it)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        DemoField(
            "Broker response timeout (ms)",
            form.brokerResponseTimeout,
            { onFormChange(form.copy(brokerResponseTimeout = it)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConnectionControls(state: MainUiState, onAction: (MainAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = state.canConnect,
            onClick = { onAction(MainAction.Connect) },
        ) { Text("Connect") }
        OutlinedButton(
            enabled = state.canUseConnection,
            onClick = { onAction(MainAction.Disconnect) },
        ) { Text("Disconnect") }
        Text("State: ${state.status}", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun RequestsSection(
    state: MainUiState,
    form: MainForm,
    onFormChange: (MainForm) -> Unit,
    onAction: (MainAction) -> Unit,
) = DemoCard("JSON-RPC requests") {
    DemoField("Method", form.method, { onFormChange(form.copy(method = it)) }, singleLine = true)
    DemoField(
        "Params JSON (object or array)",
        form.paramsJson,
        { onFormChange(form.copy(paramsJson = it)) },
        minLines = 3,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = state.canUseConnection,
            onClick = { onAction(MainAction.Call) },
        ) { Text("Call") }
        OutlinedButton(
            enabled = state.canUseConnection,
            onClick = { onAction(MainAction.Notify) },
        ) { Text("Notify") }
        OutlinedButton(
            enabled = state.canUseConnection,
            onClick = { onAction(MainAction.TransportPing) },
        ) { Text("Transport ping") }
    }
}

@Composable
private fun RuntimeControlsSection(
    state: MainUiState,
    form: MainForm,
    onFormChange: (MainForm) -> Unit,
    onAction: (MainAction) -> Unit,
) = DemoCard("Runtime controls") {
    DemoField(
        "Ping frequency (ms)",
        form.pingFrequency,
        { onFormChange(form.copy(pingFrequency = it)) },
        singleLine = true,
    )
    DemoField(
        "Maximum missed pongs",
        form.maxMissedPongs,
        { onFormChange(form.copy(maxMissedPongs = it)) },
        singleLine = true,
    )
    DemoField(
        "Request timeout (ms)",
        form.requestTimeout,
        { onFormChange(form.copy(requestTimeout = it)) },
        singleLine = true,
    )
    OutlinedButton(
        enabled = state.canUseConnection,
        onClick = { onAction(MainAction.ApplyRuntimeSettings) },
    ) { Text("Apply settings") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.logPingPong,
            onCheckedChange = { onAction(MainAction.LogPingPongChanged(it)) },
            enabled = !state.isBusy,
        )
        Text("Log every periodic ping-pong")
    }
    Text(
        "Show native ping/pong events. Monitoring and recovery run regardless of this checkbox. " +
            "Ping frequency 0 disables monitoring.",
        style = MaterialTheme.typography.bodySmall,
    )
}

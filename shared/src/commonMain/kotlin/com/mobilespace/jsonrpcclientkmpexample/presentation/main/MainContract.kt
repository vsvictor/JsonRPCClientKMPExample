package com.mobilespace.jsonrpcclientkmpexample.presentation.main

import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport

enum class CertificateSourceOption(
    val label: String,
    val inputLabel: String,
    val singleLineInput: Boolean,
    val inputMinLines: Int,
) {
    NONE("None", "", true, 1),
    PEM_TEXT("PEM text", "PEM certificate text", false, 4),
    PEM_BYTES("PEM bytes", "PEM certificate text (byte-array API)", false, 4),
    DER_BASE64("DER (base64 text)", "DER certificate, base64 encoded", false, 1),
    FILE_PATH("File path", "Certificate file path", true, 1),
    APP_ASSET("App asset", "Certificate asset name", true, 1),
    SHARED_RESOURCE("Shared Compose resource", "Filename under composeResources/files", true, 1),
}

/** Raw, user-editable form values; parsed into domain models by [MainFormMapper]. */
data class MainForm(
    val transportId: String = "TCP",
    val host: String = "127.0.0.1",
    val port: String = "12345",
    val serverName: String = "",
    val certificateSource: CertificateSourceOption = CertificateSourceOption.NONE,
    val certificateEncoding: CertificateEncoding = CertificateEncoding.PEM,
    val certificateInput: String = "",
    val srtPassphrase: String = "",
    val udpLiteSendCoverage: String = "",
    val udpLiteReceiveCoverage: String = "",
    val brokerUrl: String = "",
    val brokerRoute: String = "",
    val brokerCaPem: String = "",
    val brokerOperationTimeout: String = "",
    val brokerResponseTimeout: String = "",
    val method: String = "echo",
    val paramsJson: String = """{"message":"hello"}""",
    val pingFrequency: String = "1000",
    val maxMissedPongs: String = "3",
    val requestTimeout: String = "10000",
)

data class MainUiState(
    val form: MainForm = MainForm(),
    val transports: List<Transport> = emptyList(),
    val isConnected: Boolean = false,
    val isBusy: Boolean = false,
    val status: String = "Disconnected",
    val result: String = "",
    val logPingPong: Boolean = false,
    val logEntries: List<String> = emptyList(),
) {
    val canConnect: Boolean get() = !isBusy && !isConnected
    val canUseConnection: Boolean get() = !isBusy && isConnected
}

sealed interface MainAction {
    data class FormChanged(val form: MainForm) : MainAction
    data class LogPingPongChanged(val enabled: Boolean) : MainAction
    data object Connect : MainAction
    data object Disconnect : MainAction
    data object Call : MainAction
    data object Notify : MainAction
    data object TransportPing : MainAction
    data object ApplyRuntimeSettings : MainAction
    data object ClearLog : MainAction
}

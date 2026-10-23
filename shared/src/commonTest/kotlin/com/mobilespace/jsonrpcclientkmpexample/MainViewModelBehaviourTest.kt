package com.mobilespace.jsonrpcclientkmpexample

import com.mobilespace.jsonrpcclientkmpexample.domain.model.LivenessEvent
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ApplyRuntimeSettingsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.CallMethodUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ConnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.DisconnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.GetAvailableTransportsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveConnectionStateUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveLivenessEventsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.SendNotificationUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.TransportPingUseCase
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.CertificateSourceOption
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainAction
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainForm
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelBehaviourTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeJsonRpcRepository
    private val sink = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeJsonRpcRepository()
        sink.clear()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = MainViewModel(
        getAvailableTransports = GetAvailableTransportsUseCase(repository),
        observeConnectionState = ObserveConnectionStateUseCase(repository),
        observeLivenessEvents = ObserveLivenessEventsUseCase(repository),
        connect = ConnectUseCase(repository),
        disconnect = DisconnectUseCase(repository),
        callMethod = CallMethodUseCase(repository),
        sendNotification = SendNotificationUseCase(repository),
        transportPing = TransportPingUseCase(repository),
        applyRuntimeSettings = ApplyRuntimeSettingsUseCase(repository),
        logSink = { sink += it },
    )

    private fun TestScope.connected(): MainViewModel = createViewModel().also {
        it.onAction(MainAction.Connect)
        runCurrent()
    }

    private fun TestScope.disconnect(viewModel: MainViewModel) {
        viewModel.onAction(MainAction.Disconnect)
        runCurrent()
    }

    @Test
    fun initialStateListsRepositoryTransports() = runTest(dispatcher) {
        val state = createViewModel().uiState.value
        assertEquals(listOf("TCP", "TLS"), state.transports.map { it.id })
        assertEquals("Disconnected", state.status)
        assertTrue(state.canConnect)
    }

    @Test
    fun formAndCheckboxChangesUpdateState() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onAction(MainAction.FormChanged(MainForm(host = "10.1.0.196")))
        viewModel.onAction(MainAction.LogPingPongChanged(true))
        assertEquals("10.1.0.196", viewModel.uiState.value.form.host)
        assertTrue(viewModel.uiState.value.logPingPong)
    }

    @Test
    fun busyGuardIgnoresActionsWhileOperationRuns() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.connectGate = gate
        val viewModel = createViewModel()
        viewModel.onAction(MainAction.Connect)
        runCurrent()
        assertTrue(viewModel.uiState.value.isBusy)
        assertEquals("Connect…", viewModel.uiState.value.status)

        viewModel.onAction(MainAction.Connect)
        viewModel.onAction(MainAction.TransportPing)
        runCurrent()
        assertEquals(1, repository.connectCalls)
        assertEquals(0, repository.transportPings)

        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isBusy)
        assertTrue(viewModel.uiState.value.isConnected)
        disconnect(viewModel)
    }

    @Test
    fun invalidFormFailsWithoutReachingRepository() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onAction(MainAction.FormChanged(MainForm(port = "port")))
        viewModel.onAction(MainAction.Connect)
        runCurrent()
        assertEquals("Connect failed: Port must be a number between 1 and 65535", viewModel.uiState.value.status)
        assertEquals(0, repository.connectCalls)
    }

    @Test
    fun connectLogsCertificateLoading() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onAction(
            MainAction.FormChanged(
                MainForm(certificateSource = CertificateSourceOption.SHARED_RESOURCE, certificateInput = "cert.pem"),
            ),
        )
        viewModel.onAction(MainAction.Connect)
        runCurrent()
        val log = viewModel.uiState.value.logEntries
        assertTrue(log.any { it.startsWith("Loading CA certificate (Shared Compose resource, PEM)") }, "$log")
        assertTrue(log.any { it.startsWith("Native ping frequency: 1000ms") })
        assertEquals(log, sink)
        disconnect(viewModel)
    }

    @Test
    fun notifyPingAndApplySettingsSucceed() = runTest(dispatcher) {
        val viewModel = connected()
        viewModel.onAction(MainAction.Notify)
        runCurrent()
        assertEquals("Notification sent for echo", viewModel.uiState.value.result)
        assertEquals(1, repository.notifications)

        viewModel.onAction(MainAction.TransportPing)
        runCurrent()
        assertEquals("Transport ping completed", viewModel.uiState.value.result)
        assertEquals(1, repository.transportPings)

        viewModel.onAction(MainAction.FormChanged(MainForm(pingFrequency = "200", maxMissedPongs = "4", requestTimeout = "500")))
        viewModel.onAction(MainAction.ApplyRuntimeSettings)
        runCurrent()
        assertEquals(RuntimeSettings(200uL, 4u, 500uL), repository.appliedSettings)
        assertEquals("Apply runtime settings succeeded", viewModel.uiState.value.status)
        disconnect(viewModel)
    }

    @Test
    fun invalidRuntimeSettingsAreReported() = runTest(dispatcher) {
        val viewModel = connected()
        viewModel.onAction(MainAction.FormChanged(MainForm(requestTimeout = "0")))
        viewModel.onAction(MainAction.ApplyRuntimeSettings)
        runCurrent()
        assertEquals(
            "Apply runtime settings failed: Request timeout must be greater than zero",
            viewModel.uiState.value.status,
        )
        assertNull(repository.appliedSettings)
        disconnect(viewModel)
    }

    @Test
    fun operationErrorsKeepConnectionAndLogStackTrace() = runTest(dispatcher) {
        val viewModel = connected()
        repository.operationError = IllegalStateException("Remote: Method not found")
        viewModel.onAction(MainAction.Call)
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals("Call failed: Remote: Method not found", state.status)
        assertTrue(state.isConnected)
        assertTrue(state.logEntries.any { it.startsWith("Call failed: java.lang.IllegalStateException") || it.startsWith("Call failed: kotlin.IllegalStateException") })
        disconnect(viewModel)
    }

    @Test
    fun disconnectUpdatesStateAndStopsLivenessPolling() = runTest(dispatcher) {
        val viewModel = connected()
        disconnect(viewModel)
        assertFalse(viewModel.uiState.value.isConnected)
        assertEquals("Connection closed", viewModel.uiState.value.result)

        repository.pendingLivenessEvents += LivenessEvent("RECONNECT: should not be read")
        advanceTimeBy(500)
        assertFalse(viewModel.uiState.value.logEntries.any { it.startsWith("RECONNECT") })
    }

    @Test
    fun livenessReadFailureIsLogged() = runTest(dispatcher) {
        repository.livenessError = IllegalStateException("liveness broken")
        val viewModel = connected()
        advanceTimeBy(150)
        assertTrue(viewModel.uiState.value.logEntries.any { it.startsWith("Unable to read liveness log:") })
        disconnect(viewModel)
    }

    @Test
    fun logIsCappedAt500Entries() = runTest(dispatcher) {
        val viewModel = connected()
        viewModel.onAction(MainAction.LogPingPongChanged(true))
        repository.pendingLivenessEvents += (1..600).map { LivenessEvent("PING: $it") }
        advanceTimeBy(150)
        val log = viewModel.uiState.value.logEntries
        assertEquals(500, log.size)
        assertEquals("PING: 600", log.last())
        disconnect(viewModel)
    }
}

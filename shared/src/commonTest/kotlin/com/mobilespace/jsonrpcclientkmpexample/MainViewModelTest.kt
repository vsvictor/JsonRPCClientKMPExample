package com.mobilespace.jsonrpcclientkmpexample

import com.mobilespace.jsonrpcclientkmpexample.domain.model.LivenessEvent
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ApplyRuntimeSettingsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.CallMethodUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ConnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.DisconnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.GetAvailableTransportsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveConnectionStateUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveLivenessEventsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.SendNotificationUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.TransportPingUseCase
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainAction
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainViewModel
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeJsonRpcRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeJsonRpcRepository()
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
        logSink = {},
    )

    private fun TestScope.connected(): MainViewModel = createViewModel().also {
        it.onAction(MainAction.Connect)
        runCurrent()
    }

    @Test
    fun connectUpdatesStateAndLogs() = runTest(dispatcher) {
        val viewModel = connected()
        val state = viewModel.uiState.value
        assertTrue(state.isConnected)
        assertFalse(state.isBusy)
        assertEquals("Connect succeeded", state.status)
        assertTrue("Native connection established" in state.logEntries)
        assertEquals(3u, repository.connectedConfig?.monitoring?.maxMissedPongs)
        viewModel.onAction(MainAction.Disconnect)
        runCurrent()
    }

    @Test
    fun connectFailureIsReportedAndLogged() = runTest(dispatcher) {
        repository.connectError = IllegalStateException("No route to host")
        val viewModel = connected()
        val state = viewModel.uiState.value
        assertFalse(state.isConnected)
        assertEquals("Connect failed: No route to host", state.status)
        assertTrue(state.logEntries.any { it.startsWith("Connect failed:") })
    }

    @Test
    fun pingPongEventsAreFilteredUnlessEnabled() = runTest(dispatcher) {
        val viewModel = connected()
        repository.pendingLivenessEvents += listOf(LivenessEvent("PING: 1"), LivenessEvent("RECOVERY: restore"))
        advanceTimeBy(150)
        assertFalse("PING: 1" in viewModel.uiState.value.logEntries)
        assertTrue("RECOVERY: restore" in viewModel.uiState.value.logEntries)

        viewModel.onAction(MainAction.LogPingPongChanged(true))
        repository.pendingLivenessEvents += LivenessEvent("PONG: 2")
        advanceTimeBy(150)
        assertTrue("PONG: 2" in viewModel.uiState.value.logEntries)

        viewModel.onAction(MainAction.Disconnect)
        runCurrent()
    }

    @Test
    fun callStoresResultAndClearLogEmptiesLog() = runTest(dispatcher) {
        val viewModel = connected()
        repository.callResult = """{"ok":true}"""
        viewModel.onAction(MainAction.Call)
        runCurrent()
        assertEquals("""{"ok":true}""", viewModel.uiState.value.result)
        assertEquals("echo", repository.lastRequest?.method)

        viewModel.onAction(MainAction.ClearLog)
        assertTrue(viewModel.uiState.value.logEntries.isEmpty())

        viewModel.onAction(MainAction.Disconnect)
        runCurrent()
    }
}

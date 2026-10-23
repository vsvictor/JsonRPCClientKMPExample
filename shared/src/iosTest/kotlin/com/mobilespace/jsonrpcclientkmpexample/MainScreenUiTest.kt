package com.mobilespace.jsonrpcclientkmpexample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainAction
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainScreen
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MainScreenUiTest {
    private val transports = listOf(Transport("TCP", true), Transport("TLS", true))

    private fun ComposeUiTest.button(text: String): SemanticsNodeInteraction =
        onNode(hasText(text) and hasClickAction()).performScrollTo()

    private fun ComposeUiTest.show(state: MainUiState, actions: MutableList<MainAction> = mutableListOf()) =
        setContent { MainScreen(state) { actions += it } }

    @Test
    fun disconnectedStateEnablesOnlyConnect() = runComposeUiTest {
        show(MainUiState(transports = transports))
        onNode(hasText("JSON-RPC KMP client")).assertIsDisplayed()
        button("Connect").assertIsEnabled()
        listOf("Disconnect", "Call", "Notify", "Transport ping", "Apply settings", "Clear log")
            .forEach { button(it).assertIsNotEnabled() }
        onNode(hasText("No log entries")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun connectedStateEnablesOperationsAndDispatchesActions() = runComposeUiTest {
        val actions = mutableListOf<MainAction>()
        show(MainUiState(transports = transports, isConnected = true, logEntries = listOf("entry")), actions)
        button("Connect").assertIsNotEnabled()
        listOf("Disconnect", "Call", "Notify", "Transport ping", "Apply settings", "Clear log")
            .forEach { button(it).assertIsEnabled().performClick() }
        assertEquals(
            listOf(
                MainAction.Disconnect,
                MainAction.Call,
                MainAction.Notify,
                MainAction.TransportPing,
                MainAction.ApplyRuntimeSettings,
                MainAction.ClearLog,
            ),
            actions,
        )
    }

    @Test
    fun busyStateDisablesEverything() = runComposeUiTest {
        show(MainUiState(transports = transports, isConnected = true, isBusy = true, status = "Call…"))
        listOf("Connect", "Disconnect", "Call", "Notify", "Transport ping", "Apply settings")
            .forEach { button(it).assertIsNotEnabled() }
        onNode(isToggleable()).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun pingPongCheckboxDispatchesToggle() = runComposeUiTest {
        val actions = mutableListOf<MainAction>()
        show(MainUiState(transports = transports), actions)
        onNode(isToggleable()).performScrollTo().assertIsOff().performClick()
        assertEquals(listOf<MainAction>(MainAction.LogPingPongChanged(true)), actions)
    }

    @Test
    fun editingHostDispatchesFormChange() = runComposeUiTest {
        val actions = mutableListOf<MainAction>()
        show(MainUiState(transports = transports), actions)
        onNode(hasSetTextAction() and hasText("Host")).performScrollTo().performTextReplacement("10.1.0.196")
        val change = actions.filterIsInstance<MainAction.FormChanged>().last()
        assertEquals("10.1.0.196", change.form.host)
    }

    @Test
    fun logAndResultAreRenderedAndClearWorksWithStatefulHost() = runComposeUiTest {
        var state by mutableStateOf(
            MainUiState(
                transports = transports,
                logPingPong = true,
                status = "Call succeeded",
                result = "echo-result-42",
                logEntries = listOf("Connect started", "PING: 1"),
            ),
        )
        setContent {
            MainScreen(state) { action ->
                if (action == MainAction.ClearLog) state = state.copy(logEntries = emptyList())
            }
        }
        onNode(isToggleable()).performScrollTo().assertIsOn()
        onNode(hasText("echo-result-42")).performScrollTo().assertIsDisplayed()
        onNode(hasText("Connect started\nPING: 1")).performScrollTo().assertIsDisplayed()
        button("Clear log").performClick()
        waitForIdle()
        assertTrue(state.logEntries.isEmpty())
        onNode(hasText("No log entries")).performScrollTo().assertIsDisplayed()
        button("Clear log").assertIsNotEnabled()
    }
}

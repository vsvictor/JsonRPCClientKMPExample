package com.mobilespace.jsonrpcclientkmpexample.di

import com.mobilespace.jsonrpcclientkmpexample.data.repository.JsonRpcRepositoryImpl
import com.mobilespace.jsonrpcclientkmpexample.data.source.ComposeCertificateResourceDataSource
import com.mobilespace.jsonrpcclientkmpexample.domain.repository.JsonRpcRepository
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ApplyRuntimeSettingsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.CallMethodUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ConnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.DisconnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.GetAvailableTransportsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveConnectionStateUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveLivenessEventsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.SendNotificationUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.TransportPingUseCase
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainViewModel

/** Composition root: the only place that knows concrete implementations of every layer. */
object AppContainer {
    // Process-wide so the native connection outlives ViewModel recreation.
    private val repository: JsonRpcRepository by lazy {
        JsonRpcRepositoryImpl(ComposeCertificateResourceDataSource())
    }

    fun createMainViewModel(): MainViewModel = MainViewModel(
        getAvailableTransports = GetAvailableTransportsUseCase(repository),
        observeConnectionState = ObserveConnectionStateUseCase(repository),
        observeLivenessEvents = ObserveLivenessEventsUseCase(repository),
        connect = ConnectUseCase(repository),
        disconnect = DisconnectUseCase(repository),
        callMethod = CallMethodUseCase(repository),
        sendNotification = SendNotificationUseCase(repository),
        transportPing = TransportPingUseCase(repository),
        applyRuntimeSettings = ApplyRuntimeSettingsUseCase(repository),
    )
}

package no.shoppinglist.shared.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class ConnectivityMonitor {
    private val _isConnected = MutableStateFlow(true)
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    actual fun startMonitoring() {
        // TODO: Implement using NWPathMonitor from Network framework
        // val monitor = NWPathMonitor()
        // monitor.setUpdateHandler { path ->
        //     _isConnected.value = path.status == .satisfied
        // }
        // monitor.start(queue: .main)
        _isConnected.value = true
    }

    actual fun stopMonitoring() {
        // TODO: Cancel NWPathMonitor
    }
}

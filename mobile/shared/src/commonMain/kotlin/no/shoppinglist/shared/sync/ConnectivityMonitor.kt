package no.shoppinglist.shared.sync

import kotlinx.coroutines.flow.StateFlow

expect class ConnectivityMonitor {
    val isConnected: StateFlow<Boolean>
    fun startMonitoring()
    fun stopMonitoring()
}

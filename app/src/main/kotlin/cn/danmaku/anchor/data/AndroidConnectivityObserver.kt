package cn.danmaku.anchor.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import cn.danmaku.anchor.domain.session.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidConnectivityObserver(
    context: Context,
) : ConnectivityObserver {
    private val connectivityManager = requireNotNull(context.getSystemService<ConnectivityManager>())
    private val connectionState = MutableStateFlow(isNetworkAvailable())
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            connectionState.value = true
        }

        override fun onLost(network: Network) {
            connectionState.value = isNetworkAvailable()
        }

        override fun onUnavailable() {
            connectionState.value = false
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    override val isConnected: StateFlow<Boolean> = connectionState.asStateFlow()

    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

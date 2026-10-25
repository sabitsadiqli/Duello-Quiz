package com.sabit.duelloquiz.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/**
 * Point-in-time check whether the device itself has working internet, used to tell "no internet"
 * from "our server is unreachable" after a failed connection (DataError.ServerUnreachable).
 */
fun interface DeviceConnectivity {
    fun hasInternet(): Boolean
}

/** Active network with internet that Android has validated (not a captive portal). */
internal class ConnectivityManagerDeviceConnectivity(private val context: Context) : DeviceConnectivity {
    override fun hasInternet(): Boolean {
        val manager = context.getSystemService<ConnectivityManager>() ?: return false
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

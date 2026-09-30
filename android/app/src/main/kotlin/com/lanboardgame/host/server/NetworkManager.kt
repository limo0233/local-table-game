package com.lanboardgame.host.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkAddress(
    val address: String,
    val interfaceName: String,
    val label: String,
    val recommended: Boolean = false,
)

internal enum class NetworkTransport { WIFI, ETHERNET, VPN, CELLULAR, OTHER }

internal data class NetworkCandidate(
    val address: String,
    val interfaceName: String,
    val transport: NetworkTransport,
)

object NetworkManager {
    private const val TAG = "LanGame.Network"

    fun localIpv4Addresses(context: Context): List<NetworkAddress> = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivity.activeNetwork
        val activeProperties = activeNetwork?.let(connectivity::getLinkProperties)
        val activeInterface = activeProperties?.interfaceName
        val activeIsWifi = activeNetwork?.let(connectivity::getNetworkCapabilities)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val candidates = buildList {
            connectivity.allNetworks.forEach { network ->
                val properties = connectivity.getLinkProperties(network) ?: return@forEach
                val capabilities = connectivity.getNetworkCapabilities(network)
                val transport = capabilities.toTransport()
                val interfaceName = properties.interfaceName.orEmpty()
                properties.linkAddresses.asSequence()
                    .map { it.address }
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .forEach { address -> add(NetworkCandidate(address, interfaceName, transport)) }
            }

            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .forEach { networkInterface ->
                    val name = networkInterface.name.orEmpty()
                    val transport = inferTransport(name)
                    networkInterface.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .mapNotNull { it.hostAddress }
                        .forEach { address -> add(NetworkCandidate(address, name, transport)) }
                }
        }

        rankCandidates(candidates, activeInterface, activeIsWifi).also { addresses ->
            Log.i(
                TAG,
                "discovery activeInterface=${activeInterface.orEmpty()} activeWifi=$activeIsWifi " +
                    "candidates=${candidates.size} accepted=${addresses.joinToString { "${it.interfaceName}/${it.address}" }}",
            )
        }
    }.onFailure { error ->
        Log.e(TAG, "network discovery failed", error)
    }.getOrDefault(emptyList())

    internal fun rankCandidates(
        candidates: List<NetworkCandidate>,
        activeInterface: String?,
        activeIsWifi: Boolean,
    ): List<NetworkAddress> = candidates.asSequence()
        .filter { isRfc1918(it.address) }
        .filterNot(::isExcluded)
        .groupBy(NetworkCandidate::address)
        .mapNotNull { (_, sameAddress) ->
            sameAddress.maxByOrNull { score(it, activeInterface, activeIsWifi) }
        }
        .sortedWith(
            compareByDescending<NetworkCandidate> { score(it, activeInterface, activeIsWifi) }
                .thenBy(NetworkCandidate::address),
        )
        .mapIndexed { index, candidate ->
            NetworkAddress(
                address = candidate.address,
                interfaceName = candidate.interfaceName,
                label = label(candidate, candidate.interfaceName == activeInterface && activeIsWifi),
                recommended = index == 0,
            )
        }

    private fun NetworkCapabilities?.toTransport(): NetworkTransport = when {
        this == null -> NetworkTransport.OTHER
        hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.VPN
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
        else -> NetworkTransport.OTHER
    }

    private fun inferTransport(name: String): NetworkTransport {
        val normalized = name.lowercase()
        return when {
            normalized.startsWith("tun") || normalized.startsWith("tap") || normalized.startsWith("wg") ||
                normalized.startsWith("ppp") -> NetworkTransport.VPN
            normalized.startsWith("rmnet") || normalized.startsWith("ccmni") || normalized.startsWith("pdp") ||
                normalized.startsWith("v4-rmnet") -> NetworkTransport.CELLULAR
            normalized.startsWith("wlan") || normalized.startsWith("ap") || normalized.contains("softap") ||
                normalized.startsWith("swlan") -> NetworkTransport.WIFI
            normalized.startsWith("eth") -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        }
    }

    private fun isExcluded(candidate: NetworkCandidate): Boolean {
        val name = candidate.interfaceName.lowercase()
        return candidate.transport in setOf(NetworkTransport.VPN, NetworkTransport.CELLULAR) ||
            name.startsWith("p2p") || name.contains("wifi-direct")
    }

    private fun isRfc1918(address: String): Boolean {
        val octets = address.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    private fun score(candidate: NetworkCandidate, activeInterface: String?, activeIsWifi: Boolean): Int {
        val normalized = candidate.interfaceName.lowercase()
        var score = when (candidate.transport) {
            NetworkTransport.WIFI -> 120
            NetworkTransport.ETHERNET -> 90
            NetworkTransport.OTHER -> 20
            NetworkTransport.VPN, NetworkTransport.CELLULAR -> -500
        }
        if (activeIsWifi && candidate.interfaceName == activeInterface) score += 200
        if (normalized.startsWith("ap") || normalized.contains("softap") || normalized.startsWith("swlan")) score += 15
        return score
    }

    private fun label(candidate: NetworkCandidate, activeWifi: Boolean): String = when {
        activeWifi -> "当前 Wi-Fi（${candidate.interfaceName}）"
        candidate.interfaceName.lowercase().startsWith("ap") ||
            candidate.interfaceName.lowercase().contains("softap") -> "手机热点（${candidate.interfaceName}）"
        candidate.transport == NetworkTransport.WIFI -> "Wi-Fi / 热点（${candidate.interfaceName}）"
        candidate.transport == NetworkTransport.ETHERNET -> "有线网络（${candidate.interfaceName}）"
        else -> "其他局域网（${candidate.interfaceName}）"
    }
}

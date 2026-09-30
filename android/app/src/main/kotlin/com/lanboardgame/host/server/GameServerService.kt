package com.lanboardgame.host.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.util.Log
import com.lanboardgame.host.MainActivity
import com.lanboardgame.platform.RoomManager
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GameServerService : Service() {
    private val serverLock = Any()
    private val deployExecutor = Executors.newSingleThreadScheduledExecutor()
    private val roomManager = RoomManager()
    private var server: LocalGameServer? = null
    @Volatile private var currentPort = DEFAULT_PORT
    @Volatile private var selectedAddress = ""
    @Volatile private var deployed = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        currentPort = preferences.getInt(KEY_PORT, DEFAULT_PORT).takeIf(::validPort) ?: DEFAULT_PORT
        val available = NetworkManager.localIpv4Addresses(applicationContext)
        selectedAddress = preferences.getString(KEY_ADDRESS, null)
            ?.takeIf { saved -> available.any { it.address == saved } }
            ?: available.firstOrNull()?.address.orEmpty()
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        startForeground(NOTIFICATION_ID, notification(pending))
        startServerWithRecovery()
        registerNetworkMonitoring()
    }

    override fun onDestroy() {
        networkCallback?.let { callback ->
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        synchronized(serverLock) {
            server?.shutdown()
            server = null
        }
        deployExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "本地游戏服务", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startServer() {
        synchronized(serverLock) {
            val candidate = LocalGameServer(
                context = applicationContext,
                bindAddress = serverBindAddress(deployed),
                port = currentPort,
                roomManager = roomManager,
                hostStateProvider = { HostServerState(selectedAddress, currentPort, deployed) },
                deployHandler = ::deploy,
                undeployHandler = ::undeploy,
            )
            try {
                // A NanoHTTPD timeout is applied to every accepted socket,
                // including upgraded WebSockets.  A five-second value makes an
                // otherwise healthy idle game connection look disconnected.
                // Keep the listener timeout disabled; the WebSocket layer still
                // handles explicit close frames and network I/O failures.
                candidate.start(0, false)
                server = candidate
                Log.i(TAG, "server started bind=${serverBindAddress(deployed)} port=$currentPort selectedAddress=$selectedAddress deployed=$deployed")
            } catch (error: Throwable) {
                runCatching { candidate.shutdown() }
                Log.e(TAG, "server start failed port=$currentPort", error)
                throw error
            }
        }
    }

    private fun startServerWithRecovery() {
        runCatching(::startServer).onFailure { initialError ->
            val failedPort = currentPort
            val fallbackPort = findFallbackPort(failedPort)
            Log.w(TAG, "configured port unavailable; retrying failedPort=$failedPort fallbackPort=$fallbackPort", initialError)
            currentPort = fallbackPort
            deployed = false
            persistConfiguration()
            startServer()
            updateNotification()
        }
    }

    private fun deploy(address: String, port: Int): DeploymentResult {
        Log.i(TAG, "deploy requested address=$address port=$port currentPort=$currentPort")
        require(validPort(port)) { "端口号必须在 1024–65535 之间" }
        val available = NetworkManager.localIpv4Addresses(applicationContext)
        require(available.any { it.address == address }) { "所选 IP 已不可用，请刷新后重新选择" }

        val previousPort = currentPort
        val previousAddress = selectedAddress
        val previousDeployed = deployed
        val restarting = port != previousPort || !previousDeployed
        if (port != previousPort) ensurePortAvailable(port)

        selectedAddress = address
        currentPort = port
        deployed = true
        persistConfiguration()
        updateNotification()

        if (restarting) {
            deployExecutor.schedule({
                synchronized(serverLock) {
                    server?.shutdown()
                    server = null
                    runCatching { startServer() }.onFailure {
                        Log.e(TAG, "deploy restart failed; restoring port=$previousPort address=$previousAddress", it)
                        currentPort = previousPort
                        selectedAddress = previousAddress
                        deployed = previousDeployed
                        persistConfiguration()
                        runCatching { startServer() }
                        updateNotification()
                    }
                }
            }, 800, TimeUnit.MILLISECONDS)
        }
        return DeploymentResult(address, port, deployed = true, restarting = restarting)
    }

    private fun undeploy(): DeploymentResult {
        Log.i(TAG, "undeploy requested port=$currentPort selectedAddress=$selectedAddress deployed=$deployed")
        if (!deployed) return DeploymentResult(selectedAddress, currentPort, deployed = false, restarting = false)

        deployed = false
        persistConfiguration()
        updateNotification()
        deployExecutor.schedule({
            synchronized(serverLock) {
                server?.shutdown()
                server = null
                runCatching { startServer() }.onFailure {
                    Log.e(TAG, "undeploy restart failed; restoring LAN listener", it)
                    deployed = true
                    persistConfiguration()
                    runCatching { startServer() }
                    updateNotification()
                }
            }
        }, 800, TimeUnit.MILLISECONDS)
        return DeploymentResult(selectedAddress, currentPort, deployed = false, restarting = true)
    }

    private fun registerNetworkMonitoring() {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshNetworkSelection("available:$network")
            override fun onLost(network: Network) = refreshNetworkSelection("lost:$network")
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                refreshNetworkSelection("capabilities:$network")
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                refreshNetworkSelection("link-properties:$network/${linkProperties.interfaceName.orEmpty()}")
        }
        val request = NetworkRequest.Builder().build()
        runCatching { connectivity.registerNetworkCallback(request, callback) }
            .onSuccess {
                networkCallback = callback
                Log.i(TAG, "network callback registered without INTERNET/VALIDATED requirement")
            }
            .onFailure { Log.e(TAG, "network callback registration failed", it) }
    }

    private fun refreshNetworkSelection(reason: String) {
        runCatching {
            deployExecutor.execute {
                val available = NetworkManager.localIpv4Addresses(applicationContext)
                val oldAddress = selectedAddress
                val addressStillAvailable = available.any { it.address == oldAddress }
                if (!addressStillAvailable) {
                    val restrictToLoopback = deployed
                    selectedAddress = available.firstOrNull()?.address.orEmpty()
                    deployed = false
                    persistConfiguration()
                    updateNotification()
                    if (restrictToLoopback) {
                        synchronized(serverLock) {
                            server?.shutdown()
                            server = null
                            runCatching { startServer() }
                                .onFailure { Log.e(TAG, "failed to restrict server to loopback after network loss", it) }
                        }
                    }
                }
                Log.i(
                    TAG,
                    "network changed reason=$reason previous=$oldAddress selected=$selectedAddress " +
                        "deployed=$deployed available=${available.joinToString { it.address }}",
                )
            }
        }.onFailure { Log.w(TAG, "network refresh skipped reason=$reason", it) }
    }

    private fun persistConfiguration() {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putString(KEY_ADDRESS, selectedAddress)
            .putInt(KEY_PORT, currentPort)
            .commit()
    }

    private fun findFallbackPort(failedPort: Int): Int {
        val preferred = (DEFAULT_PORT..DEFAULT_PORT + 100).firstOrNull { it != failedPort && isPortAvailable(it) }
        if (preferred != null) return preferred
        return ServerSocket(0).use { it.localPort }.takeIf(::validPort)
            ?: error("没有可用的本地服务端口")
    }

    private fun isPortAvailable(port: Int): Boolean = runCatching {
        ensurePortAvailable(port)
        true
    }.getOrDefault(false)

    private fun ensurePortAvailable(port: Int) {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            runCatching { socket.bind(InetSocketAddress("0.0.0.0", port)) }
                .getOrElse { error("端口 $port 已被其他应用占用") }
        }
    }

    private fun notification(pendingIntent: PendingIntent): Notification = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(
            when {
                deployed -> "LAN Board Game 已上线"
                selectedAddress.isBlank() -> "LAN Board Game 仅本机运行"
                else -> "LAN Board Game 等待部署"
            },
        )
        .setContentText(
            when {
                deployed -> "http://$selectedAddress:$currentPort"
                selectedAddress.isBlank() -> "http://127.0.0.1:$currentPort"
                else -> "打开应用确认局域网地址"
            },
        )
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()

    private fun updateNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(pending))
    }

    companion object {
        const val DEFAULT_PORT = 8787
        private const val PREFERENCES = "host-server-config"
        private const val KEY_PORT = "port"
        private const val KEY_ADDRESS = "address"
        private const val CHANNEL_ID = "lan-game-server"
        private const val NOTIFICATION_ID = 8787
        private const val TAG = "LanGame.Service"

        fun configuredPort(context: android.content.Context): Int =
            context.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getInt(KEY_PORT, DEFAULT_PORT)
                .takeIf(::validPort)
                ?: DEFAULT_PORT

        private fun validPort(port: Int) = port in 1024..65535
    }
}

internal fun serverBindAddress(deployed: Boolean): String = if (deployed) "0.0.0.0" else "127.0.0.1"

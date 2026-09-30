package com.lanboardgame.host.server

data class HostServerState(
    val selectedAddress: String,
    val port: Int,
    val deployed: Boolean,
)

data class DeploymentResult(
    val selectedAddress: String,
    val port: Int,
    val deployed: Boolean,
    val restarting: Boolean,
)

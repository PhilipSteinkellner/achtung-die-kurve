package com.example.achtungdiekurve.multiplayer

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Define a sealed class to represent various connection states
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val endpointId: String) : ConnectionState()
    object Advertising : ConnectionState()
    object Discovering : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    data class Status(val message: String) : ConnectionState()
}

class NearbyConnectionsManager(
    private val context: Context,
    private val serviceId: String,
    private val onGameDataReceived: (String) -> Unit,
    private val onConnected: (endpointId: String, isHost: Boolean) -> Unit,
    private val onDisconnected: () -> Unit
) {
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    var connectedEndpointId: String? = null
        private set

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    onGameDataReceived(String(bytes))
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Not used for this game, but could track payload progress
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            _connectionState.update { ConnectionState.Status("Connection initiated with: ${info.endpointName}") }
            connectionsClient.acceptConnection(endpointId, payloadCallback).addOnSuccessListener {
                _connectionState.update { ConnectionState.Status("Accepting connection from: ${info.endpointName}") }
            }.addOnFailureListener { e ->
                _connectionState.update { ConnectionState.Error("Failed to accept connection: ${e.message}") }
                stopAllEndpoints()
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpointId = endpointId
                    _connectionState.update { ConnectionState.Connected(endpointId) }
                    stopAdvertisingAndDiscovery()
                    onConnected(endpointId, _isHost) // Pass isHost
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    _connectionState.update { ConnectionState.Error("Connection rejected by other device.") }
                    stopAllEndpoints()
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    _connectionState.update { ConnectionState.Error("Connection error.") }
                    stopAllEndpoints()
                }

                else -> {
                    _connectionState.update { ConnectionState.Error("Connection failed: ${result.status.statusCode}") }
                    stopAllEndpoints()
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectionState.update { ConnectionState.Disconnected }
            connectedEndpointId = null
            onDisconnected()
            stopAllEndpoints() // Ensure all related operations are stopped
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _connectionState.update { ConnectionState.Status("Found host: ${info.endpointName}, requesting connection...") }
            connectionsClient.requestConnection(
                "Player Device", // TODO: Make configurable
                endpointId, connectionLifecycleCallback
            ).addOnSuccessListener {
                _connectionState.update { ConnectionState.Status("Requested connection to: ${info.endpointName}") }
            }.addOnFailureListener { e ->
                _connectionState.update { ConnectionState.Error("Failed to request connection: ${e.message}") }
                stopAllEndpoints()
            }
        }

        override fun onEndpointLost(endpointId: String) {
            if (connectedEndpointId == null) {
                _connectionState.update { ConnectionState.Status("Lost discovery of host endpoint") }
            }
        }
    }

    private var _isHost: Boolean = false // Internal flag to track if we initiated as host

    fun startHosting() {
        _isHost = true
        _connectionState.update { ConnectionState.Connecting }
        _connectionState.update { ConnectionState.Status("Starting to host...") }

        val advertisingOptions =
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()

        connectionsClient.startAdvertising(
            "Game Host", // TODO: Make configurable
            serviceId, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener {
            _connectionState.update { ConnectionState.Advertising }
            _connectionState.update { ConnectionState.Status("Waiting for players to join...") }
        }.addOnFailureListener { exception ->
            _connectionState.update { ConnectionState.Error("Failed to start hosting: ${exception.message}") }
            stopAllEndpoints()
        }
    }

    fun startDiscovery() {
        _isHost = false
        _connectionState.update { ConnectionState.Connecting }
        _connectionState.update { ConnectionState.Status("Searching for host...") }

        val discoveryOptions =
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()

        connectionsClient.startDiscovery(
            serviceId, endpointDiscoveryCallback, discoveryOptions
        ).addOnSuccessListener {
            _connectionState.update { ConnectionState.Discovering }
            _connectionState.update { ConnectionState.Status("Searching for games...") }
        }.addOnFailureListener { exception ->
            _connectionState.update { ConnectionState.Error("Failed to search: ${exception.message}") }
            stopAllEndpoints()
        }
    }

    fun sendGameData(data: String) {
        connectedEndpointId?.let { endpointId ->
            val payload = Payload.fromBytes(data.toByteArray())
            connectionsClient.sendPayload(endpointId, payload)
        }
    }

    fun stopAdvertisingAndDiscovery() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
    }

    fun stopAllEndpoints() {
        connectionsClient.stopAllEndpoints()
        _connectionState.update { ConnectionState.Disconnected }
        connectedEndpointId = null
        _isHost = false
    }

    fun disconnect() {
        connectedEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
        stopAllEndpoints()
    }
}
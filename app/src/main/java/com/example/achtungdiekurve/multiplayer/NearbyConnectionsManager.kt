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
    context: Context,
    private val serviceId: String,
    private val onGameDataReceived: (String, String) -> Unit,
    private val onConnected: (endpointId: String, endpointName: String, isHost: Boolean) -> Unit,
    private val onDisconnect: (endpointId: String) -> Unit,
    private val onSearching: () -> Unit,
) {
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val connectedEndpoints = mutableSetOf<String>()
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    onGameDataReceived(endpointId, String(bytes))
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Not used for this game, but could track payload progress
        }
    }

    private val endpointNames = mutableMapOf<String, String>()

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Store the endpointName so we can retrieve it later
            endpointNames[endpointId] = info.endpointName

            if (!_isHost) {
                _connectionState.update { ConnectionState.Status("Connection initiated with: ${info.endpointName}") }
            }
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    _connectionState.update { ConnectionState.Error("Failed to accept connection: ${e.message}") }
                    stopAllEndpoints()
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val endpointName = endpointNames[endpointId] ?: endpointId

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoints.add(endpointId)
                    if (!_isHost) {
                        _connectionState.update { ConnectionState.Status("Connected to $endpointName ($endpointId)") }
                        stopAdvertisingAndDiscovery()
                    }
                    onConnected(endpointId, endpointName, _isHost)
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    _connectionState.update { ConnectionState.Error("Connection rejected by $endpointName") }
                    stopAllEndpoints()
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    _connectionState.update { ConnectionState.Error("Connection error with $endpointName") }
                    stopAllEndpoints()
                }

                else -> {
                    _connectionState.update { ConnectionState.Error("Connection failed with $endpointName: ${result.status.statusCode}") }
                    stopAllEndpoints()
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val endpointName = endpointNames[endpointId] ?: "(unknown)"
            _connectionState.update { ConnectionState.Status("Disconnected from $endpointName") }
            connectedEndpoints.remove(endpointId)
            onDisconnect(endpointId)
            stopAllEndpoints()
        }
    }


    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        var nickname = ""
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _connectionState.update { ConnectionState.Status("Found host: ${info.endpointName}, requesting connection...") }
            connectionsClient.requestConnection(
                this.nickname, endpointId, connectionLifecycleCallback
            ).addOnSuccessListener {
                _connectionState.update { ConnectionState.Status("Requested connection to: ${info.endpointName}") }
            }.addOnFailureListener { e ->
                _connectionState.update { ConnectionState.Error("Failed to request connection: ${e.message}") }
                stopAllEndpoints()
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _connectionState.update { ConnectionState.Status("Lost discovery of host endpoint") }
        }
    }

    private var _isHost: Boolean = false // Internal flag to track if we initiated as host

    fun startHosting(nickname: String) {
        _isHost = true
        _connectionState.update { ConnectionState.Connecting }
        _connectionState.update { ConnectionState.Status("Starting to host...") }
        onSearching()

        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()

        connectionsClient.startAdvertising(
            nickname, serviceId, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener {
            _connectionState.update { ConnectionState.Advertising }
            _connectionState.update { ConnectionState.Status("Waiting for players to join...") }
        }.addOnFailureListener { exception ->
            _connectionState.update { ConnectionState.Error("Failed to start hosting: ${exception.message}") }
            stopAllEndpoints()
        }
    }

    fun startDiscovery(nickname: String) {
        _isHost = false
        _connectionState.update { ConnectionState.Connecting }
        _connectionState.update { ConnectionState.Status("Searching for host...") }
        onSearching()

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        this.endpointDiscoveryCallback.nickname = nickname
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
        val payload = Payload.fromBytes(data.toByteArray())
        for (endpointId in connectedEndpoints) {
            connectionsClient.sendPayload(endpointId, payload)
        }
    }

    fun sendGameDataToEndpoint(endpointId: String, data: String) {

        val payload = Payload.fromBytes(data.toByteArray())
        connectionsClient.sendPayload(endpointId, payload)

    }

    fun stopAdvertisingAndDiscovery() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
    }

    fun stopAllEndpoints() {
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        _connectionState.update { ConnectionState.Disconnected }
        _isHost = false
    }
}
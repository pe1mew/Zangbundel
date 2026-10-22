// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol

import kotlinx.coroutines.flow.Flow

/**
 * The radio as the protocol sees it: the four primitives of ble-protocol section 15.3 plus the
 * capability queries a leader needs (architecture A-07). `platform/ble` implements it on Android;
 * `core/testing` implements a simulated radio. Android's BLE quirks live behind this interface.
 */
interface Transport {
    /** What the local radio can do; decides whether this device can lead (FRD constraint 1). */
    val capabilities: Capabilities

    /** Replace the advertising payload of one advertising set; `setId` 0 is the beacon set. */
    suspend fun advertise(setId: Int, advertisement: ByteArray, scanResponse: ByteArray?)

    /** Stop one advertising set. */
    suspend fun stopAdvertising(setId: Int)

    /** Scan results while scanning: raw manufacturer-data payloads with the RSSI of each. */
    fun scanResults(): Flow<ScanResult>

    suspend fun startScan(mode: ScanMode)
    suspend fun stopScan()

    /** Open a GATT connection to the device a scan result came from. */
    suspend fun connect(address: DeviceAddress): Connection

    /** Server side: the leader's GATT server, publishing writes and reads from singers. */
    suspend fun startServer(handler: ServerHandler)
    suspend fun stopServer()

    data class Capabilities(
        val peripheralMode: Boolean,
        val multipleAdvertisingSets: Boolean,
        val extendedAdvertising: Boolean,
        val maxAdvertisingDataLength: Int,
    )

    enum class ScanMode { DISCOVERY, GATHERING, LOW_DUTY }

    data class ScanResult(val address: DeviceAddress, val payload: ByteArray, val rssi: Int)

    @JvmInline
    value class DeviceAddress(val value: String)

    /** One GATT client connection, used for registration, requests, control and repair. */
    interface Connection {
        val mtu: Int
        suspend fun read(characteristic: Int): ByteArray
        suspend fun write(characteristic: Int, value: ByteArray)
        fun notifications(characteristic: Int): Flow<ByteArray>
        suspend fun close()
    }

    /** The leader's view of one connected singer. */
    interface ServerHandler {
        fun onConnected(client: ClientHandle)
        fun onDisconnected(client: ClientHandle)
        /** A write to a characteristic (8.1); the handler returns the ATT status, 0 for success. */
        suspend fun onWrite(client: ClientHandle, characteristic: Int, value: ByteArray): Int
        suspend fun onRead(client: ClientHandle, characteristic: Int): ByteArray
    }

    interface ClientHandle {
        suspend fun notify(characteristic: Int, value: ByteArray)
        suspend fun disconnect()
    }
}

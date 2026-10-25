// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow

/**
 * The radio as the protocol sees it (architecture A-07, ble-protocol 15.3). `platform/ble`
 * implements it on Android; `core/testing` implements a simulated radio. Android's BLE quirks live
 * behind this interface.
 *
 * Advertising is expressed in the protocol's own sets (7.1), not in raw advertising bytes, because
 * Android builds advertisements from structured data: the **beacon** (6.2: connectable, the
 * service UUID in the advertisement, the beacon bytes as manufacturer data in the scan response),
 * the **legacy data sets** and the **extended data set** (one manufacturer-data structure each).
 * Every payload here is the manufacturer data after the company identifier `0xFFFF`.
 */
interface Transport {
    /** What the local radio can do; decides whether and how this device can lead (FRD constraint 1). */
    val capabilities: Capabilities

    /** Starts the beacon set, or replaces its scan response when it runs (6.2, 6.4: within 100 ms). */
    suspend fun setBeacon(beacon: ByteArray)

    /** Opens legacy data sets one by one until the stack refuses or [max] are open; returns how many (7.1). */
    suspend fun openLegacyDataSets(max: Int): Int

    /** Opens the extended data set when the radio supports it; false otherwise (7.1). */
    suspend fun openExtendedDataSet(): Boolean

    /** Replaces the frame a data set sends: legacy sets are 0 until the count opened, the extended set is [EXTENDED_SET]. */
    suspend fun setFrame(set: Int, frame: ByteArray)

    /** Stops every advertising set, the beacon included. */
    suspend fun stopAdvertising()

    /** Scan results while scanning. */
    fun scanResults(): Flow<ScanResult>

    suspend fun startScan(mode: ScanMode)
    suspend fun stopScan()

    /**
     * Open a GATT connection to the device a scan result came from and negotiate the largest MTU
     * (8.2). Throws [GattException] when the connection cannot be made (Android status 133, timeout).
     */
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

    /**
     * One received advertisement or scan response. [manufacturerData] is the payload after company
     * `0xFFFF` (beacon encoding A or a data frame), [localName] the local name (beacon encoding B),
     * [serviceUuid] whether the Zangbundel service UUID was advertised.
     */
    class ScanResult(
        val address: DeviceAddress,
        val manufacturerData: ByteArray?,
        val localName: String?,
        val serviceUuid: Boolean,
        val rssi: Int,
    )

    @JvmInline
    value class DeviceAddress(val value: String)

    /**
     * One GATT client connection, used for registration, requests, control and repair. Every call
     * throws [GattException] when the connection fails or the leader answers with an error status.
     */
    interface Connection {
        val mtu: Int
        suspend fun read(characteristic: Int): ByteArray
        /** Write with response; a refused write throws [GattException] with the leader's status (8.3). */
        suspend fun write(characteristic: Int, value: ByteArray)
        /**
         * Enables notifications and returns once the leader has them on (9.7: subscribe before writing
         * Transfer). The channel buffers every notification until read and closes with the connection.
         */
        suspend fun subscribe(characteristic: Int): ReceiveChannel<ByteArray>
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
        /** The MTU agreed with this client; DATA chunks are MTU − 6 bytes (9.7). */
        val mtu: Int
        /** Sends one notification and returns when the stack has sent it (15.1: pacing); throws [GattException] when the client is gone. */
        suspend fun notify(characteristic: Int, value: ByteArray)
        suspend fun disconnect()
    }

    /** A failed connection or GATT operation; [status] is the ATT or application status (8.3), or [CONNECTION_FAILED]. */
    class GattException(val status: Int, message: String) : Exception(message)

    companion object {
        const val EXTENDED_SET = -1
        /** [GattException.status] for a connection that could not be made or was lost. */
        const val CONNECTION_FAILED = -1
    }
}

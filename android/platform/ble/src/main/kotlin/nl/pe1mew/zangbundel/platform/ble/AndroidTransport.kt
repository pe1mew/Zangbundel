// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import kotlinx.coroutines.flow.Flow
import nl.pe1mew.zangbundel.core.protocol.Transport

/**
 * The protocol's Transport on Android BLE (architecture A-07). The Android rules of ble-protocol
 * section 15.1 live here and nowhere else: one scan per gathering, never restarted; notifications
 * serialised on `onNotificationSent`; status 133 expected when the leader is saturated; MTU first.
 * Skeleton: capability detection only; the radio paths are written with the first device tests.
 */
class AndroidTransport(context: Context, private val adapter: BluetoothAdapter) : Transport {

    override val capabilities: Transport.Capabilities = Transport.Capabilities(
        peripheralMode = adapter.bluetoothLeAdvertiser != null,
        multipleAdvertisingSets = adapter.isMultipleAdvertisementSupported,
        extendedAdvertising = adapter.isLeExtendedAdvertisingSupported,
        maxAdvertisingDataLength = adapter.leMaximumAdvertisingDataLength,
    )

    override suspend fun advertise(setId: Int, advertisement: ByteArray, scanResponse: ByteArray?) = TODO()
    override suspend fun stopAdvertising(setId: Int) = TODO()
    override fun scanResults(): Flow<Transport.ScanResult> = TODO()
    override suspend fun startScan(mode: Transport.ScanMode) = TODO()
    override suspend fun stopScan() = TODO()
    override suspend fun connect(address: Transport.DeviceAddress): Transport.Connection = TODO()
    override suspend fun startServer(handler: Transport.ServerHandler) = TODO()
    override suspend fun stopServer() = TODO()
}

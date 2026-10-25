// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.Transport
import java.util.UUID

/**
 * The protocol's Transport on Android BLE (architecture A-07, ble-protocol 15.1). The Android rules
 * live here and nowhere else: advertising sets opened until the stack refuses, frames replaced with
 * `setAdvertisingData`, the beacon connectable with the service UUID and never the device name, one
 * continuous scan per gathering with filters on the service UUID and the company identifier.
 *
 * GATT (connect, server) is not written yet.
 *
 * Callers hold the runtime permissions before calling: `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN` and
 * `BLUETOOTH_CONNECT` on Android 12 and later, location on Android 8 to 11 for scanning.
 */
@SuppressLint("MissingPermission")
class AndroidTransport(private val context: Context, private val adapter: BluetoothAdapter) : Transport {

    override val capabilities: Transport.Capabilities = Transport.Capabilities(
        peripheralMode = adapter.bluetoothLeAdvertiser != null,
        multipleAdvertisingSets = adapter.isMultipleAdvertisementSupported,
        extendedAdvertising = adapter.isLeExtendedAdvertisingSupported,
        maxAdvertisingDataLength = adapter.leMaximumAdvertisingDataLength,
    )

    private class SetHandle : AdvertisingSetCallback() {
        val started = CompletableDeferred<Int>()
        @Volatile var set: AdvertisingSet? = null
        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            set = advertisingSet
            started.complete(status)
        }
        val dataSet = AtomicInteger()
        val dataFailed = AtomicInteger()
        @Volatile var lastDataStatus: Int? = null
        override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
            if (status == ADVERTISE_SUCCESS) dataSet.incrementAndGet() else dataFailed.incrementAndGet()
            lastDataStatus = status
        }
    }

    /**
     * Whether legacy data sets are scannable, as ble-protocol 15.1 says. Off is a diagnostic: an
     * active scanner backs off on scan requests, and some stacks report a scannable advertisement
     * only with its scan response. Takes effect for sets opened afterwards.
     */
    @Volatile var scannableDataSets: Boolean = true

    /**
     * Whether the extended set uses the 2M secondary PHY where supported, as ble-protocol 15.1 says.
     * Off (1M) is a diagnostic for phones whose extended set is not heard (D-05 run 14). Takes effect
     * for an extended set opened afterwards.
     */
    @Volatile var extendedSecondaryPhy2M: Boolean = true

    /** Diagnostic: data updates of the extended set the stack confirmed, refused, and the last status. */
    fun extendedDataResults(): Triple<Int, Int, Int?>? =
        extendedSet?.let { Triple(it.dataSet.get(), it.dataFailed.get(), it.lastDataStatus) }

    private var beaconSet: SetHandle? = null
    private val legacySets = ArrayList<SetHandle>()
    private var extendedSet: SetHandle? = null
    private val advertiser get() = adapter.bluetoothLeAdvertiser ?: throw IllegalStateException("this phone cannot advertise")

    // ---- advertising ----

    override suspend fun setBeacon(beacon: ByteArray) {
        val running = beaconSet?.set
        if (running != null) {
            running.setScanResponseData(manufacturer(beacon))
            return
        }
        val advertisement = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        val handle = SetHandle()
        advertiser.startAdvertisingSet(beaconParameters(), advertisement, manufacturer(beacon), null, null, handle)
        val status = withTimeoutOrNull(START_TIMEOUT_MS) { handle.started.await() }
        check(status == AdvertisingSetCallback.ADVERTISE_SUCCESS) { "beacon set did not start (status $status)" }
        beaconSet = handle
    }

    override suspend fun openLegacyDataSets(max: Int): Int {
        while (legacySets.size < max) {
            val handle = SetHandle()
            advertiser.startAdvertisingSet(legacyParameters(), manufacturer(EMPTY_FRAME), if (scannableDataSets) EMPTY else null, null, null, handle)
            val status = withTimeoutOrNull(START_TIMEOUT_MS) { handle.started.await() }
            if (status != AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                advertiser.stopAdvertisingSet(handle)
                break
            }
            legacySets += handle
        }
        return legacySets.size
    }

    override suspend fun openExtendedDataSet(): Boolean {
        if (extendedSet != null) return true
        if (!capabilities.extendedAdvertising) return false
        val handle = SetHandle()
        advertiser.startAdvertisingSet(extendedParameters(), manufacturer(EMPTY_FRAME), null, null, null, handle)
        val status = withTimeoutOrNull(START_TIMEOUT_MS) { handle.started.await() }
        if (status != AdvertisingSetCallback.ADVERTISE_SUCCESS) {
            advertiser.stopAdvertisingSet(handle)
            return false
        }
        extendedSet = handle
        return true
    }

    override suspend fun setFrame(set: Int, frame: ByteArray) {
        val handle = if (set == Transport.EXTENDED_SET) extendedSet else legacySets.getOrNull(set)
        handle?.set?.setAdvertisingData(manufacturer(frame))
    }

    override suspend fun stopAdvertising() {
        (listOfNotNull(beaconSet, extendedSet) + legacySets).forEach { advertiser.stopAdvertisingSet(it) }
        beaconSet = null
        extendedSet = null
        legacySets.clear()
    }

    /** 6.2, 6.4: connectable, scannable, 100 ms, high TX power. */
    private fun beaconParameters() = AdvertisingSetParameters.Builder()
        .setLegacyMode(true).setConnectable(true).setScannable(true)
        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
        .build()

    /** 7.1, 15.1: legacy data sets non-connectable and scannable, 100 ms. */
    private fun legacyParameters() = AdvertisingSetParameters.Builder()
        .setLegacyMode(true).setConnectable(false).setScannable(scannableDataSets)
        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
        .build()

    /** 7.1, 15.1: extended, non-connectable, 1M primary, 2M secondary where supported. */
    private fun extendedParameters() = AdvertisingSetParameters.Builder()
        .setLegacyMode(false).setConnectable(false).setScannable(false)
        .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
        .setSecondaryPhy(if (extendedSecondaryPhy2M && adapter.isLe2MPhySupported) BluetoothDevice.PHY_LE_2M else BluetoothDevice.PHY_LE_1M)
        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
        .build()

    private fun manufacturer(payload: ByteArray): AdvertiseData = AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .setIncludeTxPowerLevel(false)
        .addManufacturerData(ProtocolConstants.COMPANY_ID, payload)
        .build()

    // ---- scanning ----

    private val results = MutableSharedFlow<Transport.ScanResult>(
        extraBufferCapacity = 4096,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) { emit(result) }
        override fun onBatchScanResults(batch: MutableList<ScanResult>) { batch.forEach(::emit) }
        override fun onScanFailed(errorCode: Int) { scanFailure = errorCode }
    }

    /**
     * Whether the scan filters run in the Bluetooth controller (15.1) or in the app. Off is a
     * diagnostic: some stacks report fewer results when filters are offloaded. Takes effect at the
     * next [startScan].
     */
    @Volatile var hardwareFilters: Boolean = true

    /** The last scan error code from the stack, for the diagnostics screen; null while none. */
    @Volatile var scanFailure: Int? = null
        private set

    private fun emit(r: ScanResult) {
        val record = r.scanRecord
        val manufacturer = record?.getManufacturerSpecificData(ProtocolConstants.COMPANY_ID)
        val service = record?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
        if (manufacturer == null && !service) return      // the app's own filter when the controller's is off
        results.tryEmit(
            Transport.ScanResult(
                address = Transport.DeviceAddress(r.device.address),
                manufacturerData = manufacturer,
                localName = record?.deviceName,
                serviceUuid = service,
                rssi = r.rssi,
            ),
        )
    }

    override fun scanResults(): Flow<Transport.ScanResult> = results

    /** One continuous scan; 15.1: never restarted (Android throttles more than 5 starts in 30 s). */
    override suspend fun startScan(mode: Transport.ScanMode) {
        val scanner = adapter.bluetoothLeScanner ?: throw IllegalStateException("Bluetooth is off")
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build(),
            ScanFilter.Builder().setManufacturerData(ProtocolConstants.COMPANY_ID, ByteArray(0)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(if (mode == Transport.ScanMode.LOW_DUTY) ScanSettings.SCAN_MODE_LOW_POWER else ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .apply { if (capabilities.extendedAdvertising) setLegacy(false) }
            .build()
        scanFailure = null
        // Without filters Android stops the scan while the screen is off (8.1 and later); diagnostics only.
        scanner.startScan(if (hardwareFilters) filters else emptyList(), settings, scanCallback)
    }

    override suspend fun stopScan() {
        adapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    // ---- GATT: not written yet ----

    override suspend fun connect(address: Transport.DeviceAddress): Transport.Connection = TODO("GATT client")
    override suspend fun startServer(handler: Transport.ServerHandler) = TODO("GATT server")
    override suspend fun stopServer() = TODO("GATT server")

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString(ProtocolConstants.uuidOf(ProtocolConstants.CHAR_SERVICE))
        private const val START_TIMEOUT_MS = 3_000L
        /** Placeholder until the first frame: not a frame magic, not a beacon version. */
        private val EMPTY_FRAME = byteArrayOf(0x7F)
        private val EMPTY = AdvertiseData.Builder().build()
    }
}

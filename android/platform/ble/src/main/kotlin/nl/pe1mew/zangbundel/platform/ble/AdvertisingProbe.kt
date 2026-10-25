// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * The radio check of test-plan F-01 (answers ble-protocol Q2): what this phone's controller can
 * advertise. It opens legacy, non-connectable advertising sets one at a time until the phone
 * refuses, measures how long an advertising-data update takes, and tries one extended set; when
 * the stack refuses the largest single-structure payload it searches for the largest it accepts.
 *
 * What goes on the air (assessment SP-23): manufacturer data under the protocol's company ID
 * with a fixed test marker, a set number and a counter. Never the device name, never the TX power,
 * never anything about the user. Legacy sets are scannable but not connectable, like the
 * protocol's data sets (ble-protocol section 7); the extended set is neither. Everything it started is stopped
 * before [run] returns, also when the caller is cancelled. It keeps nothing.
 *
 * Must be called from the main thread: the advertising callbacks arrive on the main looper.
 */
class AdvertisingProbe(private val context: Context, private val adapter: BluetoothAdapter) {

    data class Report(
        val sdkInt: Int,
        val bluetoothOn: Boolean,
        val peripheralMode: Boolean,
        val multipleAdvertisement: Boolean,
        val extendedAdvertising: Boolean,
        val maxAdvertisingDataLength: Int,
        val le2MPhy: Boolean,
        val leCodedPhy: Boolean,
        val periodicAdvertising: Boolean,
        val legacySetsOpened: Int = 0,
        val legacyLimit: String = "not measured",
        val updatesOk: Int = 0,
        val updateFailures: Int = 0,
        val updateMeanMs: Double? = null,
        val updateMaxMs: Double? = null,
        val extendedPayloadBytes: Int = 0,
        val extendedResult: String = "not measured",
        val extendedLargestOpened: Int? = null,
        val extendedLargestTotalMultiAd: Int? = null,
        val error: String? = null,
    ) {
        /** Rows for the screen and the shared text; the keys are fixed so results compare across phones. */
        fun rows(): List<Pair<String, String>> = listOf(
            "android_api" to sdkInt.toString(),
            "bluetooth_on" to bluetoothOn.toString(),
            "peripheral_mode" to peripheralMode.toString(),
            "multiple_advertisement" to multipleAdvertisement.toString(),
            "extended_advertising" to extendedAdvertising.toString(),
            "max_advertising_data_length" to maxAdvertisingDataLength.toString(),
            "le_2m_phy" to le2MPhy.toString(),
            "le_coded_phy" to leCodedPhy.toString(),
            "periodic_advertising" to periodicAdvertising.toString(),
            "legacy_sets_opened" to legacySetsOpened.toString(),
            "legacy_limit" to legacyLimit,
            "data_updates_ok" to "$updatesOk of ${updatesOk + updateFailures}",
            "data_update_mean_ms" to (updateMeanMs?.let { "%.1f".format(Locale.ROOT, it) } ?: "-"),
            "data_update_max_ms" to (updateMaxMs?.let { "%.1f".format(Locale.ROOT, it) } ?: "-"),
            "extended_set" to extendedResult,
            "extended_payload_bytes" to extendedPayloadBytes.toString(),
            "extended_largest_payload_opened" to (extendedLargestOpened?.toString() ?: "-"),
            "extended_largest_total_multi_ad" to (extendedLargestTotalMultiAd?.toString() ?: "-"),
        ) + (error?.let { listOf("error" to it) } ?: emptyList())

        fun toText(): String = rows().joinToString("\n") { (k, v) -> "$k: $v" }
    }

    fun hasAdvertisePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

    // Every advertiser call below is reached only after hasAdvertisePermission() returned true.
    @SuppressLint("MissingPermission")
    suspend fun run(maxSets: Int = MAX_SETS, updates: Int = UPDATES): Report {
        var report = Report(
            sdkInt = Build.VERSION.SDK_INT,
            bluetoothOn = adapter.isEnabled,
            peripheralMode = adapter.bluetoothLeAdvertiser != null,
            multipleAdvertisement = adapter.isMultipleAdvertisementSupported,
            extendedAdvertising = adapter.isLeExtendedAdvertisingSupported,
            maxAdvertisingDataLength = adapter.leMaximumAdvertisingDataLength,
            le2MPhy = adapter.isLe2MPhySupported,
            leCodedPhy = adapter.isLeCodedPhySupported,
            periodicAdvertising = adapter.isLePeriodicAdvertisingSupported,
        )
        if (!report.bluetoothOn) return report.copy(error = "bluetooth off")
        if (!hasAdvertisePermission()) return report.copy(error = "no advertise permission")
        val advertiser = adapter.bluetoothLeAdvertiser ?: return report.copy(error = "no advertiser")

        report = measureLegacy(advertiser, report, maxSets, updates)
        report = if (report.extendedAdvertising) measureExtended(advertiser, report)
        else report.copy(extendedResult = "not supported")
        return report
    }

    @SuppressLint("MissingPermission")
    private suspend fun measureLegacy(advertiser: BluetoothLeAdvertiser, start: Report, maxSets: Int, updates: Int): Report {
        val opened = mutableListOf<SetHandle>()
        var report = start
        try {
            var limit = "none up to $maxSets"
            for (i in 0 until maxSets) {
                val handle = SetHandle()
                advertiser.startAdvertisingSet(
                    legacyParameters(), testData(i, 0, LEGACY_PAYLOAD_BYTES), testData(i, 0, LEGACY_PAYLOAD_BYTES), null, null, handle,
                )
                val status = withTimeoutOrNull(TIMEOUT_MS) { handle.started.await() }
                if (status == null) {
                    advertiser.stopAdvertisingSet(handle)
                    limit = "set ${i + 1}: no answer within $TIMEOUT_MS ms"
                    break
                }
                if (status != AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    limit = "set ${i + 1}: ${statusName(status)}"
                    break
                }
                opened += handle
            }
            report = report.copy(legacySetsOpened = opened.size, legacyLimit = limit)

            val first = opened.firstOrNull()
            val set = first?.set
            if (first != null && set != null) {
                val times = mutableListOf<Double>()
                var failures = 0
                for (n in 1..updates) {
                    val done = CompletableDeferred<Int>()
                    first.dataSet = done
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    set.setAdvertisingData(testData(0, n, LEGACY_PAYLOAD_BYTES))
                    val status = withTimeoutOrNull(TIMEOUT_MS) { done.await() }
                    if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                        times += (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                    } else {
                        failures++
                    }
                }
                report = report.copy(
                    updatesOk = times.size,
                    updateFailures = failures,
                    updateMeanMs = times.takeIf { it.isNotEmpty() }?.average(),
                    updateMaxMs = times.maxOrNull(),
                )
            }
        } finally {
            withContext(NonCancellable) {
                opened.forEach { advertiser.stopAdvertisingSet(it) }
                // Wait until the controller has released them, so the extended attempts start clean.
                opened.forEach { withTimeoutOrNull(TIMEOUT_MS) { it.stopped.await() } }
            }
        }
        return report
    }

    @SuppressLint("MissingPermission")
    private suspend fun measureExtended(advertiser: BluetoothLeAdvertiser, start: Report): Report {
        // One AD structure holds at most 254 bytes after its length byte: 1 type + 2 company ID + payload.
        val full = minOf(start.maxAdvertisingDataLength - AD_OVERHEAD, MAX_SINGLE_AD_PAYLOAD)
        if (full < 1) return start.copy(extendedResult = "max length too small")
        val first = tryExtended(advertiser, full)
        var report = start.copy(extendedPayloadBytes = full, extendedResult = first?.let(::statusName) ?: "no answer within $TIMEOUT_MS ms")
        if (first == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
            report = report.copy(extendedLargestOpened = full)
            return report.copy(extendedLargestTotalMultiAd = largestMultiAdTotal(advertiser, report))
        }

        // Refused: binary search for the largest single-structure payload the stack accepts.
        var lo = 0          // largest size known to open (0 = none yet)
        var hi = full       // smallest size known to fail
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (tryExtended(advertiser, mid) == AdvertisingSetCallback.ADVERTISE_SUCCESS) lo = mid else hi = mid
        }
        report = report.copy(extendedLargestOpened = lo)
        return report.copy(extendedLargestTotalMultiAd = largestMultiAdTotal(advertiser, report))
    }

    /**
     * Whether the advertisement as a whole can exceed one structure: the data is spread over a
     * manufacturer-data structure and service-data structures under the app's own UUID base, and the
     * largest total advertising-data length that opens is found by binary search. Returns 0 when
     * nothing larger than one structure opens.
     */
    private suspend fun largestMultiAdTotal(advertiser: BluetoothLeAdvertiser, report: Report): Int {
        val ceiling = minOf(report.maxAdvertisingDataLength, MULTI_AD_CEILING)
        val floor = (report.extendedLargestOpened ?: 0) + AD_OVERHEAD
        if (ceiling <= floor) return 0
        var lo = 0
        var hi = ceiling + 1
        // First probe just above one structure; if that fails, more structures do not help.
        if (tryExtended(advertiser, multiAdData(floor + 1)) != AdvertisingSetCallback.ADVERTISE_SUCCESS) return 0
        lo = floor + 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (tryExtended(advertiser, multiAdData(mid)) == AdvertisingSetCallback.ADVERTISE_SUCCESS) lo = mid else hi = mid
        }
        return lo
    }

    /** Advertising data of exactly [total] bytes: one manufacturer structure, then service-data structures. */
    private fun multiAdData(total: Int): AdvertiseData {
        val builder = AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
        val manufacturerAd = minOf(total, MULTI_AD_STRUCTURE)
        builder.addManufacturerData(COMPANY_ID, ByteArray(manufacturerAd - AD_OVERHEAD).also { it[0] = TEST_MARKER })
        var remaining = total - manufacturerAd
        var index = 0
        while (remaining > 0) {
            // A 128-bit service-data structure: 1 length + 1 type + 16 UUID + data.
            var size = minOf(remaining, MULTI_AD_STRUCTURE)
            if (size < SERVICE_DATA_OVERHEAD + 1) size = SERVICE_DATA_OVERHEAD + 1 // last one rounds up by a few bytes
            val uuid = ParcelUuid(UUID.fromString("7e2b%04x-9f1c-4a7d-b2d4-5a4e47424c45".format(SERVICE_UUID_FIRST + index)))
            builder.addServiceData(uuid, ByteArray(size - SERVICE_DATA_OVERHEAD).also { it[0] = TEST_MARKER })
            remaining -= size
            index++
        }
        return builder.build()
    }

    /** Opens one extended set with [payload] bytes, stops it again, and returns the start status (null on timeout). */
    @SuppressLint("MissingPermission")
    private suspend fun tryExtended(advertiser: BluetoothLeAdvertiser, payload: Int): Int? =
        tryExtended(advertiser, testData(0xE0, 0, payload))

    @SuppressLint("MissingPermission")
    private suspend fun tryExtended(advertiser: BluetoothLeAdvertiser, data: AdvertiseData): Int? {
        val handle = SetHandle()
        try {
            advertiser.startAdvertisingSet(extendedParameters(), data, null, null, null, handle)
            return withTimeoutOrNull(TIMEOUT_MS) { handle.started.await() }
        } finally {
            withContext(NonCancellable) {
                advertiser.stopAdvertisingSet(handle)
                // Let the controller release the set before the next attempt.
                if (handle.started.isCompleted) withTimeoutOrNull(TIMEOUT_MS) { handle.stopped.await() }
            }
        }
    }

    private fun legacyParameters(): AdvertisingSetParameters = AdvertisingSetParameters.Builder()
        .setLegacyMode(true)
        .setConnectable(false)
        .setScannable(true) // as the protocol's legacy data sets, so the count is comparable
        .setInterval(AdvertisingSetParameters.INTERVAL_LOW) // 100 ms, the carousel's interval (ble-protocol section 7)
        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
        .build()

    private fun extendedParameters(): AdvertisingSetParameters = AdvertisingSetParameters.Builder()
        .setLegacyMode(false)
        .setConnectable(false)
        .setScannable(false)
        .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
        .setSecondaryPhy(BluetoothDevice.PHY_LE_1M)
        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
        .build()

    /** Marker byte, set number, counter, zero padding. The marker is not a protocol frame magic (0xD1, 0xD2). */
    private fun testData(setNumber: Int, counter: Int, size: Int): AdvertiseData {
        val bytes = ByteArray(size)
        bytes[0] = TEST_MARKER
        if (size > 1) bytes[1] = setNumber.toByte()
        if (size > 2) bytes[2] = counter.toByte()
        return AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(COMPANY_ID, bytes)
            .build()
    }

    private class SetHandle : AdvertisingSetCallback() {
        val started = CompletableDeferred<Int>()
        var set: AdvertisingSet? = null
        var dataSet: CompletableDeferred<Int>? = null
        val stopped = CompletableDeferred<Unit>()

        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            set = advertisingSet
            started.complete(status)
        }

        override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
            dataSet?.complete(status)
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            stopped.complete(Unit)
        }
    }

    companion object {
        /** High enough to find the real limit of current phones; the protocol uses up to five data sets. */
        const val MAX_SETS = 20
        const val UPDATES = 20
        const val TIMEOUT_MS = 3_000L
        /** ble-protocol 6.2: 0xFFFF until a company identifier is assigned (Q1). */
        const val COMPANY_ID = 0xFFFF
        const val TEST_MARKER: Byte = 0x7F
        /** 31 bytes of legacy advertising data minus the AD header (1 length + 1 type) and the company ID (2). */
        const val LEGACY_PAYLOAD_BYTES = 27
        const val AD_OVERHEAD = 4
        const val MAX_SINGLE_AD_PAYLOAD = 251
        /** Structure size used when spreading data over several structures; well under the 255-byte maximum. */
        const val MULTI_AD_STRUCTURE = 200
        const val SERVICE_DATA_OVERHEAD = 18
        /** Highest total tried: the protocol's extended frame is at most 1000 bytes (ble-protocol section 7). */
        const val MULTI_AD_CEILING = 1100
        /** Service-data UUIDs 7e2b0010… onwards: the app's own base (ble-protocol 8.1), clear of its characteristics. */
        const val SERVICE_UUID_FIRST = 0x0010

        fun statusName(status: Int): String = when (status) {
            AdvertisingSetCallback.ADVERTISE_SUCCESS -> "success"
            AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
            AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
            AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
            AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
            AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported"
            else -> "status $status"
        }
    }
}

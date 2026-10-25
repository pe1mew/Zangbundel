// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import nl.pe1mew.zangbundel.core.protocol.Transport

/**
 * Simulated radio for protocol tests (architecture A-07, section 10): frame loss, scan duty cycle,
 * connection-slot limit and latency are configurable; one instance per virtual device shares a
 * [SimulatedAir] with the others. Skeleton: the air model is filled in with the simulation
 * scenarios of the test plan (S-01 to S-10).
 */
class SimulatedTransport(
    private val air: SimulatedAir,
    override val capabilities: Transport.Capabilities = Transport.Capabilities(
        peripheralMode = true,
        multipleAdvertisingSets = true,
        extendedAdvertising = false,
        maxAdvertisingDataLength = 31,
    ),
) : Transport {
    private val results = MutableSharedFlow<Transport.ScanResult>(extraBufferCapacity = 1024)

    override suspend fun setBeacon(beacon: ByteArray) = air.setBeacon(this, beacon)
    override suspend fun openLegacyDataSets(max: Int): Int = air.openLegacyDataSets(this, max)
    override suspend fun openExtendedDataSet(): Boolean = capabilities.extendedAdvertising && air.openExtendedDataSet(this)
    override suspend fun setFrame(set: Int, frame: ByteArray) = air.setFrame(this, set, frame)
    override suspend fun stopAdvertising() = air.stopAdvertising(this)
    override fun scanResults(): Flow<Transport.ScanResult> = results
    override suspend fun startScan(mode: Transport.ScanMode) = air.startScan(this, results)
    override suspend fun stopScan() = air.stopScan(this)
    override suspend fun connect(address: Transport.DeviceAddress): Transport.Connection = air.connect(this, address)
    override suspend fun startServer(handler: Transport.ServerHandler) = air.startServer(this, handler)
    override suspend fun stopServer() = air.stopServer(this)
}

/** The shared medium between simulated transports. Loss, duty cycle, slots and latency are its knobs. */
class SimulatedAir(
    val frameLoss: Double = 0.0,
    val connectionSlots: Int = 6,
    val latencyMillis: Long = 0L,
) {
    suspend fun setBeacon(from: SimulatedTransport, beacon: ByteArray) { TODO("written with the simulation scenarios") }
    suspend fun openLegacyDataSets(from: SimulatedTransport, max: Int): Int { TODO() }
    suspend fun openExtendedDataSet(from: SimulatedTransport): Boolean { TODO() }
    suspend fun setFrame(from: SimulatedTransport, set: Int, frame: ByteArray) { TODO() }
    suspend fun stopAdvertising(from: SimulatedTransport) { TODO() }
    suspend fun startScan(who: SimulatedTransport, sink: MutableSharedFlow<Transport.ScanResult>) { TODO() }
    suspend fun stopScan(who: SimulatedTransport) { TODO() }
    suspend fun connect(from: SimulatedTransport, address: Transport.DeviceAddress): Transport.Connection { TODO() }
    suspend fun startServer(who: SimulatedTransport, handler: Transport.ServerHandler) { TODO() }
    suspend fun stopServer(who: SimulatedTransport) { TODO() }
}

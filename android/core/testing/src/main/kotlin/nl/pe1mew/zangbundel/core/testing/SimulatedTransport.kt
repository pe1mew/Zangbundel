// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import nl.pe1mew.zangbundel.core.protocol.Transport

/**
 * Simulated radio for protocol tests (architecture A-07, section 10): frame loss, scan duty cycle,
 * connection-slot limit and latency are configurable; one instance per virtual device shares a
 * [SimulatedAir] with the others. Skeleton: the air model is filled in with the first protocol tests.
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

    override suspend fun advertise(setId: Int, advertisement: ByteArray, scanResponse: ByteArray?) {
        air.advertise(this, setId, advertisement, scanResponse)
    }

    override suspend fun stopAdvertising(setId: Int) = air.stopAdvertising(this, setId)
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
    suspend fun advertise(from: SimulatedTransport, setId: Int, advertisement: ByteArray, scanResponse: ByteArray?) {
        TODO("delivered to scanning transports with loss and duty cycle; written with the carousel tests")
    }
    suspend fun stopAdvertising(from: SimulatedTransport, setId: Int) { TODO() }
    suspend fun startScan(who: SimulatedTransport, sink: MutableSharedFlow<Transport.ScanResult>) { TODO() }
    suspend fun stopScan(who: SimulatedTransport) { TODO() }
    suspend fun connect(from: SimulatedTransport, address: Transport.DeviceAddress): Transport.Connection { TODO() }
    suspend fun startServer(who: SimulatedTransport, handler: Transport.ServerHandler) { TODO() }
    suspend fun stopServer(who: SimulatedTransport) { TODO() }
}

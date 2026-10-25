// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.core.protocol.Transport
import kotlin.random.Random

/**
 * Simulated radio for protocol tests (architecture A-07, ble-protocol 15.3): one instance per
 * virtual device, all sharing a [SimulatedAir]. [reception] overrides the air's loss for what this
 * device hears, so one test can mix a phone that hears 13 % with one that hears everything;
 * [extendedReception] does the same for extended advertisements alone (D-05 runs 12–16: a leader
 * whose extended set is hardly heard).
 */
class SimulatedTransport(
    private val air: SimulatedAir,
    override val capabilities: Transport.Capabilities = DEFAULT_CAPABILITIES,
    var reception: Double? = null,
    var extendedReception: Double? = null,
) : Transport {
    companion object {
        /** A legacy-only phone. */
        val DEFAULT_CAPABILITIES = Transport.Capabilities(
            peripheralMode = true,
            multipleAdvertisingSets = true,
            extendedAdvertising = false,
            maxAdvertisingDataLength = 31,
        )
    }

    val address: Transport.DeviceAddress = air.register(this)
    internal val results = MutableSharedFlow<Transport.ScanResult>(extraBufferCapacity = 4096)

    override suspend fun setBeacon(beacon: ByteArray) = air.setBeacon(this, beacon)
    override suspend fun openLegacyDataSets(max: Int): Int = air.openLegacyDataSets(this, max)
    override suspend fun openExtendedDataSet(): Boolean = capabilities.extendedAdvertising && air.openExtendedDataSet(this)
    override suspend fun setFrame(set: Int, frame: ByteArray) = air.setFrame(this, set, frame)
    override suspend fun stopAdvertising() = air.stopAdvertising(this)
    override fun scanResults(): Flow<Transport.ScanResult> = results
    override suspend fun startScan(mode: Transport.ScanMode) = air.startScan(this)
    override suspend fun stopScan() = air.stopScan(this)
    override suspend fun connect(address: Transport.DeviceAddress): Transport.Connection = air.connect(this, address)
    override suspend fun startServer(handler: Transport.ServerHandler) = air.startServer(this, handler)
    override suspend fun stopServer() = air.stopServer(this)
}

/**
 * The shared medium between simulated transports, in virtual time when [scope] runs under
 * `runTest`. Every open advertising set sends its current payload every [intervalMs]; each scanning
 * device hears it with probability 1 − loss. GATT connections go straight to the server's handler
 * after [latencyMs]; at most [connectionSlots] are open per server, the stack's own limit, beyond
 * which a connection fails as Android's status 133 does. Notifications take [notificationMs] each.
 */
class SimulatedAir(
    private val scope: CoroutineScope,
    val frameLoss: Double = 0.0,
    val connectionSlots: Int = 7,
    val latencyMs: Long = 20L,
    val notificationMs: Long = 8L,
    val mtu: Int = 185,
    val intervalMs: Long = 100L,
    private val random: Random = Random(20261025),
) {
    private class Sets {
        var beacon: ByteArray? = null
        val legacy = ArrayList<ByteArray>()
        var extended: ByteArray? = null
        var loop: Job? = null
    }

    private val devices = ArrayList<SimulatedTransport>()
    private val sets = HashMap<SimulatedTransport, Sets>()
    private val scanning = HashSet<SimulatedTransport>()
    private val servers = HashMap<SimulatedTransport, Transport.ServerHandler>()
    private val open = HashMap<SimulatedTransport, Int>()

    /** Advertising sets one device may open, beacon excluded (ble-protocol 7.1: at most 3 legacy). */
    var legacySetsPerDevice: Int = 3

    internal fun register(t: SimulatedTransport): Transport.DeviceAddress {
        devices += t
        return Transport.DeviceAddress("sim-${devices.size}")
    }

    private fun byAddress(a: Transport.DeviceAddress) = devices.firstOrNull { it.address == a }

    // ---- advertising ----

    fun setBeacon(from: SimulatedTransport, beacon: ByteArray) { setsOf(from).beacon = beacon.copyOf() }

    fun openLegacyDataSets(from: SimulatedTransport, max: Int): Int {
        val s = setsOf(from)
        while (s.legacy.size < minOf(max, legacySetsPerDevice)) s.legacy += ByteArray(0)
        return s.legacy.size
    }

    fun openExtendedDataSet(from: SimulatedTransport): Boolean { setsOf(from).extended = ByteArray(0); return true }

    fun setFrame(from: SimulatedTransport, set: Int, frame: ByteArray) {
        val s = setsOf(from)
        if (set == Transport.EXTENDED_SET) { if (s.extended != null) s.extended = frame.copyOf() }
        else if (set in s.legacy.indices) s.legacy[set] = frame.copyOf()
    }

    fun stopAdvertising(from: SimulatedTransport) {
        sets.remove(from)?.loop?.cancel()
    }

    private fun setsOf(t: SimulatedTransport): Sets = sets.getOrPut(t) {
        Sets().also { s -> s.loop = scope.launch { while (isActive) { delay(intervalMs); send(t, s) } } }
    }

    private fun send(from: SimulatedTransport, s: Sets) {
        for (to in scanning) {
            if (to === from) continue
            val hears = 1.0 - (to.reception?.let { 1.0 - it } ?: frameLoss)
            fun deliver(payload: ByteArray?, beacon: Boolean, p: Double = hears) {
                if (payload == null || payload.isEmpty() || random.nextDouble() >= p) return
                to.results.tryEmit(Transport.ScanResult(from.address, payload.copyOf(), null, beacon, -50))
            }
            deliver(s.beacon, true)
            s.legacy.forEach { deliver(it, false) }
            if (to.capabilities.extendedAdvertising) deliver(s.extended, false, to.extendedReception ?: hears)
        }
    }

    fun startScan(who: SimulatedTransport) { scanning += who }
    fun stopScan(who: SimulatedTransport) { scanning -= who }

    // ---- GATT ----

    fun startServer(who: SimulatedTransport, handler: Transport.ServerHandler) { servers[who] = handler }
    fun stopServer(who: SimulatedTransport) { servers.remove(who) }

    suspend fun connect(from: SimulatedTransport, address: Transport.DeviceAddress): Transport.Connection {
        delay(latencyMs)
        val server = byAddress(address) ?: throw Transport.GattException(Transport.CONNECTION_FAILED, "no such device")
        val handler = servers[server] ?: throw Transport.GattException(Transport.CONNECTION_FAILED, "no GATT server")
        if ((open[server] ?: 0) >= connectionSlots) throw Transport.GattException(Transport.CONNECTION_FAILED, "status 133: no slot")
        open[server] = (open[server] ?: 0) + 1
        val link = Link(server, handler)
        handler.onConnected(link.client)
        return link.connection
    }

    private inner class Link(val server: SimulatedTransport, val handler: Transport.ServerHandler) {
        var closed = false
        val channels = HashMap<Int, Channel<ByteArray>>()

        fun drop() {
            if (closed) return
            closed = true
            open[server] = (open[server] ?: 1) - 1
            channels.values.forEach { it.close() }
            handler.onDisconnected(client)
        }

        fun check() { if (closed) throw Transport.GattException(Transport.CONNECTION_FAILED, "disconnected") }

        val client = object : Transport.ClientHandle {
            override val mtu: Int get() = this@SimulatedAir.mtu
            override suspend fun notify(characteristic: Int, value: ByteArray) {
                delay(notificationMs)
                check()
                channels[characteristic]?.send(value.copyOf())
            }
            override suspend fun disconnect() = drop()
        }

        val connection = object : Transport.Connection {
            override val mtu: Int get() = this@SimulatedAir.mtu
            override suspend fun read(characteristic: Int): ByteArray {
                delay(latencyMs); check()
                return handler.onRead(client, characteristic)
            }
            override suspend fun write(characteristic: Int, value: ByteArray) {
                delay(latencyMs); check()
                val status = handler.onWrite(client, characteristic, value)
                if (status != 0) throw Transport.GattException(status, "write refused")
            }
            override suspend fun subscribe(characteristic: Int): ReceiveChannel<ByteArray> {
                delay(latencyMs); check()
                return channels.getOrPut(characteristic) { Channel(Channel.UNLIMITED) }
            }
            override suspend fun close() = drop()
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.platform.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import nl.pe1mew.zangbundel.core.protocol.ProtocolConstants
import nl.pe1mew.zangbundel.core.protocol.Transport
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private fun uuid(characteristic: Int): UUID = UUID.fromString(ProtocolConstants.uuidOf(characteristic))
private fun characteristicOf(u: UUID): Int? =
    CHARACTERISTICS.firstOrNull { uuid(it) == u }

private val CHARACTERISTICS = listOf(
    ProtocolConstants.CHAR_INFO, ProtocolConstants.CHAR_JOIN, ProtocolConstants.CHAR_STATE, ProtocolConstants.CHAR_TRANSFER,
    ProtocolConstants.CHAR_DATA, ProtocolConstants.CHAR_REQUEST, ProtocolConstants.CHAR_CONTROL,
)

/**
 * The leader's GATT server (ble-protocol 8.1, 15.1): the Zangbundel service with every
 * characteristic of 8.1, requests handed to a [Transport.ServerHandler]. Long reads are served by
 * offset from the value read at offset 0; long writes arrive as prepared writes and reach the
 * handler once, on execute. Notifications are paced: each waits for `onNotificationSent`.
 */
@SuppressLint("MissingPermission")
internal class AndroidGattServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val handler: Transport.ServerHandler,
) {
    private var server: BluetoothGattServer? = null
    private val clients = ConcurrentHashMap<String, Client>()
    private val added = CompletableDeferred<Int>()

    private inner class Client(val device: BluetoothDevice) : Transport.ClientHandle {
        @Volatile override var mtu: Int = MIN_MTU
        val sending = Mutex()
        @Volatile var sent: CompletableDeferred<Int>? = null
        val reads = HashMap<Int, ByteArray>()
        val prepared = HashMap<Int, ByteArrayOutputStream>()
        @Volatile var gone = false

        override suspend fun notify(characteristic: Int, value: ByteArray) = sending.withLock {
            if (gone) throw Transport.GattException(Transport.CONNECTION_FAILED, "client gone")
            val s = server ?: throw Transport.GattException(Transport.CONNECTION_FAILED, "server stopped")
            val ch = s.getService(AndroidTransport.SERVICE_UUID)?.getCharacteristic(uuid(characteristic))
                ?: throw Transport.GattException(ProtocolConstants.ERR_MALFORMED, "no such characteristic")
            val done = CompletableDeferred<Int>()
            sent = done
            val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                s.notifyCharacteristicChanged(device, ch, false, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = value
                @Suppress("DEPRECATION")
                s.notifyCharacteristicChanged(device, ch, false)
            }
            if (!queued) throw Transport.GattException(Transport.CONNECTION_FAILED, "notification not queued")
            val status = withTimeoutOrNull(NOTIFY_TIMEOUT_MS) { done.await() }
                ?: throw Transport.GattException(Transport.CONNECTION_FAILED, "notification not sent")
            if (status != BluetoothGatt.GATT_SUCCESS) throw Transport.GattException(Transport.CONNECTION_FAILED, "notification status $status")
        }

        override suspend fun disconnect() { server?.cancelConnection(device) }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) { added.complete(status) }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val key = device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val c = Client(device)
                if (clients.putIfAbsent(key, c) == null) handler.onConnected(c)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clients.remove(key)?.let { c ->
                    c.gone = true
                    c.sent?.complete(BluetoothGatt.GATT_FAILURE)
                    handler.onDisconnected(c)
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) { clients[device.address]?.mtu = mtu }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) { clients[device.address]?.sent?.complete(status) }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val c = clients[device.address]
            val which = characteristicOf(characteristic.uuid)
            if (c == null || which == null) { respond(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null); return }
            scope.launch {
                // A long read comes back with growing offsets; serve them all from the value read at 0.
                val value = if (offset == 0) handler.onRead(c, which).also { synchronized(c.reads) { c.reads[which] = it } }
                else synchronized(c.reads) { c.reads[which] } ?: ByteArray(0)
                if (offset > value.size) respond(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                else respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            val c = clients[device.address]
            val which = characteristicOf(characteristic.uuid)
            val bytes = value ?: ByteArray(0)
            if (c == null || which == null) { if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, 0, null); return }
            if (preparedWrite) {
                val buffer = synchronized(c.prepared) { c.prepared.getOrPut(which) { ByteArrayOutputStream() } }
                val ok = offset == buffer.size() && buffer.size() + bytes.size <= MAX_VALUE
                if (ok) buffer.write(bytes)
                if (responseNeeded) respond(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET, offset, bytes)
                return
            }
            scope.launch {
                val status = handler.onWrite(c, which, bytes)
                if (responseNeeded) respond(device, requestId, status, 0, null)
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            val c = clients[device.address] ?: run { respond(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null); return }
            val pending = synchronized(c.prepared) { c.prepared.toMap().also { c.prepared.clear() } }
            if (!execute || pending.isEmpty()) { respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null); return }
            scope.launch {
                var status = BluetoothGatt.GATT_SUCCESS
                for ((which, buffer) in pending) {
                    status = handler.onWrite(c, which, buffer.toByteArray())
                    if (status != BluetoothGatt.GATT_SUCCESS) break
                }
                respond(device, requestId, status, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        }
    }

    private fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray?) {
        server?.sendResponse(device, requestId, status, offset, value)
    }

    /** Opens the server and adds the service; throws [IllegalStateException] when the stack refuses. */
    suspend fun start() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val s = manager.openGattServer(context, callback) ?: throw IllegalStateException("GATT server refused")
        server = s
        val service = BluetoothGattService(AndroidTransport.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        fun add(which: Int, properties: Int, permissions: Int, notify: Boolean = false) {
            val ch = BluetoothGattCharacteristic(uuid(which), properties, permissions)
            if (notify) ch.addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
            service.addCharacteristic(ch)
        }
        val read = BluetoothGattCharacteristic.PROPERTY_READ
        val write = BluetoothGattCharacteristic.PROPERTY_WRITE
        val notify = BluetoothGattCharacteristic.PROPERTY_NOTIFY
        val pr = BluetoothGattCharacteristic.PERMISSION_READ
        val pw = BluetoothGattCharacteristic.PERMISSION_WRITE
        add(ProtocolConstants.CHAR_INFO, read, pr)
        add(ProtocolConstants.CHAR_JOIN, write or read, pr or pw)
        add(ProtocolConstants.CHAR_STATE, read or notify, pr, notify = true)
        add(ProtocolConstants.CHAR_TRANSFER, write, pw)
        add(ProtocolConstants.CHAR_DATA, notify, pr, notify = true)
        add(ProtocolConstants.CHAR_REQUEST, write, pw)
        add(ProtocolConstants.CHAR_CONTROL, write, pw)
        check(s.addService(service)) { "GATT service refused" }
        val status = withTimeoutOrNull(START_TIMEOUT_MS) { added.await() }
        check(status == BluetoothGatt.GATT_SUCCESS) { "GATT service not added (status $status)" }
    }

    fun stop() {
        clients.values.forEach { it.gone = true; it.sent?.complete(BluetoothGatt.GATT_FAILURE) }
        clients.clear()
        server?.close()
        server = null
    }

    companion object {
        const val MIN_MTU = 23
        const val MAX_VALUE = 512
        const val NOTIFY_TIMEOUT_MS = 2_000L
        const val START_TIMEOUT_MS = 3_000L
    }
}

/**
 * One GATT client connection (ble-protocol 8.2, 15.1): connect over LE, ask for the largest MTU,
 * discover the service, raise the connection priority. Android allows one operation at a time, so
 * every operation holds [ops] until its callback has answered.
 */
@SuppressLint("MissingPermission")
internal class AndroidGattConnection private constructor(private val gatt: BluetoothGatt, private val link: Link) : Transport.Connection {

    /** The callback's side, created before the connection so no event is lost. */
    class Link : BluetoothGattCallback() {
        val connected = CompletableDeferred<Int>()
        val services = CompletableDeferred<Int>()
        val mtu = CompletableDeferred<Int>()
        @Volatile var op: CompletableDeferred<Pair<Int, ByteArray?>>? = null
        val channels = ConcurrentHashMap<UUID, Channel<ByteArray>>()
        @Volatile var closed = false

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(status)
            } else {
                closed = true
                connected.complete(if (status == BluetoothGatt.GATT_SUCCESS) BluetoothGatt.GATT_FAILURE else status)
                services.complete(BluetoothGatt.GATT_FAILURE)
                mtu.complete(MIN_MTU)
                op?.complete(Transport.CONNECTION_FAILED to null)
                channels.values.forEach { it.close() }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            this.mtu.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else MIN_MTU)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) { services.complete(status) }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            op?.complete(status to value)
        }

        @Deprecated("Android 12 and lower")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return     // the new callback had it
            @Suppress("DEPRECATION")
            op?.complete(status to characteristic.value?.copyOf())
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            op?.complete(status to null)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            op?.complete(status to null)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            channels[characteristic.uuid]?.trySend(value.copyOf())
        }

        @Deprecated("Android 12 and lower")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            characteristic.value?.let { channels[characteristic.uuid]?.trySend(it.copyOf()) }
        }
    }

    private val ops = Mutex()
    override var mtu: Int = MIN_MTU
        private set

    private fun characteristic(which: Int): BluetoothGattCharacteristic =
        gatt.getService(AndroidTransport.SERVICE_UUID)?.getCharacteristic(uuid(which))
            ?: throw Transport.GattException(ProtocolConstants.ERR_MALFORMED, "the leader has no characteristic $which")

    /** Runs one GATT operation: [start] issues it, the callback answers; status ≠ 0 throws. */
    private suspend fun operation(start: () -> Boolean): ByteArray? = ops.withLock {
        if (link.closed) throw Transport.GattException(Transport.CONNECTION_FAILED, "disconnected")
        val done = CompletableDeferred<Pair<Int, ByteArray?>>()
        link.op = done
        if (!start()) throw Transport.GattException(Transport.CONNECTION_FAILED, "operation not started")
        val (status, value) = withTimeoutOrNull(OP_TIMEOUT_MS) { done.await() }
            ?: throw Transport.GattException(Transport.CONNECTION_FAILED, "no answer")
        if (status != BluetoothGatt.GATT_SUCCESS) throw Transport.GattException(status, "status $status")
        value
    }

    override suspend fun read(characteristic: Int): ByteArray {
        val ch = characteristic(characteristic)
        return operation { gatt.readCharacteristic(ch) } ?: ByteArray(0)
    }

    override suspend fun write(characteristic: Int, value: ByteArray) {
        val ch = characteristic(characteristic)
        operation {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = value
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(ch)
            }
        }
    }

    override suspend fun subscribe(characteristic: Int): ReceiveChannel<ByteArray> {
        val ch = characteristic(characteristic)
        val channel = link.channels.getOrPut(ch.uuid) { Channel(Channel.UNLIMITED) }
        if (!gatt.setCharacteristicNotification(ch, true)) throw Transport.GattException(Transport.CONNECTION_FAILED, "notifications refused")
        val cccd = ch.getDescriptor(CCCD) ?: throw Transport.GattException(ProtocolConstants.ERR_MALFORMED, "no CCCD")
        operation {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }
        return channel
    }

    override suspend fun close() {
        link.closed = true
        link.channels.values.forEach { it.close() }
        gatt.disconnect()
        gatt.close()
    }

    companion object {
        const val MIN_MTU = 23
        const val REQUESTED_MTU = 517
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val OP_TIMEOUT_MS = 5_000L

        /** 8.2: connect, largest MTU first and waited for, then the service; high priority while connected. */
        suspend fun open(context: Context, device: BluetoothDevice): AndroidGattConnection {
            val link = Link()
            val gatt = device.connectGatt(context, false, link, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK or BluetoothDevice.PHY_LE_2M_MASK)
                ?: throw Transport.GattException(Transport.CONNECTION_FAILED, "connectGatt refused")
            fun fail(why: String): Nothing { gatt.disconnect(); gatt.close(); throw Transport.GattException(Transport.CONNECTION_FAILED, why) }
            val status = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { link.connected.await() } ?: fail("connect timeout")
            if (status != BluetoothGatt.GATT_SUCCESS) fail("connect status $status")
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            val mtu = if (gatt.requestMtu(REQUESTED_MTU)) withTimeoutOrNull(OP_TIMEOUT_MS) { link.mtu.await() } ?: MIN_MTU else MIN_MTU
            if (!gatt.discoverServices()) fail("discovery refused")
            val found = withTimeoutOrNull(OP_TIMEOUT_MS) { link.services.await() } ?: fail("discovery timeout")
            if (found != BluetoothGatt.GATT_SUCCESS || gatt.getService(AndroidTransport.SERVICE_UUID) == null) fail("no Zangbundel service")
            return AndroidGattConnection(gatt, link).also { it.mtu = mtu }
        }
    }
}

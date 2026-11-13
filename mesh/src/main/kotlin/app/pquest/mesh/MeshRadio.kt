package app.pquest.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bluetooth LE link to one Meshtastic-compatible LoRa radio (RAK WisBlock, Heltec, T-Beam ...).
 * Sends and receives opaque payloads on the private application port; the radios do the mesh routing.
 */
@SuppressLint("MissingPermission") // callers check Bluetooth permissions before use
@Suppress("DEPRECATION")           // the pre-API-33 GATT calls work on every supported Android version
class MeshRadio(private val ctx: Context, private val onPayload: (payload: ByteArray, fromNode: Long, hops: Int?) -> Unit) {
    companion object {
        val SERVICE: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        private val TO_RADIO = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        private val FROM_RADIO = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        private val FROM_NUM = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    data class Found(val address: String, val name: String)

    val found = MutableStateFlow<List<Found>>(emptyList())
    val connected = MutableStateFlow(false)
    val radioName = MutableStateFlow<String?>(null)
    val nodes = MutableStateFlow<List<MeshNode>>(emptyList())

    private val adapter get() = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var myNode = 0L
    private val ops = ConcurrentLinkedQueue<(BluetoothGatt) -> Boolean>()
    private val busy = AtomicBoolean(false)
    private var packetId = (System.nanoTime() and 0x3FFFFFFF).toInt()

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val f = Found(r.device.address, r.device.name ?: r.scanRecord?.deviceName ?: r.device.address)
            if (found.value.none { it.address == f.address }) found.value = found.value + f
        }
    }

    fun scan(on: Boolean) = runCatching {
        val s = adapter?.bluetoothLeScanner ?: return@runCatching
        if (on) {
            found.value = emptyList()
            s.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()), ScanSettings.Builder().build(), scanCb)
        } else s.stopScan(scanCb)
    }

    /** Connects and keeps reconnecting whenever the radio comes back in range. */
    fun connect(address: String) = runCatching {
        disconnect()
        val dev: BluetoothDevice = adapter?.getRemoteDevice(address) ?: return@runCatching
        radioName.value = dev.name ?: address
        gatt = dev.connectGatt(ctx, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null; connected.value = false; ops.clear(); busy.set(false)
    }

    /** Queues one payload for broadcast over the mesh. Returns false when no radio is connected. */
    fun send(payload: ByteArray): Boolean {
        if (!connected.value || payload.size > Meshtastic.MAX_PAYLOAD) return false
        val frame = Meshtastic.send(payload, packetId++)
        enqueue { g -> g.toRadio()?.let { c -> c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; c.value = frame; g.writeCharacteristic(c) } ?: false }
        return true
    }

    private fun BluetoothGatt.toRadio() = getService(SERVICE)?.getCharacteristic(TO_RADIO)
    private fun readFromRadio() = enqueue { g -> g.getService(SERVICE)?.getCharacteristic(FROM_RADIO)?.let { g.readCharacteristic(it) } ?: false }

    private fun enqueue(op: (BluetoothGatt) -> Boolean) { ops.add(op); pump() }

    /** GATT allows one operation at a time: run the next queued one when the previous has completed. */
    private fun pump() {
        val g = gatt ?: return
        while (busy.compareAndSet(false, true)) {
            val op = ops.poll()
            if (op == null) { busy.set(false); return }
            if (runCatching { op(g) }.getOrDefault(false)) return
            busy.set(false)
        }
    }

    private fun done() { busy.set(false); pump() }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            ops.clear(); busy.set(false)
            if (state == BluetoothProfile.STATE_CONNECTED) g.requestMtu(512) else connected.value = false
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val num = g.getService(SERVICE)?.getCharacteristic(FROM_NUM) ?: return
            g.setCharacteristicNotification(num, true)
            enqueue { num.getDescriptor(CCCD)?.let { d -> d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) } ?: false }
            enqueue { g.toRadio()?.let { c -> c.value = Meshtastic.wantConfig(packetId++); g.writeCharacteristic(c) } ?: false }
            readFromRadio()
            connected.value = true
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = done()
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = done()
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { if (c.uuid == FROM_NUM) readFromRadio() }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            val v = c.value
            if (c.uuid == FROM_RADIO && status == BluetoothGatt.GATT_SUCCESS && v != null && v.isNotEmpty()) {
                when (val m = Meshtastic.parse(v)) {
                    is FromRadio.MyInfo -> myNode = m.nodeNum
                    is FromRadio.Node -> nodes.value = nodes.value.filter { it.num != m.node.num } + m.node
                    is FromRadio.Packet -> {
                        if (m.from != myNode) nodes.value = nodes.value.map { if (it.num == m.from) it.copy(lastHeard = System.currentTimeMillis(), hops = m.hops ?: it.hops) else it }
                        if (m.port == Meshtastic.PORT_PRIVATE && m.from != myNode) runCatching { onPayload(m.payload, m.from, m.hops) }
                    }
                    else -> {}
                }
                readFromRadio() // keep draining until the radio returns an empty frame
            }
            done()
        }
    }
}

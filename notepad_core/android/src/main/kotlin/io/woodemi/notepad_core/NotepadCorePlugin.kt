package io.woodemi.notepad_core

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.*
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

private const val TAG = "NotepadCorePlugin"

/** NotepadCorePlugin */
class NotepadCorePlugin() : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        NotepadCorePlugin(flutterPluginBinding.applicationContext, flutterPluginBinding.binaryMessenger)
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            NotepadCorePlugin(registrar.context(), registrar.messenger())
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    }

    private lateinit var context: Context

    constructor(context: Context, messenger: BinaryMessenger) : this() {
        this.context = context
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        MethodChannel(messenger, "notepad_core/method").setMethodCallHandler(this)
        EventChannel(messenger, "notepad_core/event.scanResult").setStreamHandler(this)
        connectorMessage = BasicMessageChannel(messenger, "notepad_core/message.connector", StandardMessageCodec.INSTANCE)
        clientMessage = BasicMessageChannel(messenger, "notepad_core/message.client", StandardMessageCodec.INSTANCE)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        Log.d(TAG, "onMethodCall " + call.method)
        when (call.method) {
            "startScan" -> {
                bluetoothManager.adapter.bluetoothLeScanner?.startScan(scanCallback)
                result.success(null)
            }
            "stopScan" -> {
                bluetoothManager.adapter.bluetoothLeScanner?.stopScan(scanCallback)
                result.success(null)
            }
            "connect" -> {
                connectGatt = bluetoothManager.adapter
                        .getRemoteDevice(call.argument<String>("deviceId"))
                        .connectGatt(context, false, gattCallback)
                result.success(null)
            }
            "disconnect" -> {
                connectGatt?.disconnect()
                connectGatt?.close()
                connectGatt = null
                result.success(null)
            }
            "discoverServices" -> {
                connectGatt?.discoverServices()
                result.success(null)
            }
            "setNotifiable" -> {
                val service = call.argument<String>("service")!!
                val characteristic = call.argument<String>("characteristic")!!
                val bleInputProperty = call.argument<String>("bleInputProperty")!!
                connectGatt?.setNotifiable(service to characteristic, bleInputProperty)
                result.success(null)
            }
            "writeValue" -> {
                val service = call.argument<String>("service")!!
                val characteristic = call.argument<String>("characteristic")!!
                val value = call.argument<ByteArray>("value")!!
                val writeResult = connectGatt?.getCharacteristic(service to characteristic)?.let {
                    it.value = value
                    connectGatt?.writeCharacteristic(it)
                }
                if (writeResult == true)
                    result.success(null)
                else
                    result.error("Characteristic unavailable", null, null)
            }
            else -> result.notImplemented()
        }
    }

    private lateinit var bluetoothManager: BluetoothManager

    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            Log.v(TAG, "onScanFailed: $errorCode")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.v(TAG, "onScanResult: $callbackType + $result")
            scanResultSink?.success(mapOf<String, Any>(
                    "name" to (result.device.name ?: ""),
                    "deviceId" to result.device.address,
                    "manufacturerData" to (result.manufacturerData ?: byteArrayOf()),
                    "rssi" to result.rssi
            ))
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.v(TAG, "onBatchScanResults: $results")
        }
    }

    private var scanResultSink: EventChannel.EventSink? = null

    override fun onListen(args: Any?, eventSink: EventChannel.EventSink?) {
        val map = args as? Map<String, Any> ?: return
        when (map["name"]) {
            "scanResult" -> scanResultSink = eventSink
        }
    }

    override fun onCancel(args: Any?) {
        val map = args as? Map<String, Any> ?: return
        when (map["name"]) {
            "scanResult" -> scanResultSink = null
        }
    }

    private var connectGatt: BluetoothGatt? = null

    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private lateinit var connectorMessage: BasicMessageChannel<Any>

    private lateinit var clientMessage: BasicMessageChannel<Any>

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (gatt != connectGatt) {
                Log.e(TAG, "Probably MEMORY LEAK!")
                return
            }
            Log.v(TAG, "onConnectionStateChange: status($status), newState($newState)")
            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                mainThreadHandler.post { connectorMessage.send(mapOf("ConnectionState" to "connected")) }
            } else {
                connectGatt?.close()
                connectGatt = null
                mainThreadHandler.post { connectorMessage.send(mapOf("ConnectionState" to "disconnected")) }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (gatt != connectGatt || status != BluetoothGatt.GATT_SUCCESS) return
            gatt?.services?.forEach { service ->
                Log.v(TAG, "Service " + service.uuid)
                service.characteristics.forEach { characteristic ->
                    Log.v(TAG, "    Characteristic ${characteristic.uuid}")
                    characteristic.descriptors.forEach {
                        Log.v(TAG, "        Descriptor ${it.uuid}")
                    }
                }
            }

            mainThreadHandler.post { connectorMessage.send(mapOf("ServiceState" to "discovered")) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt != connectGatt) {
                Log.e(TAG, "Probably MEMORY LEAK!")
                return
            }
            Log.v(TAG, "onDescriptorWrite ${descriptor.uuid}, ${descriptor.characteristic.uuid}, $status")
            mainThreadHandler.post { clientMessage.send(mapOf("characteristicConfig" to descriptor.characteristic.uuid.toString())) }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt != connectGatt) {
                Log.e(TAG, "Probably MEMORY LEAK!")
                return
            }
            Log.v(TAG, "onCharacteristicWrite ${characteristic.uuid}, ${characteristic.value.contentToString()} $status")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic) {
            if (gatt != connectGatt) {
                Log.e(TAG, "Probably MEMORY LEAK!")
                return
            }
            Log.v(TAG, "onCharacteristicChanged ${characteristic.uuid}, ${characteristic.value.contentToString()}")
            val characteristicValue = mapOf(
                    "characteristic" to characteristic.uuid.toString(),
                    "value" to characteristic.value
            )
            mainThreadHandler.post { clientMessage.send(mapOf("characteristicValue" to characteristicValue)) }
        }
    }
}

val ScanResult.manufacturerData: ByteArray?
    get() {
        val sparseArray = scanRecord?.manufacturerSpecificData ?: return null
        if (sparseArray.size() == 0) return null

        return sparseArray.keyAt(0).toShort().toByteArray() + sparseArray.valueAt(0)
    }

fun Short.toByteArray(byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray =
        ByteBuffer.allocate(2 /*Short.SIZE_BYTES*/).order(byteOrder).putShort(this).array()

fun BluetoothGatt.getCharacteristic(serviceCharacteristic: Pair<String, String>) =
        getService(UUID.fromString(serviceCharacteristic.first)).getCharacteristic(UUID.fromString(serviceCharacteristic.second))

private val DESC__CLIENT_CHAR_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

fun BluetoothGatt.setNotifiable(serviceCharacteristic: Pair<String, String>, bleInputProperty: String) {
    val descriptor = getCharacteristic(serviceCharacteristic).getDescriptor(DESC__CLIENT_CHAR_CONFIGURATION)
    val (value, enable) = when (bleInputProperty) {
        "notification" -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE to true
        "indication" -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE to true
        else -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE to false
    }
    descriptor.value = value
    setCharacteristicNotification(descriptor.characteristic, enable) && writeDescriptor(descriptor)
}
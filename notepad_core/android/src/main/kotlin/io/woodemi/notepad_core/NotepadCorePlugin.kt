package io.woodemi.notepad_core

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
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

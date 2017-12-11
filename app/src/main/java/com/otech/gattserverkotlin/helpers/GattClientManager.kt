package com.otech.gattserverkotlin.helpers

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import no.nordicsemi.android.support.v18.scanner.*
import java.util.*


class GattClientManager(private val mGattServerClientCallback: GattServerClientCallback?, private val context: Context)
    : BluetoothGattCallback() {


    private val TAG = "GattClientManager"
    private val mHandler: Handler = Handler()
    private var mConnectedGatt: BluetoothGatt? = null


    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        val device = gatt.device
        log("onConnectionStateChange(" + device.address + ") "
                + GattServerManager.DeviceProfile.getStatusDescription(status) + " to "
                + GattServerManager.DeviceProfile.getStateDescription(newState))


        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()

            if (mGattServerClientCallback != null) {
                mHandler.post { mGattServerClientCallback.onDeviceConnected(device) }
            }

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            disconnect()
            if (mGattServerClientCallback != null) {
                mHandler.post { mGattServerClientCallback.onDeviceDisconnected(device) }
            }
        }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        if (mGattServerClientCallback != null) {
            mHandler.post { mGattServerClientCallback.log(message) }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        log("onServicesDiscovered:")

        for (service in gatt.services) {
            log("Service: " + service.uuid)
            if (service.uuid === GattServerManager.DeviceProfile.SERVICE_UUID) {
                val characteristicSTATUS = service.getCharacteristic(GattServerManager.DeviceProfile.CHARACTERISTIC_STATUS_UUID)
                val characteristicSSID = service.getCharacteristic(GattServerManager.DeviceProfile.CHARACTERISTIC_SSID_UUID)
                val characteristicPASSWORD = service.getCharacteristic(GattServerManager.DeviceProfile.CHARACTERISTIC_PASSWORD_UUID)

                //Read the current characteristic's value
                gatt.readCharacteristic(characteristicSTATUS)
                gatt.readCharacteristic(characteristicSSID)
                gatt.readCharacteristic(characteristicPASSWORD)
            }
        }
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt,
                                      characteristic: BluetoothGattCharacteristic,
                                      status: Int) {
        super.onCharacteristicRead(gatt, characteristic, status)
        val value = characteristic.value

        //Register for further updates as notifications
        gatt.setCharacteristicNotification(characteristic, true)

        log("onCharacteristicRead(" + characteristic.uuid + ")")

        if (mGattServerClientCallback != null) {
            mHandler.post { mGattServerClientCallback.onCharacteristicRead(characteristic.uuid, value) }
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                         characteristic: BluetoothGattCharacteristic) {
        super.onCharacteristicChanged(gatt, characteristic)
        val value = characteristic.value

        log("onCharacteristicChanged(" + characteristic.uuid + ")")

        if (mGattServerClientCallback != null) {
            mHandler.post { mGattServerClientCallback.onCharacteristicRead(characteristic.uuid, value) }
        }
    }

    /*
     * Begin a scan for new servers that advertise our
     * matching service.
     */
    fun startScan() {
        log("Scan Started")
        //Scan for devices advertising our custom service
        val filters = ArrayList<ScanFilter>()
        filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(GattServerManager.DeviceProfile.SERVICE_UUID)).build())

        val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(true)
                .build()

        BluetoothLeScannerCompat.getScanner().startScan(filters, settings, mScanCallback)
    }

    /*
     * Callback handles results from new devices that appear
     * during a scan. Batch results appear when scan delay
     * filters are enabled.
     */
    private val mScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            processResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>?) {
            for (result in results!!) {
                processResult(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("LE Scan Failed: " + errorCode)
        }

        private fun processResult(result: ScanResult?) {
            val device = result!!.device
            if (mGattServerClientCallback != null) {
                mHandler.post { mGattServerClientCallback.onNewDeviceFound(device) }
            }
        }
    }

    /*
   * Terminate any active scans
   */
    fun stopScan() {
        log("Scan Stopped")
        BluetoothLeScannerCompat.getScanner().stopScan(mScanCallback)
    }

    fun connectToDevice(device: BluetoothDevice) {
        log("Attempting to connect to " + device.address)
        if (isConnected()) {
            disconnect()
        }
        mConnectedGatt = device.connectGatt(context, false, this@GattClientManager)

        if (mGattServerClientCallback != null) {
            mHandler.post { mGattServerClientCallback.onDeviceDisconnected(device) }
        }
    }

    interface GattServerClientCallback {
        fun onNewDeviceFound(device: BluetoothDevice)

        fun onDeviceConnected(device: BluetoothDevice)

        fun onDeviceDisconnected(device: BluetoothDevice)

        fun onCharacteristicRead(uuid: UUID, bytes: ByteArray)

        fun log(message: String?)
    }

    fun disconnect() {
        //Disconnect from any active connection
        if (mConnectedGatt != null) {
            try {
                mConnectedGatt!!.disconnect()
            } catch (e: Exception) {
                //ignored
            }

            mConnectedGatt = null
        }
    }

    fun isConnected(): Boolean {
        return mConnectedGatt != null
    }

    fun writeToCharacteristics(uuid: UUID, value: ByteArray) {
        if (mConnectedGatt != null) {
            val service = mConnectedGatt!!.getService(GattServerManager.DeviceProfile.SERVICE_UUID)
            if (service != null) {
                val characteristic = service.getCharacteristic(uuid)
                if (characteristic != null) {
                    characteristic.value = value
                    mConnectedGatt!!.writeCharacteristic(characteristic)
                }
            }
        }
    }

    fun readCharacteristic(uuid: UUID) {
        if (mConnectedGatt != null) {
            val service = mConnectedGatt!!.getService(GattServerManager.DeviceProfile.SERVICE_UUID)
            if (service != null) {
                val characteristic = service.getCharacteristic(uuid)
                if (characteristic != null) {
                    mConnectedGatt!!.readCharacteristic(characteristic)
                }
            }
        }
    }
}

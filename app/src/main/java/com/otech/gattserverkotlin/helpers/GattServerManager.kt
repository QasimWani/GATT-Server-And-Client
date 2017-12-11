package com.otech.gattserverkotlin.helpers

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import android.util.SparseArray
import java.nio.charset.Charset
import java.util.*


class GattServerManager(private val context: Context, private val gattServerCallbacks: GattServerCallbacks?)
    : BluetoothGattServerCallback() {

    private val TAG = "GattServerManager"
    private val mHandler: Handler = Handler()
    private val mBluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager;
    private var mGattServer: BluetoothGattServer? = null
    private var mBluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private val mConnectedDevices: SparseArray<BluetoothDevice> = SparseArray()

    /*
     * Create the GATT server instance, attaching all services and
     * characteristics that should be exposed
     */
    fun startServer() {

        this.mGattServer = mBluetoothManager.openGattServer(context, this)

        val service = BluetoothGattService(
                DeviceProfile.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val statusCharacteristic = BluetoothGattCharacteristic(
                DeviceProfile.CHARACTERISTIC_STATUS_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
        )

        val ssidCharacteristic = BluetoothGattCharacteristic(
                DeviceProfile.CHARACTERISTIC_SSID_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val passwordCharacteristic = BluetoothGattCharacteristic(
                DeviceProfile.CHARACTERISTIC_PASSWORD_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(statusCharacteristic)
        service.addCharacteristic(ssidCharacteristic)
        service.addCharacteristic(passwordCharacteristic)

        mGattServer?.addService(service)

        startAdvertising()
    }

    /*
     * Initialize the advertiser
     */
    private fun startAdvertising() {
        val bluetoothAdapter = mBluetoothManager.adapter
        mBluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

        if (mBluetoothLeAdvertiser == null) return

        val settings = AdvertiseSettings.Builder()
                .setConnectable(true)
                .setTimeout(0)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

        val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(DeviceProfile.SERVICE_UUID))
                .build()

        mBluetoothLeAdvertiser?.startAdvertising(settings, data, mAdvertiseCallback)
    }

    /*
     * Callback handles events from the framework describing
     * if we were successful in starting the advertisement requests.
     */
    private val mAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            append("Peripheral Advertise Started.")
            if (gattServerCallbacks != null)
                mHandler.post { gattServerCallbacks.serverIsReady(true, 0, null) }

        }

        override fun onStartFailure(errorCode: Int) {
            val errorMessage = getErrorMessage(errorCode)
            append("Peripheral Advertise Failed: " + errorMessage!!)
            if (gattServerCallbacks != null)
                mHandler.post { gattServerCallbacks.serverIsReady(false, errorCode, getErrorMessage(errorCode)) }

        }
    }

    /*
    * Terminate the server and any running callbacks
    */
    fun shutdownServer() {
        stopAdvertising()

        if (mGattServer == null) {
            mConnectedDevices.clear()
            return
        }

        for (i in 0 until mConnectedDevices.size()) {
            val device = mConnectedDevices.valueAt(i)
            try {
                mGattServer?.cancelConnection(device)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to cancel Connection with device " + device.address, e)
            }

        }

        try {
            mGattServer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to close gatt server ", e)
        }

        mConnectedDevices.clear()
    }

    /*
    * Terminate the advertiser
    */
    private fun stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return

        mBluetoothLeAdvertiser?.stopAdvertising(mAdvertiseCallback)
    }

    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        super.onConnectionStateChange(device, status, newState)

        val type = device.type
        if (type == 0) {

            append("onConnectionStateChange(" + device.address + ") from "
                    + DeviceProfile.getStatusDescription(status) + " to "
                    + DeviceProfile.getStateDescription(newState))

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                postDeviceChange(device, true)

            } else {
                postDeviceChange(device, false)
            }
        }
    }

    override fun onCharacteristicReadRequest(device: BluetoothDevice,
                                             requestId: Int,
                                             offset: Int,
                                             characteristic: BluetoothGattCharacteristic) {
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
        append("onCharacteristicReadRequest(" + device.address + ") " + characteristic.uuid.toString())


        mHandler.post {
            if (DeviceProfile.CHARACTERISTIC_STATUS_UUID == characteristic.uuid) {
                mGattServer?.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf((if (gattServerCallbacks?.isWifiConnected!!) 1 else 0).toByte()))


            } else if (DeviceProfile.CHARACTERISTIC_SSID_UUID == characteristic.uuid) {
                mGattServer?.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        gattServerCallbacks?.ssid?.toByteArray(Charset.forName("UTF-8")))

            } else if (DeviceProfile.CHARACTERISTIC_PASSWORD_UUID == characteristic.uuid) {
                mGattServer?.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        gattServerCallbacks?.password?.toByteArray(Charset.forName("UTF-8")))

            }

            /*
* Unless the characteristic supports WRITE_NO_RESPONSE,
* always send a response back for any request.
*/
            mGattServer?.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0,
                    null)
        }
    }

    override fun onCharacteristicWriteRequest(device: BluetoothDevice,
                                              requestId: Int,
                                              characteristic: BluetoothGattCharacteristic,
                                              preparedWrite: Boolean,
                                              responseNeeded: Boolean,
                                              offset: Int,
                                              value: ByteArray) {

        super.onCharacteristicWriteRequest(device, requestId, characteristic,
                preparedWrite, responseNeeded, offset, value)

        append("onCharacteristicWriteRequest(" + device.address + ") " + characteristic.uuid.toString())

        val setTo = String(value, Charset.forName("UTF-8"))

        if (gattServerCallbacks != null)
            mHandler.post {
                if (DeviceProfile.CHARACTERISTIC_SSID_UUID == characteristic.uuid) {
                    gattServerCallbacks.ssid = setTo
                    append("SSID was set to " + setTo)

                } else if (DeviceProfile.CHARACTERISTIC_PASSWORD_UUID == characteristic.uuid) {
                    gattServerCallbacks.password = setTo
                    append("Password was set to " + setTo)
                }
            }

        if (responseNeeded) {
            mGattServer?.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    value)
        }

    }

    /* Storage and access to local characteristic data */

    fun notifyConnectedDevicesOfWifiStatus(isConnected: Boolean) {
        for (i in 0 until mConnectedDevices.size()) {
            val device = mConnectedDevices.valueAt(i)
            val service = mGattServer?.getService(DeviceProfile.SERVICE_UUID)

            val readCharacteristic = service?.getCharacteristic(DeviceProfile.CHARACTERISTIC_STATUS_UUID)
            readCharacteristic?.value = byteArrayOf((if (isConnected) 1 else 0).toByte())
            append("Notifying is connected to wifi status: " + isConnected)
            mGattServer?.notifyCharacteristicChanged(device, readCharacteristic, false)
        }
    }

    private fun append(message: String) {
        Log.d(TAG, message)
        if (gattServerCallbacks != null) {
            mHandler.post { gattServerCallbacks.log(message) }
        }
    }


    private fun postDeviceChange(device: BluetoothDevice, toAdd: Boolean) {
        mHandler.post {
            //This will add the item to our list and update the adapter at the same time.
            if (toAdd) {
                mConnectedDevices.put(device.address.hashCode(), device)
            } else {
                mConnectedDevices.remove(device.address.hashCode())
            }

            //Trigger our periodic notification once devices are connected
            mHandler.removeCallbacks(mNotifyRunnable)
            if (mConnectedDevices.size() != 0) {
                mHandler.post(mNotifyRunnable)
            }
        }
    }

    private val mNotifyRunnable = object : Runnable {
        override fun run() {
            if (mConnectedDevices.size() > 0) {
                val wifiConnected = gattServerCallbacks?.isWifiConnected!!
                notifyConnectedDevicesOfWifiStatus(wifiConnected)
                mHandler.postDelayed(this, 2000)
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String? {
        if (errorCode == 1) {
            return "DATA TOO LARGE"
        } else if (errorCode == 2) {
            return "TOO MANY ADVERTISERS"
        } else if (errorCode == 3) {
            return "ALREADY STARTED"
        } else if (errorCode == 4) {
            return "INTERNAL ERROR"
        } else if (errorCode == 5) {
            return "FEATURE UNSUPPORTED"
        }
        return null
    }

    object DeviceProfile {

        /* Unique ids generated for this device by 'uuidgen'. Doesn't conform to any SIG profile. */

        //Service UUID to expose our time characteristics
        var SERVICE_UUID = UUID.fromString("00001111-0000-1000-8000-00805f9b34fb")
        var CHARACTERISTIC_SSID_UUID = UUID.fromString("BD28E457-4026-4270-A99F-F9BC20182E15")
        var CHARACTERISTIC_PASSWORD_UUID = UUID.fromString("BD28E458-4026-4270-A99F-F9BC20182E15")
        var CHARACTERISTIC_STATUS_UUID = UUID.fromString("275348FB-C14D-4FD5-B434-7C3F351DEA5F")

        fun getStateDescription(state: Int): String {
            return when (state) {
                BluetoothProfile.STATE_CONNECTED -> "Connected"
                BluetoothProfile.STATE_CONNECTING -> "Connecting"
                BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting"
                else -> "Unknown State " + state
            }
        }

        fun getStatusDescription(status: Int): String {
            return when (status) {
                BluetoothGatt.GATT_SUCCESS -> "SUCCESS"
                else -> "Unknown Status " + status
            }
        }

    }

    interface GattServerCallbacks {
        var ssid: String?
        var password: String?
        val isWifiConnected: Boolean?
        fun log(message: String?)
        fun serverIsReady(ready: Boolean, errorCode: Int, errorMessage: String?)
    }
}
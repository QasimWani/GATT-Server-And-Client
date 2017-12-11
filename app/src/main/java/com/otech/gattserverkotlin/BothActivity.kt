package com.otech.gattserverkotlin

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.otech.gattserverkotlin.databinding.ActivityBothBinding
import com.otech.gattserverkotlin.helpers.GattClientManager
import com.otech.gattserverkotlin.helpers.GattServerManager
import com.otech.gattserverkotlin.helpers.WifiConfigManager
import java.nio.charset.Charset
import java.util.*

class BothActivity : AppCompatActivity(),
        GattServerManager.GattServerCallbacks,
        GattClientManager.GattServerClientCallback, WifiConfigManager.WifiConnectionListener {


    private val TAG = "PeripheralActivity"

    private val mLock = Any()
    private var clientManager: GattClientManager? = null
    private var mDevices: SparseArray<BluetoothDevice> = SparseArray()

    private lateinit var binding: ActivityBothBinding
    private lateinit var wifiConfigManager: WifiConfigManager
    private lateinit var mGattServerManager: GattServerManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_both)
        mGattServerManager = GattServerManager(this, this)
        wifiConfigManager = WifiConfigManager(this, this)

        if (checkPermission()) {
            mGattServerManager.startServer()
        }

    }

    private fun checkPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                requestPermissions(arrayOf(Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CHANGE_WIFI_STATE), 200)

                return false
            }
        }
        return true
    }

    override fun onDestroy() {
        mGattServerManager.shutdownServer()
        //Stop any active scans
        clientManager?.stopScan()
        clientManager?.disconnect()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        for (i in grantResults.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                this.finish()
                return
            }
        }
        mGattServerManager.startServer()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scan, menu)
        //Add any device elements we've discovered to the overflow menu
        for (i in 0 until mDevices.size()) {
            val device = mDevices.valueAt(i)
            menu.add(0, mDevices.keyAt(i), 0, device.name)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan -> {
                if (checkPermission()) {
                    clientManager = GattClientManager(this, this)
                    clientManager?.startScan()
                }
                return true
            }
            else -> {
                //Obtain the discovered device to connect with
                val device = mDevices.get(item.itemId)
                Log.i(TAG, "Connecting to " + device.name)
                clientManager?.connectToDevice(device)

                return super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onNewDeviceFound(device: BluetoothDevice) {
        mDevices.put(device.address.hashCode(), device)
        //Update the overflow menu
        invalidateOptionsMenu()
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        supportActionBar?.subtitle = "Connected to " + device.address

        Handler().postDelayed({ onGetStatusClicked(null) }, 2000)
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        supportActionBar?.subtitle = "Disconnected"
    }

    override fun onCharacteristicRead(uuid: UUID, bytes: ByteArray) {
        val value = String(bytes, Charset.forName("UTF-8"))
        if (uuid == GattServerManager.DeviceProfile.CHARACTERISTIC_STATUS_UUID) {
            binding.status.text = if (value[0].toInt() == 1) "Connected" else "Disconnected"
        } else if (uuid == GattServerManager.DeviceProfile.CHARACTERISTIC_SSID_UUID) {
            binding.ssid.setText(value)
        } else if (uuid == GattServerManager.DeviceProfile.CHARACTERISTIC_PASSWORD_UUID) {
            binding.password.setText(value)
        }
    }

    fun onGetStatusClicked(view: View?) {
        if (isConnected()) {
            clientManager?.readCharacteristic(GattServerManager.DeviceProfile.CHARACTERISTIC_STATUS_UUID)
        }
    }

    fun onUpdateSSIDClicked(view: View) {
        if (isConnected()) {
            clientManager?.writeToCharacteristics(GattServerManager.DeviceProfile.CHARACTERISTIC_SSID_UUID,
                    binding.ssid.text.toString().toByteArray(Charset.forName("UTF-8")))
        }
    }

    fun onGetSSIDClicked(view: View) {
        if (isConnected()) {
            clientManager?.readCharacteristic(GattServerManager.DeviceProfile.CHARACTERISTIC_SSID_UUID)
        }
    }

    fun onUpdatePasswordClicked(view: View) {
        if (isConnected()) {
            clientManager?.writeToCharacteristics(GattServerManager.DeviceProfile.CHARACTERISTIC_PASSWORD_UUID,
                    binding.password.text.toString().toByteArray(Charset.forName("UTF-8")))
        }
    }

    fun onGetPasswordClicked(view: View) {
        if (isConnected()) {
            clientManager?.readCharacteristic(GattServerManager.DeviceProfile.CHARACTERISTIC_PASSWORD_UUID)
        }
    }

    private fun isConnected(): Boolean {
        if (clientManager != null && clientManager!!.isConnected()) {
            return true
        }

        Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show()
        return false
    }

    override fun log(message: String?) {
        binding.text.append(message + "\n")
    }

    override fun serverIsReady(ready: Boolean, errorCode: Int, errorMessage: String?) {
        if (ready)
            title = "GATT Server Ready"
        else
            title = "GATT Server Error: " + errorMessage
    }

    override fun wifiNetworkConnected(connected: Boolean) {
        mGattServerManager.notifyConnectedDevicesOfWifiStatus(connected)
    }

    override val isWifiConnected: Boolean?
        get() = wifiConfigManager.isConnectedToWifi()

    override var password: String? = null
        get() {
            synchronized(mLock) {
                return if (field == null) "" else field
            }
        }
        set(value) {
            synchronized(mLock) {
                field = value
                connectToWifi()
            }
        }

    override var ssid: String? = null
        get() {
            synchronized(mLock) {
                return if (field == null) "" else field
            }
        }
        set(value) {
            synchronized(mLock) {
                field = value
                connectToWifi()
            }
        }

    private fun connectToWifi() {
        if (!TextUtils.isEmpty(ssid) && !TextUtils.isEmpty(password)) {
            val passSplit = password?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
            if (passSplit?.size == 2) {
                wifiConfigManager.connectToWifi(ssid!!, passSplit[1],
                        WifiConfigManager.WifiNetworkPasswordType.valueOf(passSplit[0]))
            }
        }
    }
}

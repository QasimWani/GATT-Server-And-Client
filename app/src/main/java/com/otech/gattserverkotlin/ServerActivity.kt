package com.otech.gattserverkotlin

import android.Manifest
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import com.otech.gattserverkotlin.databinding.ActivityServerBinding
import com.otech.gattserverkotlin.helpers.GattServerManager
import com.otech.gattserverkotlin.helpers.WifiConfigManager

class ServerActivity : AppCompatActivity(),
        WifiConfigManager.WifiConnectionListener,
        GattServerManager.GattServerCallbacks {

    private val TAG = "PeripheralActivity"
    private val mLock = Any()
    private lateinit var mGattServerManager: GattServerManager
    private lateinit var wifiConfigManager: WifiConfigManager
    private lateinit var binding: ActivityServerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView<ActivityServerBinding>(this, R.layout.activity_server)

        wifiConfigManager = WifiConfigManager(this, this)
        mGattServerManager = GattServerManager(this, this)

        if (checkPermission()) {
            mGattServerManager.startServer()
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        for (i in grantResults.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                this.finish()
                return
            }
        }
        mGattServerManager.startServer()
        wifiConfigManager.startListeningForChange()
    }

    override fun onDestroy() {
        wifiConfigManager.stopListeningForChange()
        mGattServerManager.shutdownServer()
        super.onDestroy()
    }

    override fun log(message: String?) {
        binding.text.append(message + "\n")
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

    override fun serverIsReady(ready: Boolean, errorCode: Int, errorMessage: String?) {
        if (ready)
            title = "GATT Server Ready"
        else
            title = "GATT Server Error: " + errorMessage
    }

    override fun wifiNetworkConnected(connected: Boolean) {
        mGattServerManager.notifyConnectedDevicesOfWifiStatus(connected)
    }

    private fun connectToWifi() {
        if (!TextUtils.isEmpty(ssid) && !TextUtils.isEmpty(password)) {
            val passSplit = password?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
            if (passSplit?.size == 2) {
                wifiConfigManager.connectToWifi(ssid!!, passSplit[1], WifiConfigManager.WifiNetworkPasswordType.valueOf(passSplit[0]))
            }
        }
    }

    private fun checkPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE), 200)
                return false
            }
        }
        return true
    }
}

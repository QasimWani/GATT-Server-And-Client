package com.otech.gattserverkotlin.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager


class WifiConfigManager(private val context: Context, private val wifiConnectionListener: WifiConnectionListener?) {

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val info = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
            wifiConnectionListener?.wifiNetworkConnected(info != null && info.isConnected)
        }
    }

    fun startListeningForChange() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    fun stopListeningForChange() {
        context.unregisterReceiver(broadcastReceiver)
    }

    fun connectToWifi(networkSSID: String, networkPass: String, type: WifiNetworkPasswordType) {

        val conf = WifiConfiguration()
        conf.SSID = "\"$networkSSID \""


        if (type == WifiNetworkPasswordType.WEP) {  //For WEP network you need to do this:

            conf.wepKeys[0] = "\"$networkPass\""
            conf.wepTxKeyIndex = 0
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)

        } else if (type == WifiNetworkPasswordType.WPA) { //For WPA network you need to add passphrase like this:

            conf.preSharedKey = "\"$networkPass\""

        } else if (type == WifiNetworkPasswordType.OPEN) { //For Open network you need to do this:

            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)

        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.addNetwork(conf)

        //Enable it, so Android connects to it:
        val list = wifiManager.configuredNetworks
        for (i in list) {
            if (i.SSID != null && i.SSID == "\"$networkSSID\"") {
                wifiManager.disconnect()
                wifiManager.enableNetwork(i.networkId, true)
                wifiManager.reconnect()
                break
            }
        }
    }

    fun isConnectedToWifi(): Boolean {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)

        return mWifi.isConnected
    }

    interface WifiConnectionListener {
        fun wifiNetworkConnected(connected: Boolean)
    }

    enum class WifiNetworkPasswordType {
        WEP, WPA, OPEN
    }

}
package com.example.mviapp.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/**
 * LanDrop 服务发现
 *
 * 通过 Android 系统 NSD（Network Service Discovery）浏览局域网内广播的
 * `_landrop._tcp` 服务，自动解析出 Mac 的 IP 与端口，免手动输入、免摄像头。
 */
class LanDropDiscovery(private val context: Context) {

    interface Callback {
        fun onServiceFound(host: String, port: Int, name: String)
        fun onServiceLost()
        fun onError(message: String)
    }

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null

    fun startDiscovery(callback: Callback) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                // 只处理 LanDrop 服务类型
                if (info.serviceType.contains(SERVICE_TYPE)) {
                    resolveService(info, callback)
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                callback.onServiceLost()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                callback.onError("发现服务失败: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsdManager?.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )
    }

    private fun resolveService(info: NsdServiceInfo, callback: Callback) {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                callback.onServiceFound(host, info.port, info.serviceName)
            }

            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                callback.onError("解析服务失败: $errorCode")
            }
        }
        nsdManager?.resolveService(info, resolveListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        discoveryListener = null
        resolveListener = null
    }

    private companion object {
        const val SERVICE_TYPE = "_landrop._tcp"
    }
}

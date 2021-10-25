package com.longforus.myremotecontrol

import android.util.Base64
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.aliyun.iot20180120.models.PubRequest
import com.aliyun.iot20180120.models.RRpcRequest
import com.aliyun.iot20180120.models.RRpcResponse
import com.longforus.myremotecontrol.bean.AcMode
import com.longforus.myremotecontrol.bean.DacInputSource
import com.longforus.myremotecontrol.util.LogUtils
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

class ClientHolder(private val vm:MainViewModel) {

    val TAG = "ClientHolder"

    private var dacTimerJob: Job? = null


    fun doRequest(topic: String, content: String, devName: String) {
        val pubRequest: PubRequest = PubRequest()
            .setIotInstanceId(InstanceId)
            .setProductKey(PRODUCTKEY)
            .setQos(0)
            .setTopicFullName("/${PRODUCTKEY}/$devName/user/$topic")
            .setMessageContent(Base64.encodeToString(content.toByteArray(), Base64.DEFAULT))
        // 复制代码运行请自行打印 API 的返回值
        vm.viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = vm.client.pub(pubRequest)
                Log.d(TAG, "code = ${response.body.code} message=${response.body.errorMessage}")
                withContext(Dispatchers.Main) {
                    if (response.body.success) {
                        ToastUtils.showShort("success")
                    } else {
                        ToastUtils.showShort(response.body.errorMessage)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

     fun doRRPC(topic: String, content: String, devName: String) {
        doRRPC(topic, content.toByteArray(), devName)
    }

     fun doRRPC(topic: String, content: ByteArray, devName: String) {
        val request = RRpcRequest()
        request.setProductKey(PRODUCTKEY)
        request.setDeviceName(devName)
        request.setRequestBase64Byte(Base64.encodeToString(content, Base64.DEFAULT))
        request.setTimeout(8000)
        request.setTopic("/$PRODUCTKEY/$devName/user/$topic")
        request.setIotInstanceId(InstanceId)
        vm.viewModelScope.launch(Dispatchers.IO) {
            try {
                val response: RRpcResponse = vm.client.rRpc(request)
                Log.d(TAG, "response = ${response.body.code} message=${response.body.errorMessage}")
                withContext(Dispatchers.Main) {
                    if (response.body.success) {
                        val toString = Base64.decode(response.body.getPayloadBase64Byte(), Base64.DEFAULT).toString(Charsets.UTF_8)
                        when (topic) {
                            "home/dac" -> {
                                when (toString) {
                                    "off", "on" -> {
                                        val b = toString == "on"
                                        vm.dacOpen.value = b
                                        MMKV.defaultMMKV().encode(DAC_POWER_STATUS_KEY, b)
                                    }
                                    "+" -> {
                                        val i = min((vm.dacVol.value ?: 0) + 1, 30)
                                        vm.dacVol.value = i
                                        MMKV.defaultMMKV().encode(DAC_VOL_KEY, i)
                                    }
                                    "-" -> {
                                        val i = max((vm.dacVol.value ?: 0) - 1, 0)
                                        vm.dacVol.value = i
                                        MMKV.defaultMMKV().encode(DAC_VOL_KEY, i)
                                    }
                                    "input" -> {
                                        val dacInputSource =
                                            if (vm.dacInputSource.value == DacInputSource.USB) DacInputSource.COAXIAL
                                            else DacInputSource.USB
                                        vm.dacInputSource.value = dacInputSource
                                        MMKV.defaultMMKV().encode(DAC_SOURCE_KEY, dacInputSource.name)
                                    }
                                    "timerCancel" -> {
                                        vm.dacPowerOffTime.value = 0
                                        MMKV.defaultMMKV().encode(DAC_POWER_OFF_TIMER_KEY, 0L)
                                        dacTimerJob?.cancel()
                                    }
                                    else -> {
                                        if (toString.startsWith("timer:")) {
                                            val m = toString.removePrefix("timer:").toInt()
                                            val l = System.currentTimeMillis() + m * 60 * 1000L
                                            vm.dacPowerOffTime.value = l
                                            MMKV.defaultMMKV().encode(DAC_POWER_OFF_TIMER_KEY, l)
                                            dacTimerJob = vm.viewModelScope.launch {
                                                delay(l)
                                                vm.dacPowerOffTime.value = 0
                                                MMKV.defaultMMKV().encode(DAC_POWER_OFF_TIMER_KEY, 0L)
                                                vm.dacOpen.value = false
                                                MMKV.defaultMMKV().encode(DAC_POWER_STATUS_KEY, false)
                                            }
                                        } else {
                                            ToastUtils.showShort(toString)
                                        }
                                    }
                                }
                            }
                            "home/ac" -> {
                                // Model: 1 (YAW1F), Power: On, Mode: 0 (Auto), Temp: 25C, Fan: 0 (Auto), Turbo: Off, IFeel: Off, WiFi: Off, XFan: Off, Light: On, Sleep: Off, Swing(V) Mode: Manual, Swing(V): 0 (Last), Timer: Off, Display Temp: 0 (Off)
                                vm.acOpen.value = toString.contains("Power: On")
                                when (toString) {
                                    "off", "on" -> {
                                        val b = toString == "on"
                                        vm.acOpen.value = b
                                        MMKV.defaultMMKV().encode(AC_POWER_STATUS_KEY, b)
                                    }
                                    "+" -> {
                                        val i = min((vm.acTemp.value ?: 0) + 1, 30)
                                        vm.acTemp.value = i
                                        MMKV.defaultMMKV().encode(AC_TEMP_KEY, i)
                                    }
                                    "-" -> {
                                        val i = max((vm.acTemp.value ?: 0) - 1, 0)
                                        vm.acTemp.value = i
                                        MMKV.defaultMMKV().encode(AC_TEMP_KEY, i)
                                    }
                                    "model" -> {
                                        val dacInputSource = AcMode.values()[((vm.acMode.value?.ordinal ?: 0) + 1) % AcMode.values().size]
                                        vm.acMode.value = dacInputSource
                                        MMKV.defaultMMKV().encode(AC_MODE_KEY, dacInputSource.name)
                                    }
                                    else -> {
                                        LogUtils.d(TAG, toString)
                                        ToastUtils.showShort(toString)
                                    }
                                }
                            }
                            "iot/relay" -> {
                                when (toString) {
                                    "off", "on" -> {
                                        val b = toString == "on"
                                        vm.relayOpen.value = b
                                        MMKV.defaultMMKV().encode("relay", b)
                                    }
                                    else -> {
                                        LogUtils.d(TAG, toString)
                                        ToastUtils.showShort(toString)
                                    }
                                }
                            }
                            "settings" -> {
                                when (toString) {
                                    "clearPrefs" -> {
                                        ToastUtils.showShort("clear success")
                                    }
                                    else -> {
                                        LogUtils.d(TAG, toString)
                                        ToastUtils.showShort(toString)
                                    }
                                }
                            }
                            else -> {
                                ToastUtils.showShort(toString)
                            }
                        }
                    } else {
                        ToastUtils.showLong(response.body.errorMessage)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
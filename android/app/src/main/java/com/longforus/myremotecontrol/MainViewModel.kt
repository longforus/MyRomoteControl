package com.longforus.myremotecontrol

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliyun.iot20180120.Client
import com.aliyun.iot20180120.models.GetDeviceStatusRequest
import com.espressif.iot.esptouch.EsptouchTask
import com.espressif.iot.esptouch.IEsptouchResult
import com.espressif.iot.esptouch.util.ByteUtil
import com.espressif.iot.esptouch.util.TouchNetUtil
import com.longforus.myremotecontrol.bean.AcMode
import com.longforus.myremotecontrol.bean.DacInputSource
import com.longforus.myremotecontrol.bean.StateResult
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow



class MainViewModel : ViewModel() {


    val touchJob = MutableLiveData<Job>()

    fun doTouch(stateResult: StateResult, onResult: (IEsptouchResult) -> Unit) {
        touchJob.value?.cancel()
        val ssid: ByteArray = ByteUtil.getBytesByString(stateResult.ssid)
        val password: ByteArray = ByteUtil.getBytesByString(stateResult.pwd)
        val bssid: ByteArray = TouchNetUtil.parseBssid2bytes(stateResult.bssid)
        touchJob.value = viewModelScope.launch(Dispatchers.IO) {
            val mEsptouchTask = EsptouchTask(ssid, bssid, password, MyApp.app)
            try {
                mEsptouchTask.setPackageBroadcast(true)
                mEsptouchTask.setEsptouchListener {
                    viewModelScope.launch(Dispatchers.Main) {
                        onResult(it)
                    }
                }
                val result = mEsptouchTask.executeForResults(1)
                withContext(Dispatchers.Main) {
                    onResult(result[0])
                }
            } finally {
                mEsptouchTask.interrupt()
            }
        }
    }

    fun onChangeControlModel() {
        viewModelScope.launch {
            val isIr = !isIrModelStatusFlow.value
            isIrModelStatusFlow.emit(isIr)
            MMKV.defaultMMKV().encode("isIrModel", isIr)
        }
    }


    val client = kotlin.run {
        val config: com.aliyun.teaopenapi.models.Config = com.aliyun.teaopenapi.models.Config() // 您的AccessKey ID
            .setAccessKeyId(ALIYUN_AK) // 您的AccessKey Secret
            .setAccessKeySecret(ALIYUN_SK)
        // 访问的域名
        config.endpoint = "iot.cn-shanghai.aliyuncs.com"
        Client(config)
    }


    val deviceStatusFlow = MutableStateFlow("OFFLINE")
    val secondDeviceStatusFlow = MutableStateFlow("OFFLINE")
    val isIrModelStatusFlow = MutableStateFlow(MMKV.defaultMMKV().decodeBool("isIrModel", false))
    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val statusReq = GetDeviceStatusRequest().apply {
                    deviceName = DEVICENAME_8266
                    iotInstanceId = InstanceId
                    productKey = PRODUCTKEY
                }

                val resp = client.getDeviceStatus(statusReq)
                //OFFLINE
                deviceStatusFlow.emit(resp.body.data.status)
                delay(3 * 60 * 1000L)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val statusReq = GetDeviceStatusRequest().apply {
                    deviceName = DEVICENAME_32
                    iotInstanceId = InstanceId
                    productKey = PRODUCTKEY
                }
                val resp = client.getDeviceStatus(statusReq)
                //OFFLINE
                secondDeviceStatusFlow.emit(resp.body.data.status)
                delay(3 * 60 * 1000L)
            }
        }
    }


    var relayOpen = MutableLiveData(MMKV.defaultMMKV().decodeBool("relay", false))
    var dacOpen = MutableLiveData(MMKV.defaultMMKV().decodeBool(DAC_POWER_STATUS_KEY, false))
    var dacVol = MutableLiveData(MMKV.defaultMMKV().decodeInt(DAC_VOL_KEY, 0))
    var dacInputSource = MutableLiveData(
        DacInputSource.valueOf(
            MMKV.defaultMMKV().decodeString(DAC_SOURCE_KEY, DacInputSource.USB.name) ?: DacInputSource.USB.name
        )
    )
    var dacPowerOffTime = MutableLiveData(MMKV.defaultMMKV().decodeLong(DAC_POWER_OFF_TIMER_KEY, 0))

    var acOpen = MutableLiveData(MMKV.defaultMMKV().decodeBool(AC_POWER_STATUS_KEY, false))
    var acTemp = MutableLiveData(MMKV.defaultMMKV().decodeInt(AC_TEMP_KEY, 16))
    var acMode = MutableLiveData(
        AcMode.valueOf(
            MMKV.defaultMMKV().decodeString(AC_MODE_KEY, AcMode.COOL.name) ?: AcMode.COOL.name
        )
    )
    var acPowerOffTime = MutableLiveData(MMKV.defaultMMKV().decodeLong(AC_POWER_OFF_TIMER_KEY, 0))
}





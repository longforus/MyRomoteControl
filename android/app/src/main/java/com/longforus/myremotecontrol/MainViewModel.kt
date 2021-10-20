package com.longforus.myremotecontrol

import androidx.lifecycle.*
import com.aliyun.iot20180120.Client
import com.aliyun.iot20180120.models.GetDeviceStatusRequest
import com.aliyun.teaopenapi.models.Config
import com.longforus.myremotecontrol.bean.AcMode
import com.longforus.myremotecontrol.bean.DacInputSource
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {


    val client = kotlin.run {
        val config: Config = Config() // 您的AccessKey ID
            .setAccessKeyId(ALIYUN_AK) // 您的AccessKey Secret
            .setAccessKeySecret(ALIYUN_SK)
        // 访问的域名
        config.endpoint = "iot.cn-shanghai.aliyuncs.com"
        Client(config)
    }


    val deviceStatusFlow = MutableStateFlow("OFFLINE")


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
    }


    var deviceName = MutableLiveData(MMKV.defaultMMKV().decodeString("deviceName", DEVICENAME_8266) ?: DEVICENAME_8266)
    var relayOpen = MutableLiveData(MMKV.defaultMMKV().decodeBool("${deviceName.value}:relay", false))
    var dacOpen = MutableLiveData(MMKV.defaultMMKV().decodeBool(DAC_POWER_STATUS_KEY, false))
    var dacVol = MutableLiveData(MMKV.defaultMMKV().decodeInt(DAC_VOL_KEY, 0))
    var dacInputSource = MutableLiveData(
        DacInputSource.valueOf(
            MMKV.defaultMMKV().decodeString(DAC_SOURCE_KEY, DacInputSource.USB.name) ?: DacInputSource.USB.name
        )
    )
    var dacPowerOffTime = MutableLiveData(MMKV.defaultMMKV().decodeLong(DAC_POWER_OFF_TIMER_KEY, 0))

    var acOpen = MutableLiveData(MMKV.defaultMMKV().decodeBool(AC_POWER_STATUS_KEY, false))
    var acTemp = MutableLiveData(MMKV.defaultMMKV().decodeInt(AC_TEMP_KEY, 0))
    var acMode = MutableLiveData(
        AcMode.valueOf(
            MMKV.defaultMMKV().decodeString(AC_MODE_KEY, AcMode.COOL.name) ?: AcMode.COOL.name
        )
    )
    var acPowerOffTime = MutableLiveData(MMKV.defaultMMKV().decodeLong(AC_POWER_OFF_TIMER_KEY, 0))
}
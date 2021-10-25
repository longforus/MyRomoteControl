package com.longforus.myremotecontrol

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliyun.iot20180120.Client
import com.aliyun.iot20180120.models.GetDeviceStatusRequest
import com.aliyun.teaopenapi.models.Config
import com.espressif.iot.esptouch.EsptouchTask
import com.espressif.iot.esptouch.IEsptouchResult
import com.espressif.iot.esptouch.util.ByteUtil
import com.espressif.iot.esptouch.util.TouchNetUtil
import com.longforus.myremotecontrol.bean.AcMode
import com.longforus.myremotecontrol.bean.DacInputSource
import com.longforus.myremotecontrol.bean.StateResult
import com.longforus.myremotecontrol.util.LockUtil
import com.longforus.myremotecontrol.util.LogUtils
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "MainViewModel"


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


    val client = kotlin.run {
        val config: Config = Config() // 您的AccessKey ID
            .setAccessKeyId(ALIYUN_AK) // 您的AccessKey Secret
            .setAccessKeySecret(ALIYUN_SK)
        // 访问的域名
        config.endpoint = "iot.cn-shanghai.aliyuncs.com"
        Client(config)
    }


    val deviceStatusFlow = MutableStateFlow("OFFLINE")
    val secondDeviceStatusFlow = MutableStateFlow("OFFLINE")

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
    var acTemp = MutableLiveData(MMKV.defaultMMKV().decodeInt(AC_TEMP_KEY, 0))
    var acMode = MutableLiveData(
        AcMode.valueOf(
            MMKV.defaultMMKV().decodeString(AC_MODE_KEY, AcMode.COOL.name) ?: AcMode.COOL.name
        )
    )
    var acPowerOffTime = MutableLiveData(MMKV.defaultMMKV().decodeLong(AC_POWER_OFF_TIMER_KEY, 0))
}

fun openLockCommand(board: Int, lock: Int): String {
    val lockOrder = ByteArray(7)
    lockOrder[0] = (-86).toByte()
    lockOrder[1] = 85.toByte()
    lockOrder[2] = 3.toByte()
    lockOrder[3] = board.toByte()
    lockOrder[4] = 80.toByte()
    lockOrder[5] = (lock.toByte() - 1).toByte()
    lockOrder[6] = LockUtil.calcCrc8(lockOrder, 0, 6)
    val byteToStr = LockUtil.byteToStr(
        lockOrder.size,
        lockOrder
    )
    LogUtils.d(
        TAG,
        "open locker : $board -> $lock  \n send data  =  $byteToStr"
    )
    return byteToStr
}




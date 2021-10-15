package com.longforus.myremotecontrol

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.longforus.myremotecontrol.bean.DacInputSource
import com.tencent.mmkv.MMKV

class MainViewModel:ViewModel() {
    var deviceName  = MutableLiveData(MMKV.defaultMMKV().decodeString("deviceName", DEVICENAME_8266) ?: DEVICENAME_8266)
    var relayOpen  = MutableLiveData(MMKV.defaultMMKV().decodeBool("${deviceName.value}:relay",false))
    var dacOpen  = MutableLiveData(MMKV.defaultMMKV().decodeBool(DAC_POWER_STATUS_KEY,false))
    var dacVol  = MutableLiveData(MMKV.defaultMMKV().decodeInt(DAC_VOL_KEY,0))
    var dacInputSource  = MutableLiveData(DacInputSource.valueOf(MMKV.defaultMMKV().decodeString(DAC_SOURCE_KEY,DacInputSource.USB.name) ?: DacInputSource.USB.name))
    var dacPowerOffTime  = MutableLiveData(MMKV.defaultMMKV().decodeLong(DAC_POWER_OFF_TIMER_KEY,0))
}
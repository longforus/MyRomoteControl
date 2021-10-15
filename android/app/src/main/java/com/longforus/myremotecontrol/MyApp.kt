package com.longforus.myremotecontrol

import android.app.Application
import android.util.Log
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVLogLevel

class MyApp:Application() {

    companion object{
        lateinit var app: MyApp
    }

    val TAG = "MyApp"
    override fun onCreate() {
        super.onCreate()
        app = this
        Log.d(TAG, "mmkv init :${
            MMKV.initialize(this, MMKVLogLevel.LevelDebug)
        } ")
    }
}
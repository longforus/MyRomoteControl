package com.longforus.myremotecontrol


import android.Manifest
import android.location.LocationManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.TapAndPlay
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.location.LocationManagerCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.longforus.myremotecontrol.bean.AcMode
import com.longforus.myremotecontrol.bean.DacInputSource
import com.longforus.myremotecontrol.bean.IconScreens
import com.longforus.myremotecontrol.bean.StateResult
import com.longforus.myremotecontrol.screen.HomeScreen
import com.longforus.myremotecontrol.screen.OtherScreen
import com.longforus.myremotecontrol.ui.theme.Purple500
import com.longforus.myremotecontrol.ui.theme.myremotecontrolTheme
import com.longforus.myremotecontrol.util.ConsumerIrManagerApi
import com.longforus.myremotecontrol.util.MyTouchNetUtil
import com.longforus.myremotecontrol.util.StatusBarUtil
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.callback.RequestCallback
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.suspendCancellableCoroutine


val LocalNavCtrl = staticCompositionLocalOf<NavHostController?> {
    null
}
const val DEVICENAME_8266 = "9636868"
const val DEVICENAME_32 = "1287495"

const val DAC_POWER_OFF_TIMER_KEY = "dac:powerOff"

const val DAC_POWER_STATUS_KEY = "dac:power"

const val DAC_VOL_KEY = "dac:vol"

const val DAC_SOURCE_KEY = "dac:input"

const val AC_POWER_STATUS_KEY = "ac:power"
const val AC_TEMP_KEY = "ac:temp"
const val AC_MODE_KEY = "ac:mode"
const val AC_POWER_OFF_TIMER_KEY = "ac:powerOff"


class MainActivity : AppCompatActivity() {


    private val vm by viewModels<MainViewModel>()


    val TAG = "MainActivity"

    private val clientHolder by lazy { ClientHolder(vm) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparentStatusBar(this)
        ConsumerIrManagerApi.getConsumerIrManager(this)
        val l = vm.dacPowerOffTime.value ?: 0
        if (l != 0L && l < System.currentTimeMillis() && vm.dacOpen.value == true) {
            vm.dacPowerOffTime.value = 0
            MMKV.defaultMMKV().encode(DAC_POWER_OFF_TIMER_KEY, 0L)
            vm.dacOpen.value = false
            MMKV.defaultMMKV().encode(DAC_POWER_STATUS_KEY, false)
        }
        setContent {
            AppMainNavigation()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when(keyCode){
            KeyEvent.KEYCODE_VOLUME_UP->  clientHolder.doRRPC("home/dac", "+", DEVICENAME_8266)
            KeyEvent.KEYCODE_VOLUME_DOWN->  clientHolder.doRRPC("home/dac", "-", DEVICENAME_8266)
            else->   return super.onKeyDown(keyCode, event)
        }
        return true
    }


    @Composable
    fun AppMainNavigation() {
        val navController = rememberNavController()
        CompositionLocalProvider(LocalNavCtrl provides navController) {
            NavHost(navController, startDestination = IconScreens.Home.route) {
                // Bottom Nav
                composable(IconScreens.Home.route) {
                    ScaffoldScreen(navController, appBar = {
                        TopAppBar(title = {
                            val deviceStatus by vm.deviceStatusFlow.collectAsState()
                            val isIrModelStatusFlow by vm.isIrModelStatusFlow.collectAsState()
                            Text(
                                text =if(!isIrModelStatusFlow) "ESP8266 : $DEVICENAME_8266 $deviceStatus" else "IRModel",
                                fontSize = 20.sp,
                                modifier =   Modifier.clickable {
                                    vm.onChangeControlModel()
                                }
                            )
                        }, actions = {
                            Icon(Icons.Default.TapAndPlay, contentDescription = null, Modifier.clickable {
                                navController.navigate("espTouch?device=${DEVICENAME_8266}")
                            })
                            Spacer(modifier = Modifier.width(13.dp))
                            Icon(Icons.Default.Clear, contentDescription = null, Modifier.clickable {
                                clientHolder.doRRPC("settings", "clearPrefs", DEVICENAME_8266)
                            })
                        })
                    }) {
                        HomeScreen(navController, vm, clientHolder)
                    }
                }
                composable(IconScreens.Other.route) {
                    ScaffoldScreen(navController, appBar = {
                        TopAppBar(title = {
                            val status by vm.secondDeviceStatusFlow.collectAsState()
                            Text(
                                text = "ESP32 : $DEVICENAME_32 $status",
                                fontSize = 20.sp
                            )
                        }, actions = {
                            Icon(Icons.Default.TapAndPlay, contentDescription = null, Modifier.clickable {
                                navController.navigate("espTouch?device=${DEVICENAME_32}")
                            })
                            Spacer(modifier = Modifier.width(13.dp))
                            Icon(Icons.Default.Clear, contentDescription = null, Modifier.clickable {
                                clientHolder.doRRPC("settings", "clearPrefs", DEVICENAME_32)
                            })
                        })
                    }) {
                        val isOpen by vm.relayOpen.observeAsState(MMKV.defaultMMKV().decodeBool("relay", false))
                        OtherScreen(clientHolder, isOpen)
                    }
                }

                dialog("adjustValue?type={type}", arguments = listOf(navArgument("type") {
                    defaultValue = 0
                    type = NavType.IntType
                })) {
                    val type = it.arguments?.getInt("type", 0) ?: 0
                    AdjustDialog(type, navController)
                }
                dialog("espTouch?device={devId}", arguments = listOf(navArgument("devId") {
                    defaultValue = DEVICENAME_8266
                    type = NavType.StringType
                })) {
                    TouchDialog(navController, vm, ::check)
                }
            }
        }
    }


    @Composable
    fun AdjustDialog(type: Int = 0, navController: NavHostController) {
        var text by remember { mutableStateOf("") }
        var openDialog by remember { mutableStateOf(true) }
        if (!openDialog) {
            navController.navigateUp()
            return
        }
        val typeTitle = when (type) {
            1 -> "InputSource:0->USB 1->COAXIAL"
            2 -> "DacPower:0->off 1->on"
            3 -> "dacTimer"
            4 -> "acPower:0->off 1->on"
            5 -> "acMode:AUTO,COOL,DRY,FAN,HEAT"
            6 -> "ac temp(16..30)"
            else -> "vol(0..30)"
        }

        fun setAdjustResult(type: Int, text: String) {
            when (type) {
                1 -> {
                    vm.dacInputSource.value = if (text == "1") DacInputSource.COAXIAL else DacInputSource.USB
                    MMKV.defaultMMKV().encode(DAC_SOURCE_KEY, vm.dacInputSource.value?.name)
                }
                2 -> {
                    val b = text == "1"
                    vm.dacOpen.value = b
                    MMKV.defaultMMKV().encode(DAC_POWER_STATUS_KEY, b)
                }
                3 -> {
                    clientHolder.doRRPC("home/dac", "timer:$text", DEVICENAME_8266)
                }
                4 -> {
                    val b = text == "1"
                    vm.acOpen.value = b
                    MMKV.defaultMMKV().encode(AC_POWER_STATUS_KEY, b)
                }
                5 -> {
                    val toInt = text.toInt()
                    val acMode = AcMode.values()[toInt]
                    vm.acMode.value = acMode
                    MMKV.defaultMMKV().encode(AC_MODE_KEY, acMode.name)
                }
                6 -> {
                    val toInt = text.toInt()
                    vm.acTemp.value = toInt
                    MMKV.defaultMMKV().encode(AC_TEMP_KEY, toInt)
                }
                else -> {
                    val toInt = text.toInt()
                    vm.dacVol.value = toInt
                    MMKV.defaultMMKV().encode(DAC_VOL_KEY, toInt)
                }
            }
            openDialog = false
        }
        myremotecontrolTheme {
            Dialog(onDismissRequest = {
                openDialog = false
            }) {
                Surface {
                    Column {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(text = typeTitle) },
                            maxLines = 1,
                            modifier = Modifier.padding(20.dp),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    setAdjustResult(type, text)
                                }
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            Button(onClick = {
                                openDialog = false
                            }) {
                                Text(text = "cancel")
                            }
                            Button(onClick = {
                                setAdjustResult(type, text)
                            }) {
                                Text(text = "set")
                            }
                        }
                    }
                }
            }
        }

    }


    private suspend fun check(): StateResult {
        var result: StateResult = checkPermission()
        if (!result.permissionGranted) {
            return result
        }
        result = checkLocation()
        result.permissionGranted = true
        if (result.locationRequirement) {
            return result
        }
        result = checkWifi()
        result.permissionGranted = true
        result.locationRequirement = false
        return result
    }

    private fun checkLocation(): StateResult {
        val result = StateResult()
        result.locationRequirement = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

            val manager = getSystemService(LocationManager::class.java)
            val enable = manager != null && LocationManagerCompat.isLocationEnabled(manager)
            if (!enable) {
                result.message = "请打开 GPS 以获取 Wi-Fi 信息。"
                return result
            }
        }
        result.locationRequirement = false
        return result
    }


    private fun checkWifi(): StateResult {
        val result = StateResult()
        result.wifiConnected = false
        val mWifiManager = application.getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo = mWifiManager.getConnectionInfo()
        val connected = MyTouchNetUtil.isWifiConnected(mWifiManager)
        if (!connected) {
            result.message = "请先连上 Wi-Fi"
            return result
        }
        val ssid = MyTouchNetUtil.getSsidString(wifiInfo)
        val ipValue = wifiInfo.ipAddress
        if (ipValue != 0) {
            result.address = MyTouchNetUtil.getAddress(wifiInfo.ipAddress)
        } else {
            result.address = MyTouchNetUtil.getIPv4Address()
            if (result.address == null) {
                result.address = MyTouchNetUtil.getIPv6Address()
            }
        }
        result.wifiConnected = true
        result.message = ""
        result.is5G = MyTouchNetUtil.is5G(wifiInfo.frequency)
        if (result.is5G) {
            result.message = "当前连接的是 5G Wi-Fi, 设备仅支持 2.4G Wi-Fi"
        }
        result.ssid = ssid
        result.ssidBytes = MyTouchNetUtil.getRawSsidBytesOrElse(wifiInfo, ssid.toByteArray())
        result.bssid = wifiInfo.bssid
        return result
    }


    suspend fun checkPermission(): StateResult = suspendCancellableCoroutine {
        val result = StateResult()
        PermissionX.init(this).permissions(Manifest.permission.ACCESS_FINE_LOCATION).request(
            RequestCallback { allGranted, grantedList, deniedList ->
                result.permissionGranted = allGranted
                it.resumeWith(Result.success(result))
            }
        )
    }


    @Composable
    fun ScaffoldScreen(navController: NavHostController, appBar: @Composable (() -> Unit)? = null, screen: @Composable () ->
    Unit) {
        val bottomNavigationItems = listOf(
            IconScreens.Home,
            IconScreens.Other,
        )
        myremotecontrolTheme {
            Scaffold(
                bottomBar = { BottomAppNavBar(navController, bottomNavigationItems) },
                content = { screen() },
                topBar = {
                    appBar?.invoke()
                },
                modifier = Modifier.padding(top = StatusBarUtil.getStatusBarHeight(LocalContext.current))
            )
        }
    }


    @Composable
    fun BottomAppNavBar(navController: NavHostController, bottomNavigationItems: List<IconScreens>) {
        BottomAppBar(
            backgroundColor = Color.White,
            contentColor = Purple500,
            elevation = 10.dp,
        ) {
            bottomNavigationItems.forEach { screen ->
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                BottomNavigationItem(
                    icon = { Icon(imageVector = screen.icon, contentDescription = null) },
                    selected = currentRoute == screen.route,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    label = {
                        Text(text = screen.label)
                    },
                    alwaysShowLabel = false
                )
            }
        }

    }


}

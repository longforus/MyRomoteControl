package com.longforus.myremotecontrol

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.aliyun.iot20180120.Client
import com.aliyun.iot20180120.models.PubRequest
import com.aliyun.iot20180120.models.RRpcRequest
import com.aliyun.iot20180120.models.RRpcResponse
import com.aliyun.teaopenapi.models.Config
import com.longforus.myremotecontrol.bean.IconScreens
import com.longforus.cpix.util.StatusBarUtil
import com.longforus.myremotecontrol.bean.DacInputSource
import com.longforus.myremotecontrol.ui.theme.myremotecontrolTheme
import com.longforus.myremotecontrol.ui.theme.Purple500
import com.longforus.myremotecontrol.ui.theme.Purple700
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import kotlin.math.max
import kotlin.math.min


val LocalNavCtrl = staticCompositionLocalOf<NavHostController?> {
    null
}
const val DEVICENAME_8266 = "9636868"
const val DEVICENAME_32 = "1287495"

const val DAC_POWER_OFF_TIMER_KEY = "dac:powerOff"

const val DAC_POWER_STATUS_KEY = "dac:power"

const val DAC_VOL_KEY = "dac:vol"

const val DAC_SOURCE_KEY = "dac:input"



class MainActivity : ComponentActivity() {


    private val vm by viewModels<MainViewModel>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss")

    private var dacTimerJob:Job? = null
    //    用户登录名称 longforus@1904156388743966.onaliyun.com
//    AccessKey ID LTAI5tQtacsmAXpfCWqSTZBV
//    AccessKey Secret ObwS6Yfy76DdtmJ6BSY7OoZbIRXJ6i
    val TAG = "MainActivity"
    private val client by lazy {
        val config: Config = Config() // 您的AccessKey ID
            .setAccessKeyId(ALIYUN_AK) // 您的AccessKey Secret
            .setAccessKeySecret(ALIYUN_SK)
        // 访问的域名
        config.endpoint = "iot.cn-shanghai.aliyuncs.com"
        Client(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtil.transparentStatusBar(this)
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


    @Composable
    fun AppMainNavigation() {
        val navController = rememberNavController()
        CompositionLocalProvider(LocalNavCtrl provides navController) {
            NavHost(navController, startDestination = IconScreens.Home.route) {
                // Bottom Nav

                composable(IconScreens.Home.route) {
                    val bottomNavigationItems = listOf(
                        IconScreens.Home,
                        IconScreens.Other,
                    )
                    myremotecontrolTheme {
                        Scaffold(
                            bottomBar = { BottomAppNavBar(navController, bottomNavigationItems) },
                            content = { HomeScreen(navController) },
                            topBar = {
                                TopAppBar(title = {
                                    Text(
                                        text = "RemoteControl",
                                        fontSize = 20.sp
                                    )
                                })
                            },
                            modifier = Modifier.padding(top = 34.dp)
                        )
                    }
                }
                composable(IconScreens.Other.route) {
                    ScaffoldScreen(navController) {
                        OtherScreen()
                    }
                }

                dialog("adjustValue?type={type}", arguments = listOf(navArgument("type") {
                    defaultValue = 0
                    type = NavType.IntType
                })) {
                    val type = it.arguments?.getInt("type", 0) ?: 0
                    AdjustDialog(type, navController)
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
                    doRRPC("home/dac", "timer:$text", DEVICENAME_8266)
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


    @Composable
    fun OtherScreen() {
        Column(Modifier.padding(top = 33.dp, start = 10.dp, end = 10.dp)) {
            val devName by vm.deviceName.observeAsState(DEVICENAME_8266)
            Text(
                text = "target device:$devName", Modifier.clickable {
                    val s: String = if (devName == DEVICENAME_32) DEVICENAME_8266 else DEVICENAME_32
                    vm.deviceName.value = s
                    MMKV.defaultMMKV().encode("deviceName", s)
                },
                fontSize = 30.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "relay")
            Spacer(modifier = Modifier.height(20.dp))
            val isOpen by vm.relayOpen.observeAsState(MMKV.defaultMMKV().decodeBool("${vm.deviceName.value}:relay", false))
            RelayRow(isOpen) {
                this@MainActivity.doRRPC("iot/relay", if (isOpen) "off" else "on", devName)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "power")
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = {
                    doRRPC("iot/power", "on", devName)
                }) {
                    Text(text = "on")
                }
                Button(onClick = {
                    doRRPC("iot/power", "off", devName)
                }) {
                    Text(text = "off")
                }
                Button(onClick = {
                    doRRPC("iot/power", "sub", devName)
                }) {
                    Text(text = "-")
                }
                Button(onClick = {
                    doRRPC("iot/power", "add", devName)
                }) {
                    Text(text = "+")
                }
            }
        }
    }

    @Composable
    private fun RelayRow(isOpen: Boolean, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onClick, shape = CircleShape, modifier = Modifier
                    .height(100.dp)
                    .width(100.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = if (isOpen) Color.Green else Color.LightGray)
            ) {
                Text(text = if (isOpen) "off" else "on")
            }
        }
    }


    @Composable
    fun HomeScreen(navController: NavHostController) {
        Column(Modifier.padding(10.dp)) {
            Text(text = "Dac")
            val dacIsOpen by vm.dacOpen.observeAsState(MMKV.defaultMMKV().decodeBool(DAC_POWER_STATUS_KEY, false))
            val volume by vm.dacVol.observeAsState(MMKV.defaultMMKV().decodeInt(DAC_VOL_KEY, 0))
            val dacTime by vm.dacPowerOffTime.observeAsState(MMKV.defaultMMKV().decodeLong(DAC_POWER_OFF_TIMER_KEY, 0))
            //COAXIAL
            val dacInputSource by vm.dacInputSource.observeAsState(
                DacInputSource.valueOf(
                    MMKV.defaultMMKV().decodeString(DAC_SOURCE_KEY, "USB") ?: "USB"
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xffefefef))
            ) {
                val (tvVol, tvSource) = createRefs()
                if (dacIsOpen) {
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontSize = 123.sp, fontWeight = FontWeight.Bold)) {
                            append(volume.toString())
                        }
                        append("vol")
                    },
                        modifier = Modifier
                            .constrainAs(tvVol) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start)
                                end.linkTo(tvSource.start)
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = {
                                    navController.navigate("adjustValue?type=0")
                                })
                            }
                    )
                    Text(dacInputSource.name, fontSize = 30.sp, fontWeight = FontWeight.W400, modifier = Modifier
                        .constrainAs(tvSource) {
                            start.linkTo(tvVol.end)
                            end.linkTo(parent.end)
                            baseline.linkTo(tvVol.baseline)
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = {
                                navController.navigate("adjustValue?type=1")
                            })
                        })
                } else {
                    Text(buildAnnotatedString {
                        withStyle(style = SpanStyle(fontSize = 123.sp, fontWeight = FontWeight.Bold, color = Color.Gray)) {
                            append("off")
                        }
                    },
                        modifier = Modifier
                            .constrainAs(tvVol) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start, margin = 30.dp)
                                end.linkTo(tvSource.start)
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = {
                                    navController.navigate("adjustValue?type=2")
                                })
                            }
                    )
                }

            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp)
            ) {
                Button(
                    onClick = {
                        doRRPC("home/dac", if (dacIsOpen) "off" else "on", DEVICENAME_8266)
                    }, modifier = Modifier
                        .height(80.dp)
                        .width(80.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = {
                                navController.navigate("adjustValue?type=2")
                            })
                        },
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (dacIsOpen) Purple500 else Color.LightGray)
                ) {
                    Text(text = if (dacIsOpen) "off" else "on")
                }
                Column(
                    Modifier
                        .width(100.dp)
                        .height(100.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    Button(
                        onClick = {
                            doRRPC("home/dac", "+", DEVICENAME_8266)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = dacIsOpen
                    ) {
                        Text(text = "+")
                    }
                    Button(
                        onClick = {
                            doRRPC("home/dac", "-", DEVICENAME_8266)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        enabled = dacIsOpen
                    ) {
                        Text(text = "-")
                    }
                }
                Button(
                    onClick = {
                        doRRPC("home/dac", "input", DEVICENAME_8266)
                    },
                    modifier = Modifier
                        .height(80.dp)
                        .width(80.dp),
                    enabled = dacIsOpen,
                ) {
                    Text(text = "Input")
                }

            }
            Button(
                onClick = {
                    if (dacTime > System.currentTimeMillis()) {
                        doRRPC("home/dac", "timerCancel", DEVICENAME_8266)
                    } else {
                        navController.navigate("adjustValue?type=3")
                    }
                }, modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(start = 20.dp, end = 20.dp),
                enabled = dacIsOpen,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (dacTime > System.currentTimeMillis()) Purple500 else Purple700)
            ) {
                Text(text = if (dacTime > System.currentTimeMillis()) timeFormat.format(dacTime) else "Timer")
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "AC")
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
//                Button(onClick = {
//                    doRRPC("iot/power", "on", devName)
//                }) {
//                    Text(text = "on")
//                }
            }
        }
    }


    @Composable
    fun ScaffoldScreen(navController: NavHostController, float: @Composable (() -> Unit)? = null, screen: @Composable () -> Unit) {
        val bottomNavigationItems = listOf(
            IconScreens.Home,
            IconScreens.Other,
        )
        myremotecontrolTheme {
            Scaffold(
                bottomBar = { BottomAppNavBar(navController, bottomNavigationItems) },
                content = { screen() },
                floatingActionButton = { float?.invoke() }
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

    private fun doRequest(topic: String, content: String, devName: String) {
        val pubRequest: PubRequest = PubRequest()
            .setIotInstanceId(InstanceId)
            .setProductKey(PRODUCTKEY)
            .setQos(0)
            .setTopicFullName("/${PRODUCTKEY}/$devName/user/$topic")
            .setMessageContent(Base64.encodeToString(content.toByteArray(), Base64.DEFAULT))
        // 复制代码运行请自行打印 API 的返回值
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = client.pub(pubRequest)
                Log.d(TAG, "code = ${response.body.code} message=${response.body.errorMessage}")
                withContext(Dispatchers.Main) {
                    if (response.body.success) {
                        Toast.makeText(this@MainActivity, "success", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, response.body.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun doRRPC(topic: String, content: String, devName: String) {
        val request = RRpcRequest()
        request.setProductKey(PRODUCTKEY)
        request.setDeviceName(devName)
        request.setRequestBase64Byte(Base64.encodeToString(content.toByteArray(), Base64.DEFAULT))
        request.setTimeout(8000)
        request.setTopic("/$PRODUCTKEY/$devName/user/$topic")
        request.setIotInstanceId(InstanceId)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response: RRpcResponse = client.rRpc(request)
                Log.d(TAG, "response = ${response.body.code} message=${response.body.errorMessage}")
                val toString = Base64.decode(response.body.getPayloadBase64Byte(), Base64.DEFAULT).toString(Charsets.UTF_8)
                withContext(Dispatchers.Main) {
                    if (response.body.success) {
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
                                            dacTimerJob = lifecycleScope.launch {
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
                            else -> {
                                ToastUtils.showShort(toString)
                            }
                        }
                    } else {
                        ToastUtils.showShort(response.body.errorMessage)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}


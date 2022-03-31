package com.longforus.myremotecontrol

import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.longforus.myremotecontrol.bean.StateResult
import com.longforus.myremotecontrol.ui.theme.myremotecontrolTheme
import com.longforus.myremotecontrol.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private val TAG = "TouchDialog"

@Composable
fun TouchDialog(navController: NavHostController = rememberNavController(),vm: MainViewModel, check:suspend ()->StateResult) {
    var confirmEnable by remember { mutableStateOf(false) }
    var stateResult by remember { mutableStateOf(StateResult()) }
    var openDialog by remember { mutableStateOf(true) }
    var ssid by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    val touchJob by vm.touchJob.observeAsState()
    if (!openDialog) {
        navController.navigateUp()
        return
    }
    LaunchedEffect(Unit) {
        stateResult = check()
    }

    val current = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(openDialog, current) {
        if (!openDialog) {
            return@DisposableEffect onDispose {
            }
        }
        val changeFlow = MutableStateFlow("")
        val netChangeReceiver = NetChangeReceiver(changeFlow)
        val filter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        changeFlow.onEach {
            stateResult = check()
            confirmEnable = false
            ssid = stateResult.ssid ?: ""
            if (stateResult.wifiConnected) {
                confirmEnable = true
                if (stateResult.is5G) {
                    stateResult.message = "设备不支持 5G Wi-Fi, 请确认当前连接的 Wi-Fi 为 2.4G"
                }
            } else {
                if (touchJob != null) {
                    touchJob?.cancel()
                    vm.touchJob.value = null
                    stateResult.message = "Wi-Fi 已断开或发生了变化"
                }
            }
        }.launchIn(current.lifecycleScope)
        context.registerReceiver(netChangeReceiver, filter)
        onDispose {
            context.unregisterReceiver(netChangeReceiver)
        }
    }
    myremotecontrolTheme {
        Dialog(onDismissRequest = {
            openDialog = false
            vm.touchJob.value?.cancel()
            vm.touchJob.value = null
        }, DialogProperties(dismissOnBackPress = touchJob == null, dismissOnClickOutside = touchJob == null)) {
            Surface(shape = RoundedCornerShape(5.dp), modifier = Modifier.height(320.dp)) {
                Box {
                    Column {
                        OutlinedTextField(
                            value = ssid,
                            onValueChange = {
                                ssid = it
                            },
                            label = {
                                Text(text = "SSID")
                            },
                            singleLine = true, modifier = Modifier.padding(20.dp),
                        )
                        OutlinedTextField(
                            value = pwd,
                            onValueChange = {
                                pwd = it
                            },
                            label = {
                                Text(text = "PASSWORD")
                            },
                            singleLine = true, modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                        )
                        Text(text = stateResult.message.toString(), modifier = Modifier.padding(20.dp))
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
                                Text(text = "Cancel")
                            }
                            Button(onClick = {
                                stateResult.ssid = ssid
                                stateResult.pwd = pwd
                                if (stateResult.ssid.isNullOrEmpty()) {
                                    ToastUtils.nonNull("SSID")
                                    return@Button
                                }
                                if (stateResult.pwd.isNullOrEmpty()) {
                                    ToastUtils.nonNull("PASSWORD")
                                    return@Button
                                }
                                vm.doTouch(stateResult) {
                                    LogUtils.i(TAG, "EspTouchResult: $it")
                                    stateResult.message = it.bssid + " is connected to the wifi"
                                    ToastUtils.showShort(stateResult.message)
                                    openDialog = false
                                }
                            }, enabled = confirmEnable) {
                                Text(text = "Set")
                            }
                        }
                    }
                    if (touchJob != null) {
                        Surface(color = Color(0xddffffff), modifier = Modifier.fillMaxSize()) {
                            Column(verticalArrangement = Arrangement.SpaceAround, horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Button(onClick = {
                                    vm.touchJob.value?.cancel()
                                    vm.touchJob.value = null
                                }) {
                                    Text(text = "Stop")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



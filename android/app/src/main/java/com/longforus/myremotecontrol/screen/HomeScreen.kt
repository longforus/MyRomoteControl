package com.longforus.myremotecontrol.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.longforus.myremotecontrol.*
import com.longforus.myremotecontrol.R
import com.longforus.myremotecontrol.bean.AcMode
import com.longforus.myremotecontrol.bean.DacInputSource
import com.longforus.myremotecontrol.ui.LongClickButton
import com.longforus.myremotecontrol.ui.theme.Purple500
import com.longforus.myremotecontrol.ui.theme.Purple700
import com.tencent.mmkv.MMKV
import java.text.SimpleDateFormat

private val timeFormat = SimpleDateFormat("HH:mm:ss")

val TAG = "HomeScreen"

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview(showSystemUi = true)
fun HomeScreen(navController: NavHostController = rememberNavController(), viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose
    .viewModel(), clientHolder: ClientHolder = ClientHolder(viewModel)
) {
    Column(Modifier.padding(10.dp)) {
        DacSpace(navController,viewModel,clientHolder)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "AC")
        Spacer(modifier = Modifier.height(20.dp))
        val acIsOpen by viewModel.acOpen.observeAsState(MMKV.defaultMMKV().decodeBool(AC_POWER_STATUS_KEY, false))
        val acTemp by viewModel.acTemp.observeAsState(MMKV.defaultMMKV().decodeInt(AC_TEMP_KEY, 0))
        val acTime by viewModel.acPowerOffTime.observeAsState(MMKV.defaultMMKV().decodeLong(AC_POWER_OFF_TIMER_KEY, 0))
        val acMode by viewModel.acMode.observeAsState(
            AcMode.valueOf(
                MMKV.defaultMMKV().decodeString(AC_MODE_KEY, AcMode.COOL.name) ?: AcMode.COOL.name
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
            if (acIsOpen) {
                Text(
                    buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = 123.sp, fontWeight = FontWeight.Bold)) {
                        append(acTemp.toString())
                    }
                    append("°C")
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
                                switchACPower(viewModel)
                            })
                        }
                )
                Text(acMode.name, fontSize = 30.sp, fontWeight = FontWeight.W400, modifier = Modifier
                    .constrainAs(tvSource) {
                        start.linkTo(tvVol.end)
                        end.linkTo(parent.end)
                        baseline.linkTo(tvVol.baseline)
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            navController.navigate("adjustValue?type=5")
                        })
                    })
            } else {
                Text(
                    buildAnnotatedString {
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
                                switchDacPower(viewModel)
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
                    clientHolder.doRRPC("home/ac", if (acIsOpen) "off" else "on", DEVICENAME_8266)
                }, modifier = Modifier
                    .height(80.dp)
                    .width(80.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            switchACPower(viewModel)
                        })
                    },
                colors = ButtonDefaults.buttonColors(backgroundColor = if (acIsOpen) Purple500 else Color.LightGray)
            ) {
                Text(text = if (acIsOpen) "off" else "on")
            }
            Column(
                Modifier
                    .width(100.dp)
                    .height(100.dp),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Button(
                    onClick = {
                        clientHolder.doRRPC("home/ac", "+", DEVICENAME_8266)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = acIsOpen
                ) {
                    Text(text = "+")
                }
                Button(
                    onClick = {
                        clientHolder.doRRPC("home/ac", "-", DEVICENAME_8266)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    enabled = acIsOpen
                ) {
                    Text(text = "-")
                }
            }
            Button(
                onClick = {
                    clientHolder.doRRPC("home/ac", "model", DEVICENAME_8266)
                },
                modifier = Modifier
                    .height(80.dp)
                    .width(80.dp),
                enabled = acIsOpen,
            ) {
                Text(text = "Input")
            }

        }
//            Button(
//                onClick = {
//                    if (dacTime > System.currentTimeMillis()) {
//                        doRRPC("home/dac", "timerCancel", DEVICENAME_8266)
//                    } else {
//                        navController.navigate("adjustValue?type=3")
//                    }
//                }, modifier = Modifier
//                    .fillMaxWidth()
//                    .height(50.dp)
//                    .padding(start = 20.dp, end = 20.dp),
//                enabled = acIsOpen,
//                colors = ButtonDefaults.buttonColors(backgroundColor = if (dacTime > System.currentTimeMillis()) Purple500 else Purple700)
//            ) {
//                Text(text = if (dacTime > System.currentTimeMillis()) timeFormat.format(dacTime) else "Timer")
//            }
    }
}

@ExperimentalFoundationApi
@Composable
private fun DacSpace(navController: NavHostController, viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(), clientHolder: ClientHolder = ClientHolder(viewModel)) {
    Text(text = "Dac")
    val dacIsOpen by viewModel.dacOpen.observeAsState(MMKV.defaultMMKV().decodeBool(DAC_POWER_STATUS_KEY, false))
    val volume by viewModel.dacVol.observeAsState(MMKV.defaultMMKV().decodeInt(DAC_VOL_KEY, 0))
    val dacTime by viewModel.dacPowerOffTime.observeAsState(MMKV.defaultMMKV().decodeLong(DAC_POWER_OFF_TIMER_KEY, 0))
    //COAXIAL
    val dacInputSource by viewModel.dacInputSource.observeAsState(
        DacInputSource.valueOf(
            MMKV.defaultMMKV().decodeString(DAC_SOURCE_KEY, DacInputSource.USB.name) ?: DacInputSource.USB.name
        )
    )
    Spacer(modifier = Modifier.height(10.dp))
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xffefefef))
    ) {
        val (tvVol, tvSource, icon) = createRefs()
        if (dacIsOpen) {
            Text(
                buildAnnotatedString {
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
            fun onChangeInputSource() {
                viewModel.dacInputSource.value =
                    if (viewModel.dacInputSource.value == DacInputSource.USB) DacInputSource.COAXIAL else DacInputSource.USB
                MMKV
                    .defaultMMKV()
                    .encode(DAC_SOURCE_KEY, viewModel.dacInputSource.value?.name)
            }
            Text(dacInputSource.name, fontSize = 30.sp, fontWeight = FontWeight.W400, modifier = Modifier
                .constrainAs(tvSource) {
                    start.linkTo(tvVol.end)
                    end.linkTo(parent.end)
                    baseline.linkTo(tvVol.baseline)
                }
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        onChangeInputSource()
                    })
                })
            Image(
                painterResource(if (dacInputSource == DacInputSource.USB) R.drawable.usb else R.drawable.coaxial),
                contentDescription = null,
                Modifier
                    .constrainAs(icon) {
                        bottom.linkTo(tvSource.top, 8.dp)
                        start.linkTo(tvSource.start)
                        end.linkTo(tvSource.end)
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            onChangeInputSource()
                        })
                    })
        } else {
            Text(
                buildAnnotatedString {
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
                            switchDacPower(viewModel)
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
        LongClickButton(
             modifier = Modifier
                 .height(80.dp)
                 .width(80.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = if (dacIsOpen) Purple500 else Color.LightGray),
            onClick = {
                clientHolder.doRRPC("home/dac", if (dacIsOpen) "off" else "on", DEVICENAME_8266)
            },
            onLongClick = {
                switchDacPower(viewModel)
            }
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
                    clientHolder.doRRPC("home/dac", "+", DEVICENAME_8266)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = dacIsOpen
            ) {
                Text(text = "+")
            }
            Button(
                onClick = {
                    clientHolder.doRRPC("home/dac", "-", DEVICENAME_8266)
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
                clientHolder.doRRPC("home/dac", "input", DEVICENAME_8266)
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
                clientHolder.doRRPC("home/dac", "timerCancel", DEVICENAME_8266)
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
}



private fun switchACPower(viewModel: MainViewModel) {
    val b = !(viewModel.acOpen.value ?: false)
    viewModel.acOpen.value = b
    MMKV.defaultMMKV().encode(AC_POWER_STATUS_KEY, b)
}



private fun switchDacPower(viewModel: MainViewModel) {
    val b = !(viewModel.dacOpen.value ?: false)
    viewModel.dacOpen.value = b
    MMKV.defaultMMKV().encode(DAC_POWER_STATUS_KEY, b)
}


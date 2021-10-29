package com.longforus.myremotecontrol.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.longforus.myremotecontrol.ClientHolder
import com.longforus.myremotecontrol.DEVICENAME_32
import com.longforus.myremotecontrol.ToastUtils
import com.longforus.myremotecontrol.bean.UartCommand
import com.longforus.myremotecontrol.gson


@Composable
fun OtherScreen(clientHolder: ClientHolder, isOpen: Boolean) {
    Column(Modifier.padding(start = 10.dp, end = 10.dp)) {
        Text(text = "relay")
        Spacer(modifier = Modifier.height(20.dp))
        RelayRow(isOpen) {
            clientHolder.doRRPC("iot/relay", if (isOpen) "off" else "on", DEVICENAME_32)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "serial")
        Spacer(modifier = Modifier.height(20.dp))
        var board by remember {
            mutableStateOf("0")
        }
        var locker by remember {
            mutableStateOf("1")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.height
                (60.dp)
        ) {
            OutlinedTextField(
                value = board,
                onValueChange = {
                    board = it
                },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                label = {
                    Text(text = "board")
                }, modifier = Modifier.fillMaxHeight()
            )
            Spacer(modifier = Modifier.width(15.dp))
            Button(
                onClick = {
                    if (board.isNotEmpty() && locker.isNotEmpty()) {
                        val command = UartCommand(1, board.toInt(), locker.toInt())
                        clientHolder.doRRPC("iot/serial", gson.toJson(command), DEVICENAME_32)
                    } else {
                        ToastUtils.nonNull("board or locker")
                    }
                }, modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
            ) {
                Text(text = "status")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.height(60.dp)
        ) {

            OutlinedTextField(value = locker, onValueChange = {
                locker = it
            }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), label = {
                Text(text = "locker")
            }, modifier = Modifier.fillMaxHeight())
            Spacer(modifier = Modifier.width(15.dp))
            Button(
                onClick = {
                    if (board.isNotEmpty() && locker.isNotEmpty()) {
                        val command = UartCommand(0, board.toInt(), locker.toInt())
                        clientHolder.doRRPC("iot/serial", gson.toJson(command), DEVICENAME_32)
                    } else {
                        ToastUtils.nonNull("board or locker")
                    }
                }, modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
            ) {
                Text(text = "open")
            }

        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Button(onClick = {
                clientHolder.doRRPC("iot/power", "off", DEVICENAME_32)
            }) {
                Text(text = "off")
            }
            Button(onClick = {
                clientHolder.doRRPC("iot/power", "sub", DEVICENAME_32)
            }) {
                Text(text = "-")
            }
            Button(onClick = {
                clientHolder.doRRPC("iot/power", "add", DEVICENAME_32)
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
            Text(text = if (isOpen) "off" else "on", color = Color.White)
        }
    }
}

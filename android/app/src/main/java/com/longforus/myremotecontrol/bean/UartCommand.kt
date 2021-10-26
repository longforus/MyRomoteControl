package com.longforus.myremotecontrol.bean

import com.longforus.myremotecontrol.util.LockerUtil

data class UartCommand(
    val action: Int = 0, val board: Int = 0, val locker: Int = 1, val time: Long = System.currentTimeMillis(), val
    result: String = ""
) {
    val command: String = if (action == 0) LockerUtil.openLock(board, locker) else LockerUtil.queryLock(board)
}

package com.longforus.myremotecontrol.util

import android.os.SystemClock
import java.util.*


object LockerUtil {

    const val cmdPrefix = "33 34 33 "
    private var receData: String? = null
    val TAG = "LockerUtil"

    @Volatile
    private var curCursor = 0

    @Volatile
    private var resultBuf = StringBuffer()
    private var allElectricResultBuf = StringBuffer()
    private var allElectricResultCount = 0

    private var curDataLength = 5


    private const val ELECTRIC_RESULT_PREFIX = "03 00 64"

    private val isolationPrefixRegex by lazy { Regex("03 00 64 ..") }

    private val electricCmdPrefixRegex by lazy { Regex("33 34 33 .. .. .. .. .. 16") }

    fun onDataReceived(bRec: ByteArray?) {
        val contents =
            CrcUtil.byteToStr(bRec?.size ?: 0, bRec).trim()
        LogUtils.v(TAG, contents)
        when (curCmd) {
            100 -> {//获取电量
                allElectricResultBuf.append(contents)
                allElectricResultCount++
                if (allElectricResultCount > 3 && allElectricResultBuf.contains("aa 55 03 00 64 16 7d")) {
                    if (allElectricResultBuf.split(ELECTRIC_RESULT_PREFIX).size > 20) {
                        for (result in isolationPrefixRegex.findAll(allElectricResultBuf)) {
                            resultBuf.append(result.value.substring(9, 11))
                            resultBuf.append(" ")
                        }
                        LogUtils.d(TAG, "electricResultBuf $resultBuf")
                        allElectricResultBuf = StringBuffer()
                        allElectricResultCount = 0
                        val find = electricCmdPrefixRegex.find(resultBuf)
                        if (find != null) {
                            val value = find.value
                            val result = StringBuilder()
                            value.substring(cmdPrefix.length)
                                .split(" ")
                                .take(4)
                                .reversed().forEachIndexed { index, data ->
                                    if (index == 3) {
                                        result.append(".")
                                    }
                                    val cb =
                                        Integer.toHexString(Integer.parseInt(data, 16) - 51)
                                    result.append(if (cb.length < 2) "0$cb" else cb)
                                }
                            receData = result.toString()

                        } else {
                            allElectricResultCount = 0
                            SystemClock.sleep(200)
                            sendElectricCmd()
                        }
                    } else {
                        allElectricResultCount = 0
                        SystemClock.sleep(200)
                        sendElectricCmd()
                    }
                } else if (allElectricResultCount >= 5) {
                    allElectricResultCount = 0
                    SystemClock.sleep(200)
                    sendElectricCmd()
                }
            }
            else -> {
                contents.split(" ").take(20).forEachIndexed { i, s ->

                    when (curCursor) {
                        0 -> {
                            if (s == "aa") {
                                resultBuf.append(s)
                                resultBuf.append(" ")
                                curCursor++
                            }
                        }
                        1 -> {
                            if (s == "55") {
                                resultBuf.append(s)
                                resultBuf.append(" ")
                                curCursor++
                            }
                        }
                        2 -> {
                            if (s == "05" || s == "06") {
                                resultBuf.append(s)
                                resultBuf.append(" ")
                                curDataLength = s.toInt()
                                curCursor++
                            }
                        }
                        3 -> {
                            if (s.toInt() == curBoard) {
                                resultBuf.append(s)
                                resultBuf.append(" ")
                                curCursor++
                            }
                        }
                        4 -> {
                            if (s.toInt() == curCmd) {
                                resultBuf.append(s)
                                resultBuf.append(" ")
                                curCursor++
                            }
                        }
                        in 5..8 -> {
                            resultBuf.append(s)
                            resultBuf.append(" ")
                            if (curDataLength == 5 && curCursor == 8) {//3byte数据长度
                                receData = resultBuf.toString().trim()
                                resultBuf = StringBuffer()

                            }
                            curCursor++
                        }
                        9 -> {
                            resultBuf.append(s)
                            resultBuf.append(" ")
                            curCursor++
                            receData = resultBuf.toString().trim()
                            resultBuf = StringBuffer()

                        }
                    }

                }
            }
        }
    }


    @Throws(InterruptedException::class)
    fun openLock(board: Int, lock: Int): String {
        receData = null
        val lockOrder = ByteArray(7)
        lockOrder[0] = (-86).toByte()
        lockOrder[1] = 85.toByte()
        lockOrder[2] = 3.toByte()
        lockOrder[3] = board.toByte()
        lockOrder[4] = 80.toByte()
        lockOrder[5] = (lock.toByte() - 1).toByte()
        lockOrder[6] = CrcUtil.calcCrc8(lockOrder, 0, 6)
        val byteToStr = CrcUtil.byteToStr(
            lockOrder.size,
            lockOrder
        )
        LogUtils.d(
            TAG,
            "open locker : $board -> $lock  \n send data  =  $byteToStr"
        )
        return byteToStr
    }

    private var curCmd: Int = 50
    private var curBoard: Int = 0


    @Throws(InterruptedException::class)
    fun queryLock(board: Int): String {
        receData = null
        val lockOrder = ByteArray(6)
        lockOrder[0] = (-86).toByte()
        lockOrder[1] = 85.toByte()
        lockOrder[2] = 2.toByte()
        lockOrder[3] = board.toByte()
        lockOrder[4] = 81.toByte()
        lockOrder[5] = CrcUtil.calcCrc8(lockOrder, 0, 5)
        val contents =
            CrcUtil.byteToStr(lockOrder.size, lockOrder).trim()
        LogUtils.d(TAG, "queryAllLock $board  send content:$contents")
        return contents
    }


    /**
     *  设置隔离串口
     */
    @Throws(InterruptedException::class)
    fun setIsolation(): String {
        receData = null
        val lockOrder = ByteArray(8)
        lockOrder[0] = (-86).toByte()
        lockOrder[1] = 85.toByte()
        lockOrder[2] = 4.toByte()
        lockOrder[3] = 0.toByte()
        lockOrder[4] = 97.toByte()
        lockOrder[5] = 2.toByte()
        lockOrder[6] = 3.toByte()
        lockOrder[7] = CrcUtil.calcCrc8(lockOrder, 0, 7)
        val contents =
            CrcUtil.byteToStr(lockOrder.size, lockOrder).trim()
        LogUtils.d(TAG, "设置隔离串口 :$contents")
        return contents
    }


    /**
     * * @param onRec -1 未知 0关闭 1打开
     */
    fun callbackResult(
        lock: Int,
        data: String,
        onRec: (Int) -> Unit
    ) {
        val split = data.split(" ")
        when (split.size) {
            10 -> {//24E
                when (lock) {
                    /**
                     * 类型是不一样的,都有的是开的时候检测线通,这里要==1 有的是闭合的时候检测线通,这里要==0
                     */
                    in 1..8 -> {
                        val parseInt = Integer.parseInt(split[5], 16)
                        try {
                            onRec(parseInt shr (lock - 1) and 0x01)
                        } catch (e: Exception) {
                            LogUtils.e(TAG, e)
                        }
                    }
                    in 9..16 -> {
                        val parseInt = Integer.parseInt(split[6], 16)
                        try {
                            onRec(parseInt shr (lock - 9) and 0x01)
                        } catch (e: Exception) {
                            LogUtils.e(TAG, e)
                        }
                    }
                    in 17..22 -> {
                        val parseInt = Integer.parseInt(split[7], 16)
                        try {
                            onRec(parseInt shr (lock - 17) and 0x01)
                        } catch (e: Exception) {
                            LogUtils.e(TAG, e)
                        }
                    }
                    else -> {
                        try {
                            onRec(-1)
                        } catch (e: Exception) {
                            LogUtils.e(TAG, e)
                        }
                        LogUtils.e(TAG, "索引超出范围")
                    }
                }
            }
            9 -> {//18E
                when (lock) {
                    in 1..8 -> {
                        val parseInt = Integer.parseInt(split[5], 16)
                        try {
                            onRec(parseInt shr (lock - 1) and 0x01)
                        } catch (e: Exception) {
                            LogUtils.e(TAG, e)
                        }
                    }
                    in 9..13 -> {
                        val parseInt = Integer.parseInt(split[6], 16)
                        try {
                            onRec(parseInt shr (lock - 9) and 0x01)
                        } catch (e: Exception) {
                            LogUtils.e(TAG, e)
                        }
                    }
                    else -> {
                        try {
                            onRec(-1)
                        } catch (e: Exception) {
                            LogUtils.e(TAG, e)
                        }
                        LogUtils.e(TAG, "索引超出范围")
                    }
                }

            }
            else -> {
                try {
                    onRec(-1)
                } catch (e: Exception) {
                    LogUtils.e(TAG, e)
                }
                LogUtils.e(TAG, "返回数据长度不符")
            }
        }
    }


    fun sendElectricCmd(): String {
        receData = null
        allElectricResultBuf = StringBuffer()
        resultBuf = StringBuffer()
        //            68 AA AA AA AA AA AA 68 11 04 33 33 34 33 AE 16   标准内容
        val bOutArray = CrcUtil.HexToByteArr(
            "AA 55 12 00 64 68 AA AA AA AA AA AA 68 11 04 33 33 34 33 AE 16".replace(
                " ",
                ""
            )
        )
        val size = bOutArray.size
        val lockOrder = Arrays.copyOf(bOutArray, size + 1)
        lockOrder[size] = CrcUtil.calcCrc8(lockOrder, 0, size)
        val contents =
            CrcUtil.byteToStr(lockOrder.size, lockOrder).trim()
        LogUtils.d(
            TAG,
            "getElectricQuantity send content : $contents"
        )
        return contents
    }


}
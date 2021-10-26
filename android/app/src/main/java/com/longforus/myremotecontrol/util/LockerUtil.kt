package com.xm.smcabinet.utils

import android.os.SystemClock
import android.os.SystemClock.sleep
import android_serialport_api.SerialPort
import android_serialport_api.SerialPortFinder
import androidx.lifecycle.LifecycleOwner
import com.huawen.core.ext.onDestroyDispose
import com.huawen.core.utils.LogUtils
import com.huawen.core.utils.ShellUtils
import com.xm.smcabinet.bean.ChestData
import com.xm.smcabinet.bean.Model
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.anko.collections.forEachWithIndex
import tp.xmaihh.serialport.SerialHelper
import tp.xmaihh.serialport.bean.ComBean
import tp.xmaihh.serialport.utils.ByteUtil
import java.io.File
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume


object LockerUtil {

    var lastOpenLocker: ChestData? = null
    lateinit var sSp: SerialHelper

    private var receData: String? = null
    val TAG = "LockerUtil"

    @Volatile
    private var curCursor = 0

    @Volatile
    private var resultBuf = StringBuffer()
    private var allElectricResultBuf = StringBuffer()
    private var allElectricResultCount = 0

    private var curDataLength = 5


    private val workerThread= ThreadPoolExecutor(
        1, 1,
        0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        NamedThreadFactory("LockerUtil")
    )

    private const val ELECTRIC_RESULT_PREFIX = "03 00 64"

    private val isolationPrefixRegex by lazy { Regex("03 00 64 ..") }

    private val electricCmdPrefixRegex by lazy { Regex("33 34 33 .. .. .. .. .. 16") }

    fun init() {
        val serialPortFinder = SerialPortFinder()
        LogUtils.i(TAG, Arrays.toString(serialPortFinder.allDevices))
        sSp = object : SerialHelper(DEFAULT_COM485_NODE, 9600) {
            override fun onDataReceived(paramComBean: ComBean?) {
                 val contents =
                    Constant.byteToStr(paramComBean?.bRec?.size ?: 0, paramComBean?.bRec).trim()
                LogUtils.v(TAG, contents)
                when (curCmd) {
                    91 -> {//获取温度
                        val take = contents.split(" ").take(10)
                        val t5 = Integer.parseInt(take[5], 16)
                        val t6 = Integer.parseInt(take[6], 16)
                        receData = ((t5 * 256 + t6 - 500) / 10f).toString()
                        cmdRuning = false

                    }
                    105 -> {//获取风扇温度阈值
                        val take = contents.split(" ").take(10)
                        val t5 = Integer.parseInt(take[5], 16)
                        val t6 = Integer.parseInt(take[6], 16)
                        val t7 = Integer.parseInt(take[7], 16)
                        val t8 = Integer.parseInt(take[8], 16)
                        receData =
                            ((t5 * 256 + t6 - 500) / 10f).toString() + "," + ((t7 * 256 + t8 - 500) / 10f).toString()
                        cmdRuning = false
                    }
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
                                    value.substring(ElectricUtil.cmdPrefix.length)
                                        .split(" ")
                                        .take(4)
                                        .reversed().forEachWithIndex { index, data ->
                                            if (index == 3) {
                                                result.append(".")
                                            }
                                            val cb =
                                                Integer.toHexString(Integer.parseInt(data, 16) - 51)
                                            result.append(if (cb.length < 2) "0$cb" else cb)
                                        }
                                    receData = result.toString()
                                    cmdRuning = false
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
                        contents.split(" ").take(20).forEachWithIndex { i, s ->
                            if (!cmdRuning) {
                                return@forEachWithIndex
                            }
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
                                        cmdRuning = false
                                    }
                                    curCursor++
                                }
                                9 -> {
                                    resultBuf.append(s)
                                    resultBuf.append(" ")
                                    curCursor++
                                    receData = resultBuf.toString().trim()
                                    resultBuf = StringBuffer()
                                    cmdRuning = false
                                }
                            }

                        }
                    }
                }

            }
        }

        sSp.stopBits = SerialPort.STOPB.B1.stopBit
        sSp.parity = SerialPort.PARITY.NONE.parity
        sSp.dataBits = SerialPort.DATAB.CS8.dataBit
//        try {
//            sSp.open()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//        sSp.stickPackageHelper = this

        LogUtils.d(TAG, "init sSp:$sSp ")
    }


    val DEFAULT_COM485_NODE: String = "/dev/ttyS4"
//    val DEFAULT_COM485_NODE: String = "/dev/ttyUSB2"

    @Volatile
    private var cmdRuning = false

    @Throws(InterruptedException::class)
    private fun openLock(board: Int, lock: Int) {
        receData = null
        val lockOrder = ByteArray(7)
        lockOrder[0] = (-86).toByte()
        lockOrder[1] = 85.toByte()
        lockOrder[2] = 3.toByte()
        lockOrder[3] = board.toByte()
        lockOrder[4] = 80.toByte()
        lockOrder[5] = (lock.toByte() - 1).toByte()
        lockOrder[6] = Constant.calcCrc8(lockOrder, 0, 6)
        LogUtils.d(
            TAG,
            "open locker : $board -> $lock  \n send data  =  ${
                Constant.byteToStr(
                    lockOrder.size,
                    lockOrder
                )
            }"
        )
        sSp.send(lockOrder)
    }

    private var curCmd: Int = 50
    private var curBoard: Int = 0

    /**
     * @board start with 0
     * @lock start with 1
     * @param onRec -1 未知 0关闭 1打开
     */
    fun open(board: Int, lock: Int, owner: LifecycleOwner?, onRec: (Int) -> Unit) {
        checkOpen()
        workerThread.execute(Runnable {
            cmdRuning = true
            curCmd = 50
            curCursor = 0
            receData = null
            resultBuf = StringBuffer()
            curBoard = board
            LogUtils.i(TAG, "open Locker: board = $board lock=$lock")
            openLock(board, lock)
            val observeOn = Completable.create {
                do {
                } while (cmdRuning)
                it.onComplete()
                SystemClock.sleep(100)
            }
                .timeout(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
            if (owner != null) {
                observeOn.onDestroyDispose(owner)
            }
            observeOn.subscribe({
//            onRec(true)//返回的数据不稳定,直接成功
                if (!receData.isNullOrEmpty()) {
                    LogUtils.d(TAG, "open locker : $board -> $lock  \n rec data  = $receData")
                    callbackResult(
                        lock,
                        receData!!,
                        onRec
                    )
                } else {
                    try {
                        onRec(-1)
                    } catch (e: Exception) {
                        LogUtils.e(TAG, e)
                    }
                }
                receData = null
            }, {
                doOnError(onRec, it)
            })
        })

    }

    /**
     * @board start with 0
     * @lock start with 1
     * @param onRec -1 未知 0关闭 1打开
     */
    suspend fun openCo(board: Int, lock: Int):Int  = suspendCancellableCoroutine {co->
        checkOpen()
        workerThread.execute(Runnable {
            cmdRuning = true
            curCmd = 50
            curCursor = 0
            receData = null
            resultBuf = StringBuffer()
            curBoard = board
            LogUtils.i(TAG, "open Locker: board = $board lock=$lock")
            openLock(board, lock)
            val observeOn = Completable.create {
                do {
                } while (cmdRuning)
                it.onComplete()
                SystemClock.sleep(100)
            }
                .timeout(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
            observeOn.subscribe({
//            onRec(true)//返回的数据不稳定,直接成功
                if (!receData.isNullOrEmpty()) {
                    LogUtils.d(TAG, "open locker : $board -> $lock  \n rec data  = $receData")
                    callbackResult(
                        lock,
                        receData!!
                    ) {
                        if (co.isActive) {
                            co.resume(it)
                        }
                    }
                } else {
                    if (co.isActive) {
                        co.resume(-1)
                    }
                }
                receData = null
            }, {
                doOnError({
                    if (co.isActive) {
                        co.resume(it)
                    }
                }, it)
            })
        })

    }


    /**
     * @param onRec // -1 未知 0关闭 1打开
     */
    fun isOpen(
        board: Int,
        lock: Int,
        owner: LifecycleOwner,
        onRec: (Int) -> Unit
    ) {
        checkOpen()
        workerThread.execute(Runnable {
            cmdRuning = true
            curCmd = 51
            curCursor = 0
            receData = null
            resultBuf = StringBuffer()
            curBoard = board
            LogUtils.d(
                TAG,
                "query locker : $board -> $lock "
            )
//        sLocker.queryLock("A1")
            queryLock(board)
            Completable.create {
                do {
                } while (cmdRuning)
                it.onComplete()
                SystemClock.sleep(100)
            }.timeout(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .onDestroyDispose(owner)
                .subscribe({
                    if (!receData.isNullOrEmpty()) {
                        LogUtils.d(TAG, "query locker  : $board -> $lock   rec data  = $receData")
                        callbackResult(
                            lock,
                            receData!!,
                            onRec
                        )
                    } else {
                        onRec(-1)
                        LogUtils.e(TAG, "锁板返回数据空")
                    }
                    receData = null
                }, {
                    doOnError(onRec, it)
                })
        })

    }
/**
     * @param onRec // -1 未知 0关闭 1打开
     */
    suspend fun isOpenCo(
        board: Int,
        lock: Int
    ):Int = suspendCancellableCoroutine { co->
        checkOpen()
        workerThread.execute(Runnable {
            cmdRuning = true
            curCmd = 51
            curCursor = 0
            receData = null
            resultBuf = StringBuffer()
            curBoard = board
            LogUtils.d(
                TAG,
                "query locker : $board -> $lock "
            )
//        sLocker.queryLock("A1")
            queryLock(board)
            Completable.create {
                do {
                } while (cmdRuning)
                it.onComplete()
                SystemClock.sleep(100)
            }.timeout(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (!receData.isNullOrEmpty()) {
                        LogUtils.d(TAG, "query locker  : $board -> $lock   rec data  = $receData")
                        callbackResult(
                            lock,
                            receData!!
                        ){
                            if (co.isActive) {
                                co.resume(it)
                            }
                        }
                    } else {
                        if (co.isActive) {
                            co.resume(-1)
                        }
                        LogUtils.e(TAG, "锁板返回数据空")
                    }
                    receData = null
                }, {
                    doOnError({
                        if (co.isActive) {
                            co.resume(it)
                        }
                    }, it)
                })
        })

    }

    fun getTemp(
        board: Int,
        owner: LifecycleOwner,
        onRec: (Float) -> Unit
    ) {
        checkOpen()
        workerThread.execute(Runnable {
            cmdRuning = true
            curCmd = 91
            curCursor = 0
            receData = null
            resultBuf = StringBuffer()
            curBoard = board
            LogUtils.d(
                TAG,
                "getTemp : $board  "
            )
            receData = null
            val lockOrder = ByteArray(6)
            lockOrder[0] = (-86).toByte()
            lockOrder[1] = 85.toByte()
            lockOrder[2] = 2.toByte()
            lockOrder[3] = board.toByte()
            lockOrder[4] = 91.toByte()
            lockOrder[5] = Constant.calcCrc8(lockOrder, 0, 5)
            sSp.send(lockOrder)

            Completable.create {
                do {
                } while (cmdRuning)
                it.onComplete()
                SystemClock.sleep(100)
            }.timeout(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .onDestroyDispose(owner)
                .subscribe({
                    if (!receData.isNullOrEmpty()) {
                        LogUtils.d(TAG, "getTemp : $board   rec data  = $receData")
                        onRec(receData?.toFloat() ?: Float.MIN_VALUE)
                    }
                    receData = null
                }, {
                    it.printStackTrace()
                    cmdRuning = false
                    onRec(Float.MIN_VALUE)
                })
        })

    }


    fun getFanThresholdTemp(
        owner: LifecycleOwner,
        onRec: (String) -> Unit
    ) {
        checkOpen()
        workerThread.execute(Runnable {
            cmdRuning = true
            curCmd = 105
            curCursor = 0
            receData = null
            resultBuf = StringBuffer()
            curBoard = 0
            LogUtils.d(
                TAG,
                "getFanThresholdTemp "
            )
            receData = null
            val lockOrder = ByteArray(6)
            lockOrder[0] = (-86).toByte()
            lockOrder[1] = 85.toByte()
            lockOrder[2] = 2.toByte()
            lockOrder[3] = 0.toByte()
            lockOrder[4] = 105.toByte()
            lockOrder[5] = Constant.calcCrc8(lockOrder, 0, 5)
            sSp.send(lockOrder)
            Completable.create {
                do {
                } while (cmdRuning)
                it.onComplete()
                SystemClock.sleep(100)
            }.timeout(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .onDestroyDispose(owner)
                .subscribe({
                    if (!receData.isNullOrEmpty()) {
                        LogUtils.d(TAG, "getFanThresholdTemp :   rec data  = $receData")
                        onRec(receData ?: "")
                    }
                    receData = null
                }, {
                    cmdRuning = false
                    onRec("")
                })
        })
    }


    private fun doOnError(onRec: (Int) -> Unit, it: Throwable) {
        try {
            onRec.invoke(-1)
        } catch (e: Exception) {
            LogUtils.e(TAG, e)
        }
        LogUtils.e(TAG, it.message ?: "开锁超时")
        receData = null
        it.printStackTrace()
        cmdRuning = false
    }


    @Throws(InterruptedException::class)
    private fun queryLock(board: Int) {
        receData = null
        val lockOrder = ByteArray(6)
        lockOrder[0] = (-86).toByte()
        lockOrder[1] = 85.toByte()
        lockOrder[2] = 2.toByte()
        lockOrder[3] = board.toByte()
        lockOrder[4] = 81.toByte()
        lockOrder[5] = Constant.calcCrc8(lockOrder, 0, 5)
        val contents =
            Constant.byteToStr(lockOrder.size, lockOrder).trim()
        LogUtils.d(TAG, "queryAllLock $board  send content:$contents")
        sSp.send(lockOrder)
    }

    @Throws(InterruptedException::class)
    fun openFan(open: Boolean) {
        receData = null
        checkOpen()
        if (cmdRuning) {
            return
        }
        workerThread.execute(Runnable {
            val lockOrder = ByteArray(7)
            lockOrder[0] = (-86).toByte()
            lockOrder[1] = 85.toByte()
            lockOrder[2] = 3.toByte()
            lockOrder[3] = 0.toByte()
            lockOrder[4] = 83.toByte()
            lockOrder[5] = if (open) 0.toByte() else 1.toByte()
            lockOrder[6] = Constant.calcCrc8(lockOrder, 0, 6)
            val contents =
                Constant.byteToStr(lockOrder.size, lockOrder).trim()
            LogUtils.d(TAG, "set fan open : $open  send content:$contents")
            sSp.send(lockOrder)
            sleep(2000)
            lockOrder[4] = 82.toByte()
            lockOrder[6] = Constant.calcCrc8(lockOrder, 0, 6)
            sSp.send(lockOrder)
        })

    }

    @Throws(InterruptedException::class)
    fun setFanThresholdTemp(start: Int, stop: Int) {
        checkOpen()
        workerThread.execute(Runnable {
            receData = null
            val lockOrder = ByteArray(10)
            lockOrder[0] = (-86).toByte()
            lockOrder[1] = 85.toByte()
            lockOrder[2] = 6.toByte()
            lockOrder[3] = 0.toByte()
            lockOrder[4] = 88.toByte()
            val sta = start * 10 + 500
            lockOrder[5] = (sta shr 8).toByte()
            lockOrder[6] = (sta and 0xff).toByte()
            val sto = stop * 10 + 500
            lockOrder[7] = (sto shr 8).toByte()
            lockOrder[8] = (sto and 0xff).toByte()
            lockOrder[9] = Constant.calcCrc8(lockOrder, 0, 9)
            val contents =
                Constant.byteToStr(lockOrder.size, lockOrder).trim()
            LogUtils.d(TAG, "set fan temp start : $start  stop :$stop  send content:$contents")
            sSp.send(lockOrder)
            sleep(2000)
            lockOrder[4] = 89.toByte()
            lockOrder[9] = Constant.calcCrc8(lockOrder, 0, 9)
            sSp.send(lockOrder)
        })
    }

    /**
     * @param auto 是否自动控制风扇
     */
    @Throws(InterruptedException::class)
    fun setCtl(board: Int, auto: Boolean) {
        checkOpen()
        workerThread.execute(Runnable {
            receData = null
            val lockOrder = ByteArray(7)
            lockOrder[0] = (-86).toByte()
            lockOrder[1] = 85.toByte()
            lockOrder[2] = 3.toByte()
            lockOrder[3] = board.toByte()
            lockOrder[4] = 93.toByte()
            lockOrder[5] = if (auto) 1.toByte() else 0.toByte()
            lockOrder[6] = Constant.calcCrc8(lockOrder, 0, 6)
            val contents =
                Constant.byteToStr(lockOrder.size, lockOrder).trim()
            LogUtils.d(TAG, "setCtl $board auto : $auto  send content:$contents")
            sSp.send(lockOrder)
        })

    }

    /**
     *  设置隔离串口
     */
    @Throws(InterruptedException::class)
    fun setIsolation() {
        checkOpen()
        workerThread.execute(Runnable {
            receData = null
            val lockOrder = ByteArray(8)
            lockOrder[0] = (-86).toByte()
            lockOrder[1] = 85.toByte()
            lockOrder[2] = 4.toByte()
            lockOrder[3] = 0.toByte()
            lockOrder[4] = 97.toByte()
            lockOrder[5] = 2.toByte()
            lockOrder[6] = 3.toByte()
            lockOrder[7] = Constant.calcCrc8(lockOrder, 0, 7)
            val contents =
                Constant.byteToStr(lockOrder.size, lockOrder).trim()
            LogUtils.d(TAG, "设置隔离串口 :$contents")
            sSp.send(lockOrder)
        })

    }


    private fun callbackResult(
        lock: Int,
        data: String,
        onRec: (Int) -> Unit
    ) {
        val split = data.split(" ")
        when (split.size) {
            10 -> {//24E
                when (lock) {
                    /**
                     * 锁的类型是不一样的,都有的是开的时候检测线通,这里要==1 有的是闭合的时候检测线通,这里要==0
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
                        LogUtils.e(TAG, "锁索引超出范围")
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
                        LogUtils.e(TAG, "锁索引超出范围")
                    }
                }

            }
            else -> {
                try {
                    onRec(-1)
                } catch (e: Exception) {
                    LogUtils.e(TAG, e)
                }
                LogUtils.e(TAG, "开锁返回数据长度不符")
            }
        }
    }

//    override fun onDataReceived(p0: ByteArray?, p1: Int) {
//        SystemClock.sleep(250)
//        LogUtils.v(TAG, "recv $p1 : ${Constant.byteToStr(p0?.size ?: 0, p0)}")
//        receData = if (receData == null) {
//            Locker.subBytes(p0, 0, p1)
//        } else {
//            byteMerger(
//                receData!!,
//                Locker.subBytes(p0, 0, p1)
//            )
//        }
//        if (receData?.size ?: 0 > 6) {
//            cmdRuning = false
//            LogUtils.d(TAG, "onDataReceived : end ")
//        }
//    }

    private fun byteMerger(var0: ByteArray, var1: ByteArray): ByteArray? {
        val var2 = ByteArray(var0.size + var1.size)
        System.arraycopy(var0, 0, var2, 0, var0.size)
        System.arraycopy(var1, 0, var2, var0.size, var1.size)
        return var2
    }

    fun queryCabinetStatus(board: Model, owner: LifecycleOwner, onRec: (Model) -> Unit) {
        checkOpen()
        workerThread.execute(Runnable {
            cmdRuning = true
            curCmd = 51
            curCursor = 0
            receData = null
            resultBuf = StringBuffer()
            curBoard = board.deviceNumber.toInt()
            queryLock(curBoard)
            Completable.create {
                do {
                } while (cmdRuning)
                it.onComplete()
                SystemClock.sleep(200)
            }.timeout(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .onDestroyDispose(owner)
                .subscribe({
                    if (!receData.isNullOrEmpty()) {
                        LogUtils.d(
                            TAG,
                            "queryCabinetStatus : ${board.deviceNumber} ->  rec data  = $receData"
                        )
                        val split = receData!!.split(" ")
                        for (chestData in board.deviceChestList) {
                            if (chestData.itemMutableType != 0 || chestData.lockNumber.isNullOrEmpty()) {
                                continue
                            }
                            val lock = chestData.lockNumber.toInt()
                            when (split.size) {
                                10 -> {//24E
                                    when (lock) {
                                        in 1..8 -> {
                                            val parseInt = Integer.parseInt(split[5], 16)
                                            chestData.openStatus = parseInt shr (lock - 1) and 0x01
                                        }
                                        in 9..16 -> {
                                            val parseInt = Integer.parseInt(split[6], 16)
                                            chestData.openStatus =
                                                (parseInt shr (lock - 9) and 0x01)
                                        }
                                        in 17..22 -> {
                                            val parseInt = Integer.parseInt(split[7], 16)
                                            chestData.openStatus =
                                                (parseInt shr (lock - 17) and 0x01)
                                        }
                                    }
                                }
                                9 -> {//18E
                                    when (lock) {
                                        in 1..8 -> {
                                            val parseInt = Integer.parseInt(split[5], 16)
                                            chestData.openStatus =
                                                (parseInt shr (lock - 1) and 0x01)
                                        }
                                        in 9..13 -> {
                                            val parseInt = Integer.parseInt(split[6], 16)
                                            chestData.openStatus =
                                                (parseInt shr (lock - 9) and 0x01)
                                        }
                                    }

                                }
                            }
                        }
                        onRec.invoke(board)
                    } else {
                        onRec.invoke(board)
                    }
                    receData = null
                }, {
                    it.printStackTrace()
                    cmdRuning = false
                    onRec.invoke(board)
                })
        })
    }

    fun openAll(fromIndex: Int = 0, toCabinetIndex: Int) {
        checkOpen()
        workerThread.execute {
            for (i in fromIndex..toCabinetIndex) {
                for (j in 1..(if (i == 0) {
                    6
                } else {
                    22
                })) {
                    openLock(i, j)
                    sleep(2000)
                }
            }
        }
    }

    private fun checkOpen() {
        if (!sSp.isOpen) {
            /* Check access permission */  // 检查是否获取了指定串口的读写权限
            checkCanRW {
                sSp.open()
            }
        }
    }


    fun checkCanRW(nodePath: String = DEFAULT_COM485_NODE, onOk: () -> Unit) {
        val device = File(nodePath)
        /* Check access permission */  // 检查是否获取了指定串口的读写权限
        if (device.exists()) {
            if (!device.canRead() || !device.canWrite()) {
                ShellUtils.execCmd("chmod 666 $nodePath", true, true)?.let {
                    LogUtils.i(TAG, "${it.successMsg} ")
                    LogUtils.i(TAG, "${it.errorMsg} ")
                    LogUtils.i(TAG, "${it.result} ")
                    onOk.invoke()
                }
            } else {
                onOk.invoke()
            }
        }
    }

    fun prepareClose() {
        workerThread.execute {
            sSp.close()
        }
    }


    fun getElectricQuantity(
        owner: LifecycleOwner,
        onRec: (Float) -> Unit
    ) {
        if (!sSp.isOpen) {
            sSp.open()
        }
        workerThread.execute(Runnable {
            cmdRuning = true
            curCmd = 100
            curBoard = 0
            sendElectricCmd()
            Completable.create {
                do {
                } while (cmdRuning)
                it.onComplete()
                SystemClock.sleep(100)
            }.timeout(5, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .onDestroyDispose(owner)
                .subscribe({
                    if (!receData.isNullOrEmpty()) {
                        LogUtils.d(TAG, "getElectricQuantity :   rec data  = $receData")
                        onRec((receData ?: "0.0").toFloat())
                    }
                    receData = null
                }, {
                    it.printStackTrace()
                    cmdRuning = false
                    onRec(-1f)
                })
        })
    }

    private fun sendElectricCmd() {
        if (!cmdRuning) {
            return
        }
        receData = null
        allElectricResultBuf = StringBuffer()
        resultBuf = StringBuffer()
        //            68 AA AA AA AA AA AA 68 11 04 33 33 34 33 AE 16   标准内容
        val bOutArray = ByteUtil.HexToByteArr(
            "AA 55 12 00 64 68 AA AA AA AA AA AA 68 11 04 33 33 34 33 AE 16".replace(
                " ",
                ""
            )
        )
        val size = bOutArray.size
        val lockOrder = Arrays.copyOf(bOutArray, size + 1)
        lockOrder[size] = Constant.calcCrc8(lockOrder, 0, size)
        val contents =
            Constant.byteToStr(lockOrder.size, lockOrder).trim()
        LogUtils.d(
            TAG,
            "getElectricQuantity send content : $contents"
        )
        sSp.send(lockOrder)
    }


}
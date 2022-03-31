package com.longforus.myremotecontrol.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

object StatusBarUtil {
    fun transparentStatusBar(activity: Activity) {
        with(activity) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            val option = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            val vis = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = option or vis
            window.statusBarColor = Color.parseColor("#22000000")
        }
    }


    fun getStatusBarHeight(activity: Context):Dp{
        /**
         * 获取状态栏高度——方法1
         * */
        /**
         * 获取状态栏高度——方法1
         */
        var statusBarHeight1 = -1
        //获取status_bar_height资源的ID
        //获取status_bar_height资源的ID
        val resourceId: Int = activity.getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            //根据资源ID获取响应的尺寸值
            statusBarHeight1 = activity.getResources().getDimension(resourceId).toInt()
        }
        Log.d("StatusBarUtil", "状态栏-方法1:$statusBarHeight1")
        val scale = activity.getResources().getDisplayMetrics().density;
        return  (statusBarHeight1 / scale + 0.5f).dp
    }
}


/**
 * Returns a string containing the first [n] characters from this string, or the entire string if this string is shorter.
 * 如果length超过n返回实际长度,否则截取前n个char
 *
 * @throws IllegalArgumentException if [n] is negative.
 *
 * @sample samples.text.Strings.take
 */
fun String.ifTakeAppendLength(n: Int): String {
    require(n >= 0) { "Requested character count $n is less than zero." }
    val endIndex = n.coerceAtMost(length)
    return substring(0, endIndex) + if (endIndex == n) "......(length = $length)" else ""
}


//android获取一个用于打开文件的intent  
fun getOpenFileIntent(file: File, context: Context, type: String = "application/vnd.ms-excel"): Intent {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val uri: Uri
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        uri = FileProvider.getUriForFile(context,
            context.packageName + ".fileProvider",
            file)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        uri = Uri.fromFile(file)
    }
    intent.putExtra(Intent.EXTRA_STREAM, uri)
    intent.setDataAndType(uri, type)
    return intent
}
//android获取一个用于打开文件的intent  
fun getOpenDirIntent(file: File, context: Context): Intent {
    val intent = Intent(Intent.ACTION_GET_CONTENT)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val uri: Uri
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        uri = FileProvider.getUriForFile(context,
            context.packageName + ".fileProvider", file)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        uri = Uri.fromFile(file)
    }
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
    }
    intent.setDataAndType(uri, "*/*")
    return intent
}


inline val Int.c: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color(this)
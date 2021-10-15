package com.longforus.myremotecontrol.bean

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class IconScreens(val route: String, val label:String, val icon: ImageVector) {

    //Bottom Nav

    object Home : IconScreens("hone", "Home", Icons.Outlined.Home)
    object Other : IconScreens("other", "Other", Icons.Outlined.Settings)
}
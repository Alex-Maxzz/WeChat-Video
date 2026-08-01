package com.elder.wechatvideo.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elder.wechatvideo.R
import com.elder.wechatvideo.ui.screens.about.AboutScreen
import com.elder.wechatvideo.ui.screens.calibration.CalibrationScreen
import com.elder.wechatvideo.ui.screens.contacts.AddEditContactScreen
import com.elder.wechatvideo.ui.screens.contacts.ContactListScreen
import com.elder.wechatvideo.ui.screens.keepalive.KeepAliveScreen
import com.elder.wechatvideo.ui.screens.settings.SettingsScreen

/**
 * 路由常量集中定义。
 */
object Routes {
    /** 联系人列表（底部 Tab） */
    const val CONTACTS = "contacts"

    /** 添加联系人 */
    const val CONTACTS_ADD = "contacts/add"

    /** 编辑联系人，参数 id 为联系人主键 */
    const val CONTACTS_EDIT = "contacts/edit/{id}"

    /** 保活设置（底部 Tab） */
    const val KEEP_ALIVE = "keepalive"

    /** 关于（设置子页面） */
    const val ABOUT = "about"

    /** 设置（底部 Tab） */
    const val SETTINGS = "settings"

    /** 按键位置校准 */
    const val CALIBRATION = "calibration"

    /** 构造编辑联系人路由 */
    fun editRoute(id: Long): String = "contacts/edit/$id"
}

/**
 * 底部 Tab 项定义。
 *
 * @property route 该 Tab 对应的路由
 * @property labelRes 标题字符串资源
 * @property icon 图标
 */
private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

/** 底部 3 个 Tab */
private val bottomTabs: List<BottomTab> = listOf(
    BottomTab(Routes.CONTACTS, R.string.tab_contacts, Icons.Filled.People),
    BottomTab(Routes.KEEP_ALIVE, R.string.tab_keepalive, Icons.Filled.Shield),
    BottomTab(Routes.SETTINGS, R.string.tab_settings, Icons.Filled.Settings)
)

/**
 * 应用顶层导航。
 *
 * 包含一个 [Scaffold]，底部为 3 标签 [NavigationBar]；
 * 主 Tab（联系人/保活/关于）显示底部栏，添加/编辑联系人子页面为全屏（无底部栏）。
 */
@Composable
fun AppNavigation(
    initialRoute: String? = null
) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 从 CallBridgeActivity 跳转时自动导航到校准页
    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            navController.navigate(initialRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    // 仅在三个主 Tab 上显示底部导航栏
    val showBottomBar = currentRoute in setOf(
        Routes.CONTACTS, Routes.KEEP_ALIVE, Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CONTACTS,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 联系人列表
            composable(Routes.CONTACTS) {
                ContactListScreen(
                    onAddContact = { navController.navigate(Routes.CONTACTS_ADD) },
                    onEditContact = { id -> navController.navigate(Routes.editRoute(id)) }
                )
            }

            // 添加联系人
            composable(Routes.CONTACTS_ADD) {
                AddEditContactScreen(
                    contactId = null,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            // 编辑联系人
            composable(
                route = Routes.CONTACTS_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: 0L
                AddEditContactScreen(
                    contactId = id,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            // 保活设置
            composable(Routes.KEEP_ALIVE) {
                KeepAliveScreen(
                    onNavigateToCalibration = { navController.navigate(Routes.CALIBRATION) }
                )
            }

            // 设置（底部 Tab）
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToAbout = { navController.navigate(Routes.ABOUT) }
                )
            }

            // 关于（设置子页面）
            composable(Routes.ABOUT) {
                AboutScreen()
            }

            // 按键位置校准
            composable(Routes.CALIBRATION) {
                CalibrationScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

package com.example.inventorymaster.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import kotlin.math.roundToInt

/**
 * 统一的页面转场规范 (参考 Material 3 空间连续性与 ImageToolbox 动效规范)
 * 彻底废除 0.4f 残影半透明，引入横向位移 + 平滑加减速贝塞尔曲线。
 */
object NavMotion {

    /** 类似 iOS/高端 Android 的平滑加减速曲线 */
    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // ==========================================
    // 1. 标准推栈页面转场 (右侧滑入 / 滑出)
    // ==========================================

    /** 新页面从右向左滑入 (位移 30% 屏幕宽度) + 完全淡入 (0f -> 1f) */
    val slideEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> (fullWidth * 0.3f).roundToInt() },
            animationSpec = tween(durationMillis = 300, easing = StandardEasing)
        ) + fadeIn(
            initialAlpha = 0f,
            animationSpec = tween(durationMillis = 200)
        )
    }

    /** 旧页面向左微移退出 (位移 -20% 屏幕宽度) + 完全淡出 (1f -> 0f) */
    val slideExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -(fullWidth * 0.2f).roundToInt() },
            animationSpec = tween(durationMillis = 300, easing = StandardEasing)
        ) + fadeOut(
            targetAlpha = 0f,
            animationSpec = tween(durationMillis = 200)
        )
    }

    /** 返回时：旧页面从左向右回位复原 (-20% -> 0) + 淡入 */
    val slidePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -(fullWidth * 0.2f).roundToInt() },
            animationSpec = tween(durationMillis = 300, easing = StandardEasing)
        ) + fadeIn(
            initialAlpha = 0f,
            animationSpec = tween(durationMillis = 200)
        )
    }

    /** 返回时：当前顶层页面向右滑出屏幕 (30% 位移) + 完全淡出 */
    val slidePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> (fullWidth * 0.3f).roundToInt() },
            animationSpec = tween(durationMillis = 300, easing = StandardEasing)
        ) + fadeOut(
            targetAlpha = 0f,
            animationSpec = tween(durationMillis = 200)
        )
    }

    // ==========================================
    // 2. 底部 Tab 切换 (轻快微淡入淡出 + 微缩放，灵动而不发黏)
    // ==========================================
    val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(
            animationSpec = tween(durationMillis = 150, easing = StandardEasing)
        )
    }

    val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(
            animationSpec = tween(durationMillis = 100, easing = StandardEasing)
        )
    }
}

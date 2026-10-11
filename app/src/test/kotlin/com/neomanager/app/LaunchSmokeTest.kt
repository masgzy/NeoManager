/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 启动冒烟测试（Robolectric）。
 *
 * 在 JVM 上运行真实 Android 框架并启动 [MainActivity]：Application/Provider 初始化、
 * 主题装载、Compose 首帧组合、导航图构建中任何一环抛出的异常都会以真实堆栈失败本测试。
 * 该测试进入 CI 后可拦截「本地构建通过但真机闪退」类的回归（0.2.0-alpha02 教训）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = NeoApplication::class)
public class LaunchSmokeTest {
    @Test
    public fun mainActivity_launchesAndComposesFirstFrame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            // 冲刷主线程消息队列：驱动 Choreographer 执行 Compose 首帧组合
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                // 首帧后 decorView 必须已挂载；组合失败会在 idle() 阶段抛出真实异常
                checkNotNull(activity.window.decorView) { "decorView 未挂载" }
            }
        }
    }
}

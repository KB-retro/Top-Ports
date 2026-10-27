package com.topports.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallPlannerTest {

    @Test
    fun xapkWithSplitsAndObb() {
        val plan = InstallPlanner.plan(
            listOf(
                "manifest.json",
                "com.game.apk",
                "config.arm64_v8a.apk",
                "Android/obb/com.game/main.3.com.game.obb",
            ),
        )
        assertEquals(listOf("com.game.apk", "config.arm64_v8a.apk"), plan.apks)
        assertTrue(plan.isSplitApk)
        assertEquals(listOf(InstallPlan.Target("Android/obb/com.game/main.3.com.game.obb", "com.game")), plan.obbs)
    }

    @Test
    fun looseObbAndDataFolder() {
        val plan = InstallPlanner.plan(
            listOf(
                "Port/game.apk",
                "Port/main.1.com.port.game.obb",
                "Port/Android/data/com.port.game/files/data.pak",
                "Port/Android/data/com.port.game/files/more.pak",
            ),
        )
        assertFalse(plan.isSplitApk)
        assertEquals("com.port.game", plan.obbs.single().packageName)
        assertEquals(listOf(InstallPlan.Target("Port/Android/data/com.port.game", "com.port.game")), plan.dataDirs)
    }
}

package com.ssortia.runtimer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {
    @Test
    fun `на Android 13 без разрешения нужен запрос`() {
        assertTrue(needsNotificationRequest(sdkInt = 33, granted = false))
    }

    @Test
    fun `на новых версиях без разрешения нужен запрос`() {
        assertTrue(needsNotificationRequest(sdkInt = 35, granted = false))
    }

    @Test
    fun `выданное разрешение запрашивать не нужно`() {
        assertFalse(needsNotificationRequest(sdkInt = 33, granted = true))
    }

    @Test
    fun `до Android 13 запрос не нужен, хотя проверка возвращает отказ`() {
        assertFalse(needsNotificationRequest(sdkInt = 32, granted = false))
        assertFalse(needsNotificationRequest(sdkInt = 26, granted = false))
    }
}

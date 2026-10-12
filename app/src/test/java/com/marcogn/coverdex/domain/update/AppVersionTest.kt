package com.marcogn.coverdex.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parse accepts plain x_y_z with or without a leading v`() {
        assertEquals(AppVersion(1, 0, 6), AppVersion.parse("1.0.6"))
        assertEquals(AppVersion(1, 0, 6), AppVersion.parse("v1.0.6"))
        assertEquals(AppVersion(12, 34, 56), AppVersion.parse(" v12.34.56 "))
    }

    @Test
    fun `compares numerically, not as strings`() {
        assertTrue(AppVersion.parse("1.0.10")!! > AppVersion.parse("1.0.9")!!)
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.99")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
        assertEquals(0, AppVersion.parse("v1.1.0")!!.compareTo(AppVersion.parse("1.1.0")!!))
    }

    @Test
    fun `anything else is not comparable`() {
        listOf(
            "1.1.0-beta", "1.1", "1.1.0.1", "V1.0.0", "vv1.0.0", "1.a.0", "", "latest",
            "1.0.99999999999", "-1.0.0", "1..0",
        ).forEach { assertNull(it, AppVersion.parse(it)) }
    }

    @Test
    fun `toString is the plain form without v`() {
        assertEquals("1.0.10", AppVersion.parse("v1.0.10").toString())
    }
}

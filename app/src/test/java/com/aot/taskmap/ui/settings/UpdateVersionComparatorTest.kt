package com.aot.taskmap.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionComparatorTest {

    @Test
    fun returnsTrueWhenRemoteVersionIsHigher() {
        assertTrue(UpdateVersionComparator.isRemoteVersionNewer("1.4.2", "1.4.1"))
    }

    @Test
    fun returnsFalseWhenVersionsAreEqual() {
        assertFalse(UpdateVersionComparator.isRemoteVersionNewer("1.4.1", "1.4.1"))
    }

    @Test
    fun parsesPrefixedTagVersions() {
        assertTrue(UpdateVersionComparator.isRemoteVersionNewer("v1.5.0", "1.4.9"))
    }

    @Test
    fun fallbackComparisonForNonNumericEqualVersions() {
        assertFalse(UpdateVersionComparator.isRemoteVersionNewer("release-alpha", "release-alpha"))
    }
}

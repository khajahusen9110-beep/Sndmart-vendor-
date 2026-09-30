package com.example.util

import com.example.data.model.AppVersionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateLogicTest {

    // --- Version String Parsing & Comparison Tests ---

    @Test
    fun testCompareVersionStrings_1_0_1_vs_1_0_10() {
        val result = compareVersionStrings("1.0.1", "1.0.10")
        assertTrue("1.0.1 should be less than 1.0.10", result < 0)
    }

    @Test
    fun testCompareVersionStrings_1_10_vs_1_9() {
        val result = compareVersionStrings("1.10", "1.9")
        assertTrue("1.10 should be greater than 1.9", result > 0)
    }

    @Test
    fun testCompareVersionStrings_equalVersions() {
        val result = compareVersionStrings("1.0.1", "1.0.1")
        assertEquals(0, result)
    }

    @Test
    fun testCompareVersionStrings_nullAndBlankMin() {
        // null or blank strings should parse to empty parts
        assertEquals(0, compareVersionStrings(null, null))
        assertEquals(0, compareVersionStrings("", ""))
        assertTrue(compareVersionStrings("1.0.0", null) > 0)
        assertTrue(compareVersionStrings(null, "1.0.0") < 0)
        assertTrue(compareVersionStrings("1.0.0", "   ") > 0)
        assertTrue(compareVersionStrings("   ", "1.0.0") < 0)
    }

    @Test
    fun testCompareVersionStrings_nonNumericSuffix() {
        // "1.0.1-beta" should be treated as "1.0.1"
        assertEquals(0, compareVersionStrings("1.0.1-beta", "1.0.1"))
        assertEquals(0, compareVersionStrings("1.0.1+build.42", "1.0.1"))
        assertTrue(compareVersionStrings("1.0.2-alpha", "1.0.1") > 0)
        assertTrue(compareVersionStrings("1.0.1-rc1", "1.0.2") < 0)
    }

    // --- Decision Logic (evaluateUpdateState) Tests ---

    @Test
    fun testForceUpdate_whenCurrentCodeBelowMinVersionCode() {
        val info = AppVersionInfo(
            minVersionCode = 10,
            latestVersionCode = 15,
            minimumSupportedVersion = "1.0.0",
            currentVersion = "1.5.0",
            forceUpdate = false
        )
        val state = evaluateUpdateState(
            currentCode = 9,
            currentName = "1.0.0",
            info = info
        )
        assertTrue("State should be ForceUpdate when currentCode < minVersionCode", state is AppUpdateState.ForceUpdate)
    }

    @Test
    fun testForceUpdate_whenForceUpdateFlagTrueAndCurrentCodeBelowLatest() {
        val info = AppVersionInfo(
            minVersionCode = 5,
            latestVersionCode = 10,
            minimumSupportedVersion = "1.0.0",
            currentVersion = "2.0.0",
            forceUpdate = true // Forced update to latest!
        )
        val state = evaluateUpdateState(
            currentCode = 7, // >= minVersionCode, but forceUpdate = true and < latestVersionCode
            currentName = "1.2.0",
            info = info
        )
        assertTrue("State should be ForceUpdate when forceUpdate=true and below latest", state is AppUpdateState.ForceUpdate)
    }

    @Test
    fun testOptionalUpdate_whenCurrentCodeBelowLatestButAboveMin() {
        val info = AppVersionInfo(
            minVersionCode = 5,
            latestVersionCode = 10,
            minimumSupportedVersion = "1.0.0",
            currentVersion = "2.0.0",
            forceUpdate = false
        )
        val state = evaluateUpdateState(
            currentCode = 8,
            currentName = "1.5.0",
            info = info
        )
        assertTrue("State should be OptionalUpdate when current is between min and latest", state is AppUpdateState.OptionalUpdate)
        val opt = state as AppUpdateState.OptionalUpdate
        assertEquals("2.0.0", opt.latestVersion)
        assertEquals(10, opt.latestVersionCode)
    }

    @Test
    fun testOptionalUpdate_dismissedForSameVersion_returnsUpToDate() {
        val info = AppVersionInfo(
            minVersionCode = 5,
            latestVersionCode = 10,
            minimumSupportedVersion = "1.0.0",
            currentVersion = "2.0.0",
            forceUpdate = false
        )
        val state = evaluateUpdateState(
            currentCode = 8,
            currentName = "1.5.0",
            info = info,
            lastDismissedCode = 10, // already dismissed code 10
            lastDismissedName = "2.0.0"
        )
        assertTrue("State should be UpToDate when user already dismissed this latest version", state is AppUpdateState.UpToDate)
    }

    @Test
    fun testUpToDate_whenCurrentMatchesOrExceedsLatest() {
        val info = AppVersionInfo(
            minVersionCode = 5,
            latestVersionCode = 10,
            minimumSupportedVersion = "1.0.0",
            currentVersion = "2.0.0",
            forceUpdate = false
        )
        val stateEqual = evaluateUpdateState(
            currentCode = 10,
            currentName = "2.0.0",
            info = info
        )
        assertTrue("State should be UpToDate when current == latest", stateEqual is AppUpdateState.UpToDate)

        val stateGreater = evaluateUpdateState(
            currentCode = 12,
            currentName = "2.1.0",
            info = info
        )
        assertTrue("State should be UpToDate when current > latest", stateGreater is AppUpdateState.UpToDate)
    }

    @Test
    fun testFallback_whenVersionCodeColumnsNull_usesStringComparison() {
        // version_code columns null hone par fallback to string comparison
        val info = AppVersionInfo(
            minVersionCode = null,
            latestVersionCode = null,
            minimumSupportedVersion = "1.0.5",
            currentVersion = "1.2.0",
            forceUpdate = false
        )

        // Below minimum supported version string
        val forceState = evaluateUpdateState(
            currentCode = 1,
            currentName = "1.0.2",
            info = info
        )
        assertTrue("Should fallback to string comparison and be ForceUpdate", forceState is AppUpdateState.ForceUpdate)

        // Between min and latest
        val optState = evaluateUpdateState(
            currentCode = 1,
            currentName = "1.1.0",
            info = info
        )
        assertTrue("Should fallback to string comparison and be OptionalUpdate", optState is AppUpdateState.OptionalUpdate)

        // Equal to latest
        val upToDateState = evaluateUpdateState(
            currentCode = 1,
            currentName = "1.2.0",
            info = info
        )
        assertTrue("Should fallback to string comparison and be UpToDate", upToDateState is AppUpdateState.UpToDate)
    }

    @Test
    fun testFailOpen_whenNetworkErrorAndNoCachedForce_returnsUpToDate() {
        val state = evaluateUpdateState(
            currentCode = 2,
            currentName = "1.0.1",
            info = null, // network/parse error
            cachedForceInfo = null
        )
        assertTrue("Fail-open policy: when info is null and no cached force, app must not be blocked", state is AppUpdateState.UpToDate)
    }

    @Test
    fun testCachedForce_whenNetworkErrorAndStillBelowCachedMin_returnsForceUpdate() {
        val cachedForce = AppVersionInfo(
            minVersionCode = 5,
            latestVersionCode = 10,
            minimumSupportedVersion = "2.0.0",
            currentVersion = "2.5.0",
            updateMessage = "Security patch required"
        )
        val state = evaluateUpdateState(
            currentCode = 3, // Still below cached min 5
            currentName = "1.0.1",
            info = null, // Network error
            cachedForceInfo = cachedForce
        )
        assertTrue("When network fails but previous FORCE result is still unsatisfied, keep user blocked", state is AppUpdateState.ForceUpdate)
        val force = state as AppUpdateState.ForceUpdate
        assertEquals("Security patch required", force.message)
    }

    @Test
    fun testCachedForce_whenNetworkErrorButCurrentNowSatisfiesCachedMin_returnsUpToDate() {
        val cachedForce = AppVersionInfo(
            minVersionCode = 5,
            latestVersionCode = 10,
            minimumSupportedVersion = "2.0.0",
            currentVersion = "2.5.0"
        )
        val state = evaluateUpdateState(
            currentCode = 6, // User has updated to code 6, >= cached min 5!
            currentName = "2.0.1",
            info = null, // Network error
            cachedForceInfo = cachedForce
        )
        assertTrue("If current app version satisfies cached min, network failure allows access", state is AppUpdateState.UpToDate)
    }
}

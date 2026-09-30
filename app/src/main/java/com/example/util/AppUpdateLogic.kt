package com.example.util

import com.example.data.model.AppVersionInfo

/**
 * Represents the three operational states for app updates:
 * 1. ForceUpdate: Blocking, unavoidable update (Play IMMEDIATE update or Play Store fallback).
 * 2. OptionalUpdate: Non-blocking, dismissable update (Play FLEXIBLE update or Play Store fallback).
 * 3. UpToDate: App is compliant, no update UI shown.
 */
sealed class AppUpdateState {
    data object UpToDate : AppUpdateState()

    data class ForceUpdate(
        val message: String?,
        val requiredVersion: String,
        val currentVersion: String,
        val updateUrl: String?,
        val minVersionCode: Int? = null,
        val latestVersionCode: Int? = null
    ) : AppUpdateState()

    data class OptionalUpdate(
        val message: String?,
        val latestVersion: String,
        val currentVersion: String,
        val updateUrl: String?,
        val latestVersionCode: Int? = null
    ) : AppUpdateState()
}

/**
 * Extracts numeric version segments from a version string, ignoring non-numeric suffixes.
 * E.g., "1.0.1-beta" -> [1, 0, 1]
 *       "2.10.3+build45" -> [2, 10, 3]
 *       "1.9" -> [1, 9]
 */
fun parseVersionParts(version: String?): List<Int> {
    if (version.isNullOrBlank()) return emptyList()
    val clean = version.trim().split(Regex("[-+_a-zA-Z]")).firstOrNull()?.trim() ?: ""
    if (clean.isBlank()) return emptyList()
    return clean.split('.')
        .mapNotNull { it.trim().toIntOrNull() }
}

/**
 * Compares two version strings numerically part by part.
 * Returns negative if v1 < v2, zero if equal, positive if v1 > v2.
 * E.g. "1.0.1" vs "1.0.10" -> -9 (< 0)
 *      "1.10" vs "1.9" -> 1 (> 0)
 *      "1.0.1-beta" vs "1.0.1" -> 0
 */
fun compareVersionStrings(v1: String?, v2: String?): Int {
    val p1 = parseVersionParts(v1)
    val p2 = parseVersionParts(v2)
    val maxLen = maxOf(p1.size, p2.size)
    for (i in 0 until maxLen) {
        val n1 = p1.getOrElse(i) { 0 }
        val n2 = p2.getOrElse(i) { 0 }
        if (n1 != n2) {
            return n1.compareTo(n2)
        }
    }
    return 0
}

/**
 * Pure decision function to determine the app update state.
 *
 * Rules:
 * A. Version Code compare is preferred. Falls back to string compare if code columns are null.
 * B. FORCE if current < min, OR (force_update == true AND current < latest).
 *    OPTIONAL if current < latest AND current >= min (unless previously dismissed).
 *    UP_TO_DATE otherwise.
 * E. Fail-open policy: If network/parse failed (info == null), don't block unless a previously
 *    cached FORCE constraint exists and the current app is still below it.
 */
fun evaluateUpdateState(
    currentCode: Int,
    currentName: String,
    info: AppVersionInfo?,
    lastDismissedCode: Int? = null,
    lastDismissedName: String? = null,
    cachedForceInfo: AppVersionInfo? = null
): AppUpdateState {
    if (info != null) {
        // 1. Minimum version check
        val isBelowMin = when {
            info.minVersionCode != null -> currentCode < info.minVersionCode
            !info.minimumSupportedVersion.isNullOrBlank() -> compareVersionStrings(currentName, info.minimumSupportedVersion) < 0
            else -> false
        }

        // 2. Latest version check
        val isBelowLatest = when {
            info.latestVersionCode != null -> currentCode < info.latestVersionCode
            !info.currentVersion.isNullOrBlank() -> compareVersionStrings(currentName, info.currentVersion) < 0
            else -> false
        }

        // 3. Force update determination
        val isForce = isBelowMin || (info.forceUpdate && isBelowLatest)

        if (isForce) {
            val reqVersion = info.minimumSupportedVersion
                ?: info.minVersionCode?.toString()
                ?: info.currentVersion
                ?: info.latestVersionCode?.toString()
                ?: ""
            return AppUpdateState.ForceUpdate(
                message = info.updateMessage,
                requiredVersion = reqVersion,
                currentVersion = currentName,
                updateUrl = info.updateUrl,
                minVersionCode = info.minVersionCode,
                latestVersionCode = info.latestVersionCode
            )
        }

        // 4. Optional update check (if below latest and not force)
        if (isBelowLatest) {
            val isDismissed = when {
                info.latestVersionCode != null && lastDismissedCode != null -> lastDismissedCode >= info.latestVersionCode
                !info.currentVersion.isNullOrBlank() && !lastDismissedName.isNullOrBlank() -> compareVersionStrings(lastDismissedName, info.currentVersion) >= 0
                else -> false
            }

            if (isDismissed) {
                return AppUpdateState.UpToDate
            }

            val targetVersion = info.currentVersion
                ?: info.latestVersionCode?.toString()
                ?: ""
            return AppUpdateState.OptionalUpdate(
                message = info.updateMessage,
                latestVersion = targetVersion,
                currentVersion = currentName,
                updateUrl = info.updateUrl,
                latestVersionCode = info.latestVersionCode
            )
        }

        return AppUpdateState.UpToDate
    }

    // info == null (Network / parse error) -> Fail-open policy
    // But if a cached FORCE result was previously recorded, verify if current app is still below it.
    if (cachedForceInfo != null) {
        val isStillBelowMin = when {
            cachedForceInfo.minVersionCode != null -> currentCode < cachedForceInfo.minVersionCode
            !cachedForceInfo.minimumSupportedVersion.isNullOrBlank() -> compareVersionStrings(currentName, cachedForceInfo.minimumSupportedVersion) < 0
            else -> false
        }

        val isStillBelowLatest = when {
            cachedForceInfo.latestVersionCode != null -> currentCode < cachedForceInfo.latestVersionCode
            !cachedForceInfo.currentVersion.isNullOrBlank() -> compareVersionStrings(currentName, cachedForceInfo.currentVersion) < 0
            else -> false
        }

        val isStillForce = isStillBelowMin || (cachedForceInfo.forceUpdate && isStillBelowLatest)
        if (isStillForce) {
            val reqVersion = cachedForceInfo.minimumSupportedVersion
                ?: cachedForceInfo.minVersionCode?.toString()
                ?: cachedForceInfo.currentVersion
                ?: cachedForceInfo.latestVersionCode?.toString()
                ?: ""
            return AppUpdateState.ForceUpdate(
                message = cachedForceInfo.updateMessage,
                requiredVersion = reqVersion,
                currentVersion = currentName,
                updateUrl = cachedForceInfo.updateUrl,
                minVersionCode = cachedForceInfo.minVersionCode,
                latestVersionCode = cachedForceInfo.latestVersionCode
            )
        }
    }

    return AppUpdateState.UpToDate
}

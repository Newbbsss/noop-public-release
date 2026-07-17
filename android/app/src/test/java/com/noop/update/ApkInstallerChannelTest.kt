package com.noop.update

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInstallerChannelTest {

    @Test
    fun samePackageIsOk() {
        assertNull(
            ApkInstaller.packageMismatchReason(
                installedPackage = "com.noop.whoop",
                archivePackage = "com.noop.whoop",
            ),
        )
        assertNull(
            ApkInstaller.packageMismatchReason(
                installedPackage = "com.noop.whoop.debug",
                archivePackage = "com.noop.whoop.debug",
            ),
        )
    }

    @Test
    fun mainVsDebugIsBlocked() {
        val reason = ApkInstaller.packageMismatchReason(
            installedPackage = "com.noop.whoop",
            archivePackage = "com.noop.whoop.debug",
        )
        assertTrue(reason!!.contains("DEBUG", ignoreCase = true))
        assertTrue(reason.contains("MAIN", ignoreCase = true) || reason.contains("matching"))
    }

    @Test
    fun unreadableArchiveIsOk() {
        assertNull(
            ApkInstaller.packageMismatchReason(
                installedPackage = "com.noop.whoop",
                archivePackage = null,
            ),
        )
    }
}

package com.noop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the additive v17 -> v18 Room migration (dual HRV: overnight SDNN beside RMSSD on `dailyMetric`).
 */
class DailyAvgSdnnMigrationTest {

    @Test
    fun migration_isAdditive_onlyAddColumn() {
        val sql = WhoopDatabase.DAILY_AVG_SDNN_MIGRATION_SQL
        assertEquals("one ADD COLUMN statement", 1, sql.size)
        for (s in sql) {
            val up = s.trimStart().uppercase()
            assertTrue("only ALTER TABLE ADD COLUMN allowed, got: $s", up.startsWith("ALTER TABLE"))
            assertTrue("must be an ADD COLUMN, got: $s", up.contains("ADD COLUMN"))
            assertTrue("nullable column must not be NOT NULL: $s", !up.contains("NOT NULL"))
            for (banned in listOf("DROP ", "DELETE ", "UPDATE ", "INSERT ", "RENAME ")) {
                assertTrue("additive migration must not contain '$banned': $s", !up.contains(banned))
            }
        }
    }

    @Test
    fun migration_addsExactColumn() {
        assertEquals(
            listOf("ALTER TABLE `dailyMetric` ADD COLUMN `avgSdnn` REAL"),
            WhoopDatabase.DAILY_AVG_SDNN_MIGRATION_SQL,
        )
    }

    @Test
    fun migration_versionPair_is17to18() {
        assertEquals(17, WhoopDatabase.MIGRATION_17_18.startVersion)
        assertEquals(18, WhoopDatabase.MIGRATION_17_18.endVersion)
    }

    @Test
    fun dailyMetric_avgSdnn_defaultsNull() {
        val bare = DailyMetric(deviceId = "my-whoop", day = "2026-07-15")
        assertEquals(null, bare.avgSdnn)
        assertEquals(null, bare.avgHrv)
        val filled = bare.copy(avgHrv = 42.0, avgSdnn = 55.0)
        assertEquals(42.0, filled.avgHrv)
        assertEquals(55.0, filled.avgSdnn)
    }
}

package com.noop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards additive v19 → v20: sleepStateSample.aux82 + dailyMetric.spo2OpticalAux (never SpO₂ %). */
class SleepStateAux82MigrationTest {

    @Test
    fun migration_isAdditive_alterOnly() {
        val sql = WhoopDatabase.SLEEP_STATE_AUX82_MIGRATION_SQL
        assertEquals(2, sql.size)
        for (s in sql) {
            val up = s.trimStart().uppercase()
            assertTrue("only ALTER TABLE allowed, got: $s", up.startsWith("ALTER TABLE"))
            for (banned in listOf("DROP ", "DELETE ", "UPDATE ", "INSERT ", "CREATE ")) {
                assertTrue("banned $banned in: $s", !up.contains(banned))
            }
        }
        assertTrue(sql[0].contains("sleepStateSample") && sql[0].contains("aux82"))
        assertTrue(sql[1].contains("dailyMetric") && sql[1].contains("spo2OpticalAux"))
    }
}

package com.noop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the additive v18 → v19 Room migration (`imuActivitySample`) for WHOOP 5/MG offload IMU
 * activity features used by denser step estimates.
 */
class ImuActivitySampleMigrationTest {

    @Test
    fun migration_isAdditive_onlyCreateTable() {
        val sql = WhoopDatabase.IMU_ACTIVITY_SAMPLE_MIGRATION_SQL
        assertEquals("one CREATE TABLE statement", 1, sql.size)
        for (s in sql) {
            val up = s.trimStart().uppercase()
            assertTrue("only CREATE TABLE allowed, got: $s", up.startsWith("CREATE TABLE"))
            for (banned in listOf("DROP ", "DELETE ", "UPDATE ", "INSERT ", "ALTER ")) {
                assertTrue("additive migration must not contain '$banned': $s", !up.contains(banned))
            }
        }
    }

    @Test
    fun migration_createsExactTable() {
        assertEquals(
            listOf(
                "CREATE TABLE IF NOT EXISTS `imuActivitySample` (`deviceId` TEXT NOT NULL, " +
                    "`ts` INTEGER NOT NULL, `accelEnergyG` REAL NOT NULL, `gyroEnergyDps` REAL NOT NULL, " +
                    "`jerkRms` REAL NOT NULL, `cadenceHz` REAL, `cadenceStrength` REAL NOT NULL, " +
                    "`sampleCount` INTEGER NOT NULL, PRIMARY KEY(`deviceId`, `ts`))",
            ),
            WhoopDatabase.IMU_ACTIVITY_SAMPLE_MIGRATION_SQL,
        )
    }

    @Test
    fun migration_versionPair_is18to19() {
        assertEquals(18, WhoopDatabase.MIGRATION_18_19.startVersion)
        assertEquals(19, WhoopDatabase.MIGRATION_18_19.endVersion)
    }

    @Test
    fun entity_shape_nullableCadence() {
        val row = ImuActivitySample(
            deviceId = "my-whoop",
            ts = 1_780_916_150L,
            accelEnergyG = 0.02,
            gyroEnergyDps = 5.0,
            jerkRms = 0.01,
            cadenceHz = null,
            cadenceStrength = 0.0,
            sampleCount = 100,
        )
        assertEquals("my-whoop", row.deviceId)
        assertEquals(null, row.cadenceHz)
        assertEquals(100, row.sampleCount)
    }
}

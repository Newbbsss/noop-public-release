package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HonestVitalsLabelsTest {

    @Test
    fun bp_blankWithoutCuff() {
        val line = HonestVitalsLabels.bpLine(null, null, null)
        assertEquals("—", line.valueText)
        assertEquals(HonestVitalsLabels.Provenance.BLANK, line.provenance)
        assertTrue(line.caption.contains("Lab Book", ignoreCase = true) || line.caption.contains("cuff", ignoreCase = true))
        assertFalse(line.caption.contains("decoded live", ignoreCase = true))
        assertEquals(StrandTone.Neutral, HonestVitalsLabels.provenanceTone(line.provenance))
    }

    @Test
    fun bp_labBookWhenBothPresent() {
        val line = HonestVitalsLabels.bpLine(118.4, 76.2, "2026-07-09")
        assertEquals("118/76", line.valueText)
        assertEquals(HonestVitalsLabels.Provenance.LAB_BOOK, line.provenance)
        assertTrue(line.caption.contains("2026-07-09") || line.caption.contains("Cuff", ignoreCase = true))
        assertEquals("Lab Book", HonestVitalsLabels.provenanceLabel(line.provenance))
    }

    @Test
    fun spo2_blankWhenMissing() {
        val line = HonestVitalsLabels.spo2Line(null)
        assertEquals("—", line.valueText)
        assertEquals(HonestVitalsLabels.Provenance.BLANK, line.provenance)
        assertTrue(line.caption.contains("import", ignoreCase = true) || line.caption.contains("offload", ignoreCase = true))
    }

    @Test
    fun spo2_blankNeverInventedFromZeroOrOutOfRange() {
        // Raw ADC / empty bank must not become a clinical-looking %.
        assertEquals("—", HonestVitalsLabels.spo2Line(0.0).valueText)
        assertEquals(HonestVitalsLabels.Provenance.BLANK, HonestVitalsLabels.spo2Line(0.0).provenance)
        assertEquals("—", HonestVitalsLabels.spo2Line(-1.0).valueText)
        assertEquals("—", HonestVitalsLabels.spo2Line(101.0).valueText)
        assertEquals(HonestVitalsLabels.Provenance.BLANK, HonestVitalsLabels.spo2Line(101.0).provenance)
    }

    @Test
    fun spo2_measuredWhenBanked() {
        val line = HonestVitalsLabels.spo2Line(97.0)
        assertEquals("97%", line.valueText)
        assertEquals(HonestVitalsLabels.Provenance.MEASURED, line.provenance)
    }

    @Test
    fun spo2_rawAdcWhenSleepOffloadPresent_neverPercent() {
        val line = HonestVitalsLabels.spo2Line(null, spo2Red = 31000, spo2Ir = 28000)
        assertEquals("29500", line.valueText)
        assertFalse(line.valueText.contains("%"))
        assertEquals(HonestVitalsLabels.Provenance.ESTIMATE, line.provenance)
        assertTrue(line.caption.contains("ADC", ignoreCase = true))
        assertTrue(line.caption.contains("not %", ignoreCase = true))
    }

    @Test
    fun spo2_mgOpticalAuxBanked_neverPercentDigit() {
        val line = HonestVitalsLabels.spo2Line(null, opticalAuxBanked = true)
        assertEquals("raw", line.valueText)
        assertFalse(line.valueText.contains("%"))
        assertEquals(HonestVitalsLabels.Provenance.ESTIMATE, line.provenance)
        assertTrue(line.caption.contains("MG", ignoreCase = true))
        assertTrue(line.caption.contains("not %", ignoreCase = true))
    }

    @Test
    fun spo2_percentWinsOverRawAdc() {
        val line = HonestVitalsLabels.spo2Line(96.0, spo2Red = 31000, spo2Ir = 28000)
        assertEquals("96%", line.valueText)
        assertEquals(HonestVitalsLabels.Provenance.MEASURED, line.provenance)
    }

    @Test
    fun bp_partialCuffStaysBlank() {
        // One of sys/dia/day missing → blank tile, never invent the other side.
        assertEquals("—", HonestVitalsLabels.bpLine(120.0, null, "2026-07-14").valueText)
        assertEquals("—", HonestVitalsLabels.bpLine(null, 80.0, "2026-07-14").valueText)
        assertEquals("—", HonestVitalsLabels.bpLine(120.0, 80.0, null).valueText)
        assertEquals("—", HonestVitalsLabels.bpLine(120.0, 80.0, "").valueText)
        assertEquals(
            HonestVitalsLabels.Provenance.BLANK,
            HonestVitalsLabels.bpLine(118.0, null, "2026-07-14").provenance,
        )
    }

    @Test
    fun vo2_estimateLabeled() {
        val line = HonestVitalsLabels.vo2Line(42.0)
        assertEquals("42", line.valueText)
        assertEquals(HonestVitalsLabels.Provenance.ESTIMATE, line.provenance)
        assertTrue(line.caption.contains("Estimate", ignoreCase = true))
    }

    @Test
    fun vo2_blankWhenNone() {
        assertEquals(HonestVitalsLabels.Provenance.BLANK, HonestVitalsLabels.vo2Line(null).provenance)
    }
}

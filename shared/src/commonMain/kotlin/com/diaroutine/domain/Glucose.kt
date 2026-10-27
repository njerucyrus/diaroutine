package com.diaroutine.domain

import com.diaroutine.core.format.Fmt
import com.diaroutine.domain.model.GlucoseUnit
import com.diaroutine.domain.model.Profile
import kotlin.math.roundToInt

enum class GlucoseLevel(val label: String) {
    Low("Low"),
    InRange("In range"),
    High("High"),
}

object Glucose {
    const val MG_PER_MMOL = 18.0
    const val MIN_MG_DL = 20
    const val MAX_MG_DL = 600

    fun format(mgDl: Int, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MgDl -> mgDl.toString()
        GlucoseUnit.MmolL -> Fmt.oneDecimal(mgDl / MG_PER_MMOL)
    }

    fun formatWithUnit(mgDl: Int, unit: GlucoseUnit): String = "${format(mgDl, unit)} ${unit.label}"

    /** The typed value in mg/dL, or null when it isn't a number in the plausible meter range. */
    fun parse(text: String, unit: GlucoseUnit): Int? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val mgDl = when (unit) {
            GlucoseUnit.MgDl -> value
            GlucoseUnit.MmolL -> value * MG_PER_MMOL
        }.roundToInt()
        return mgDl.takeIf { it in MIN_MG_DL..MAX_MG_DL }
    }

    fun level(mgDl: Int, profile: Profile): GlucoseLevel = when {
        mgDl < profile.targetLowMgDl -> GlucoseLevel.Low
        mgDl > profile.targetHighMgDl -> GlucoseLevel.High
        else -> GlucoseLevel.InRange
    }

    /** "70–180 mg/dL" */
    fun targetLabel(profile: Profile): String =
        "${format(profile.targetLowMgDl, profile.glucoseUnit)}–${format(profile.targetHighMgDl, profile.glucoseUnit)} ${profile.glucoseUnit.label}"

    fun validRangeLabel(unit: GlucoseUnit): String =
        "${format(MIN_MG_DL, unit)} and ${format(MAX_MG_DL, unit)} ${unit.label}"
}

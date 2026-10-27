package com.diaroutine.backup

import com.diaroutine.data.db.MedicationEntity
import com.diaroutine.data.db.ProfileEntity
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupFormatTest {
    @Test
    fun recoveryKeysRoundTripAsEightGroupsOfFour() {
        repeat(50) {
            val key = Random.nextBytes(RecoveryKey.BYTES)
            val text = RecoveryKey.format(key)
            assertTrue(Regex("""[0-9A-Z]{4}(-[0-9A-Z]{4}){7}""").matches(text), text)
            assertContentEquals(key, RecoveryKey.parse(text))
        }
    }

    @Test
    fun readingAKeyForgivesCaseSpacingAndLookAlikes() {
        val key = Random.nextBytes(RecoveryKey.BYTES)
        val text = RecoveryKey.format(key)
        val sloppy = text.lowercase().replace("-", " ").replace('0', 'o').replace('1', 'l')
        assertContentEquals(key, RecoveryKey.parse(sloppy))
    }

    @Test
    fun incompleteOrInvalidKeysAreRejected() {
        val text = RecoveryKey.format(Random.nextBytes(RecoveryKey.BYTES))
        assertNull(RecoveryKey.parse(text.dropLast(1)))
        assertNull(RecoveryKey.parse(text + "A"))
        assertNull(RecoveryKey.parse(text.replaceRange(0, 1, "U")))
        assertNull(RecoveryKey.parse(""))
    }

    @Test
    fun backupDataRoundTripsThroughJson() {
        val data = BackupData(
            createdAtMillis = 1_800_000_000_000,
            appVersion = "1.0.0",
            profiles = listOf(
                ProfileEntity(
                    name = "Lina", glucoseUnit = "MgDl", targetLowMgDl = 70, targetHighMgDl = 180, waterGoalGlasses = 8,
                    stepGoal = 6000, walkGoalMinutes = 30, walkMinuteOfDay = 1080, glucoseChecks = "420:Fasting",
                    remindersEnabled = true, caregiverEnabled = false, caregiverName = "", caregiverContact = "",
                    onboarded = true, appLockEnabled = true, followUpMinutes = 15,
                ),
            ),
            medications = listOf(
                MedicationEntity(
                    id = 3, name = "Metformin", strength = "500 mg", form = "Tablet", amountPerDose = 1, times = "480,1200",
                    asNeeded = false, instructions = "", supplyRemaining = 40, refillAt = 7, startEpochDay = 20000, endEpochDay = null,
                ),
            ),
        )
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }

    @Test
    fun backupsFromANewerAppAreRefused() {
        val newer = BackupCodec.encode(BackupData(format = BackupCodec.FORMAT + 1, createdAtMillis = 0, appVersion = "9"))
        assertFailsWith<UnsupportedBackupException> { BackupCodec.decode(newer) }
    }

    @Test
    fun columnsAddedLaterDefaultWhenAnOlderBackupLacksThem() {
        val older = """{"format":1,"createdAtMillis":0,"appVersion":"1.0.0","profiles":[{"id":0,"name":"Sam","glucoseUnit":"MgDl",
            |"targetLowMgDl":70,"targetHighMgDl":180,"waterGoalGlasses":8,"stepGoal":6000,"walkGoalMinutes":30,"walkMinuteOfDay":null,
            |"glucoseChecks":"","remindersEnabled":true,"caregiverEnabled":false,"caregiverName":"","caregiverContact":"","onboarded":true}]}
        """.trimMargin()
        val profile = BackupCodec.decode(older.encodeToByteArray()).profiles.single()
        assertEquals(false, profile.appLockEnabled)
        assertEquals(30, profile.followUpMinutes)
    }
}

package com.seolhwa.armyrist.data

import org.junit.Assert.*
import org.junit.Test

class GuardedSnapshotStoreTest {
    private class Disk(var raw: String? = "1,2") {
        val errors = mutableListOf<String>()
        var writes = 0
        var failNext = false
        val store = GuardedSnapshotStore(
            readRaw = { raw },
            writeRaw = { value ->
                writes++
                raw = value // Models SharedPreferences' memory-before-disk behavior.
                if (failNext) { failNext = false; false } else true
            },
            decode = { value -> if (value == "[]") emptyList() else value.split(',').map(String::toInt) },
            encode = { value: List<Int> -> if (value.isEmpty()) "[]" else value.joinToString(",") },
            onError = errors::add
        )
    }

    @Test fun corruptLoadCannotBeOverwrittenByNewDocument() {
        val disk = Disk("1,broken,3")
        assertEquals(emptyList<Int>(), disk.store.load(emptyList()))
        assertFalse(disk.store.save(listOf(4)))
        assertEquals("1,broken,3", disk.raw)
        assertEquals(0, disk.writes)
        assertTrue(disk.errors.isNotEmpty())
    }

    @Test fun failedCommitRollsBackSharedPreferencesMemory() {
        val disk = Disk()
        val before = disk.store.load(emptyList())
        disk.failNext = true
        assertFalse(disk.store.save(listOf(9)))
        assertEquals("1,2", disk.raw)
        assertEquals(before, disk.store.load(emptyList()))
        assertTrue(disk.errors.isNotEmpty())
    }

    @Test fun successfulCommitCanBeReadByNewReader() {
        val disk = Disk()
        assertTrue(disk.store.save(listOf(3, 4)))
        assertEquals(listOf(3, 4), disk.store.load(emptyList()))
    }

    @Test fun corruptionAfterLoadAlsoBlocksSave() {
        val disk = Disk()
        assertEquals(listOf(1, 2), disk.store.load(emptyList()))
        disk.raw = "bad"
        assertFalse(disk.store.save(listOf(8)))
        assertEquals("bad", disk.raw)
    }

    @Test fun reloadFailureKeepsLastGoodVisibleState() {
        val disk = Disk()
        val good = disk.store.load(emptyList())
        disk.raw = "bad"
        assertEquals(good, disk.store.load(emptyList(), good))
        assertFalse(disk.store.readable)
    }

    @Test fun explicitBackupRestoreUnlocksStoreAfterReload() {
        val disk = Disk("bad")
        disk.store.load(emptyList())
        disk.raw = "5,6" // Full restore replaces the on-disk snapshot explicitly.
        assertEquals(listOf(5, 6), disk.store.load(emptyList()))
        assertTrue(disk.store.readable)
        assertTrue(disk.store.save(listOf(7)))
    }

    @Test fun absentSnapshotMeansEmptyInsteadOfOldData() {
        val disk = Disk(null)
        assertEquals(emptyList<Int>(), disk.store.load(emptyList(), listOf(1)))
        assertTrue(disk.store.save(listOf(2)))
    }
}

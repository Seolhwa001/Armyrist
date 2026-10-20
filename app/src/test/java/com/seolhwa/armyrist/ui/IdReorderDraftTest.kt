package com.seolhwa.armyrist.ui

import org.junit.Assert.*
import org.junit.Test

class IdReorderDraftTest {
    private val original = listOf("a", "b", "c", "d")
    @Test fun multiplePointerMovesProduceOneFinalWrite() {
        val draft = IdReorderDraft(original)
        assertTrue(draft.begin("a"))
        draft.moveTo(1); draft.moveTo(2); draft.moveTo(3)
        var saves = 0
        assertEquals(listOf("a", "b", "c", "d"), original)
        draft.finish(true)?.let { saves++; assertEquals(listOf("b", "c", "d", "a"), it) }
        draft.finish(true)?.let { saves++ }
        assertEquals(1, saves)
    }
    @Test fun cancelRestoresOrderWithoutSave() {
        val draft = IdReorderDraft(original)
        draft.begin("b"); draft.moveTo(3)
        assertNull(draft.finish(false))
        assertEquals(original, draft.order)
    }
    @Test fun returnToOriginDoesNotSave() {
        val draft = IdReorderDraft(original)
        draft.begin("b"); draft.moveTo(3); draft.moveTo(1)
        assertNull(draft.finish(true))
    }
    @Test fun staleExternalSnapshotCannotResetActiveDrag() {
        val draft = IdReorderDraft(original)
        draft.begin("a"); draft.moveTo(2); draft.sync(original)
        assertEquals(listOf("b", "c", "a", "d"), draft.order)
    }
    @Test fun failedSaveCanRestoreCanonicalOrder() {
        val draft = IdReorderDraft(original)
        draft.begin("a"); draft.moveTo(2); draft.finish(true)
        draft.sync(original)
        assertEquals(original, draft.order)
    }
    @Test fun invalidIdCannotStartDrag() {
        assertFalse(IdReorderDraft(original).begin("missing"))
    }
    @Test fun invalidRepositoryOrdersCannotDropOrDuplicateItems() {
        assertNull(reorderedByIds(original, listOf("a", "a", "c", "d")) { it })
        assertNull(reorderedByIds(original, listOf("a", "b", "c")) { it })
        assertNull(reorderedByIds(original, listOf("a", "b", "c", "other")) { it })
        assertEquals(original.reversed(), reorderedByIds(original, original.reversed()) { it })
    }
}

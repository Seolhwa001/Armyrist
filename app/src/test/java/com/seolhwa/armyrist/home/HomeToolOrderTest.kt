package com.seolhwa.armyrist.home

import org.junit.Assert.*
import org.junit.Test

class HomeToolOrderTest {
    @Test fun corruptedStorageUsesDefaults() { assertEquals(HomeToolOrder.defaults, HomeToolOrder.decode("broken")) }
    @Test fun missingStorageUsesDefaults() { assertEquals(HomeToolOrder.defaults, HomeToolOrder.decode(null)) }
    @Test fun unknownAndDuplicateToolsAreRemoved() { assertEquals(HomeToolOrder.defaults, HomeToolOrder.normalize(listOf("bad", "counting", "counting"))) }
    @Test fun newlyAddedToolsAreAppended() { assertEquals(listOf("report", "counting", "checklist", "timeplan", "handicap"), HomeToolOrder.normalize(listOf("report"))) }
    @Test fun lastToolCanMoveToFirst() { assertEquals(listOf("handicap", "counting", "checklist", "timeplan", "report"), HomeToolOrder.move(HomeToolOrder.defaults, "handicap", "counting")) }
    @Test fun firstToolCanMoveToLast() { assertEquals(listOf("checklist", "timeplan", "report", "handicap", "counting"), HomeToolOrder.move(HomeToolOrder.defaults, "counting", "handicap")) }
    @Test fun adjacentMoveKeepsAllToolsExactlyOnce() { val moved = HomeToolOrder.move(HomeToolOrder.defaults, "counting", "checklist"); assertEquals("checklist", moved.first()); assertEquals(5, moved.toSet().size) }
    @Test fun sameTargetDoesNotMove() { assertEquals(HomeToolOrder.defaults, HomeToolOrder.move(HomeToolOrder.defaults, "counting", "counting")) }
    @Test fun unknownTargetDoesNotMove() { assertEquals(HomeToolOrder.defaults, HomeToolOrder.move(HomeToolOrder.defaults, "counting", "bad")) }
    @Test fun orderSurvivesSaveAndRestore() { val moved = HomeToolOrder.move(HomeToolOrder.defaults, "handicap", "counting"); assertEquals(moved, HomeToolOrder.decode(HomeToolOrder.encode(moved))) }
    @Test fun repeatedPlacementIntoSameSlotDoesNotUndoMove() { val moved = HomeToolOrder.moveToIndex(HomeToolOrder.defaults, "handicap", 0); assertEquals(moved, HomeToolOrder.moveToIndex(moved, "handicap", 0)) }
    @Test fun outOfRangeSlotDoesNotMove() { assertEquals(HomeToolOrder.defaults, HomeToolOrder.moveToIndex(HomeToolOrder.defaults, "counting", 5)) }
    @Test fun negativeSlotDoesNotMove() { assertEquals(HomeToolOrder.defaults, HomeToolOrder.moveToIndex(HomeToolOrder.defaults, "counting", -1)) }
    @Test fun unknownSourceDoesNotMove() { assertEquals(HomeToolOrder.defaults, HomeToolOrder.moveToIndex(HomeToolOrder.defaults, "bad", 0)) }
}

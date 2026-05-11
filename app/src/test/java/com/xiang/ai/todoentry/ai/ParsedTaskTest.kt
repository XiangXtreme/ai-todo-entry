package com.xiang.ai.todoentry.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ParsedTaskTest {
    @Test
    fun importanceDefaultsToNormal() {
        assertEquals(TaskImportance.NORMAL, TaskImportance.from(null))
        assertEquals(TaskImportance.NORMAL, TaskImportance.from("urgent"))
    }

    @Test
    fun importanceMapsKnownValues() {
        assertEquals(TaskImportance.LOW, TaskImportance.from("low"))
        assertEquals(TaskImportance.HIGH, TaskImportance.from("HIGH"))
    }
}

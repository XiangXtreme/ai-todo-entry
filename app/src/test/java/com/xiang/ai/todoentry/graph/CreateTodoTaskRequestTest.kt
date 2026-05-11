package com.xiang.ai.todoentry.graph

import com.xiang.ai.todoentry.ai.ParsedTask
import com.xiang.ai.todoentry.ai.TaskImportance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class CreateTodoTaskRequestTest {
    @Test
    fun mapsParsedTaskToGraphRequest() {
        val request = CreateTodoTaskRequest.from(
            ParsedTask(
                title = "Buy milk",
                body = "Organic if available",
                dueDateTime = "2026-05-12T15:00:00",
                reminderDateTime = "2026-05-12T14:30:00",
                importance = TaskImportance.HIGH
            ),
            ZoneId.of("Asia/Shanghai")
        )

        assertEquals("Buy milk", request.title)
        assertEquals("Organic if available", request.body?.content)
        assertEquals("text", request.body?.contentType)
        assertEquals("2026-05-12T15:00", request.dueDateTime?.dateTime)
        assertEquals("Asia/Shanghai", request.dueDateTime?.timeZone)
        assertEquals(true, request.isReminderOn)
        assertEquals("high", request.importance)
    }

    @Test
    fun ignoresInvalidDateStrings() {
        val request = CreateTodoTaskRequest.from(
            ParsedTask(title = "Buy milk", dueDateTime = "tomorrow"),
            ZoneId.of("UTC")
        )

        assertNull(request.dueDateTime)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankTitle() {
        CreateTodoTaskRequest.from(ParsedTask(title = " "))
    }
}

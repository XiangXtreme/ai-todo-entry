package com.xiang.ai.todoentry.ai

import com.xiang.ai.todoentry.settings.AppSettings
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiTaskParserConnectivityTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parserCallsOpenAiCompatibleEndpointAndParsesTaskJson() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\"title\":\"Buy milk\",\"body\":\"Organic\",\"dueDateTime\":\"2026-05-12T15:00:00\",\"reminderDateTime\":\"2026-05-12T14:30:00\",\"importance\":\"high\",\"targetListName\":\"Tasks\",\"confidence\":0.92}"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val batch = OpenAiTaskParser().parse(
            input = "Remind me tomorrow at 3 PM to buy milk",
            settings = AppSettings(llmBaseUrl = server.url("/v1").toString().trimEnd('/'), llmModel = "test-model"),
            apiKey = "test-key"
        )
        val task = batch.tasks.single()

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("test-model"))
        assertEquals("Buy milk", task.title)
        assertEquals("Organic", task.body)
        assertEquals(TaskImportance.HIGH, task.importance)
        assertEquals("Tasks", task.targetListName)
    }

    @Test
    fun parserParsesTaskArray() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "[{\"title\":\"Buy milk\",\"importance\":\"normal\",\"confidence\":0.9},{\"title\":\"Submit report\",\"importance\":\"high\",\"confidence\":0.8}]"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val batch = OpenAiTaskParser().parse(
            input = "Buy milk and submit report",
            settings = AppSettings(llmBaseUrl = server.url("/v1").toString().trimEnd('/'), llmModel = "test-model"),
            apiKey = "test-key"
        )

        assertEquals(2, batch.tasks.size)
        assertEquals("Buy milk", batch.tasks[0].title)
        assertEquals("Submit report", batch.tasks[1].title)
        assertEquals(TaskImportance.HIGH, batch.tasks[1].importance)
    }
}

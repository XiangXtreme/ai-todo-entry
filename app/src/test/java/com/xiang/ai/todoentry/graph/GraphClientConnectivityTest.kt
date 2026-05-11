package com.xiang.ai.todoentry.graph

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GraphClientConnectivityTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GraphClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GraphClient(graphBaseUrl = server.url("/v1.0").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getListsCallsGraphListsEndpoint() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"value":[{"id":"list-1","displayName":"Tasks"}]}""")
        )

        val lists = client.getLists("token")
        val request = server.takeRequest()

        assertEquals("/v1.0/me/todo/lists", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("Tasks", lists.single().displayName)
    }

    @Test
    fun createTaskPostsToSelectedList() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"task-1","title":"Buy milk","importance":"high"}""")
        )

        val task = client.createTask(
            accessToken = "token",
            listId = "list-1",
            requestBody = CreateTodoTaskRequest(title = "Buy milk", importance = "high")
        )
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("/v1.0/me/todo/lists/list-1/tasks", request.path)
        assertEquals("POST", request.method)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertTrue(body.contains("\"title\":\"Buy milk\""))
        assertEquals("task-1", task.id)
        assertEquals("Buy milk", task.title)
    }

    @Test
    fun getTasksCallsSelectedListTasksEndpoint() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"value":[{"id":"task-1","title":"Buy milk","status":"notStarted"}]}""")
        )

        val tasks = client.getTasks("token", "list-1")
        val request = server.takeRequest()

        assertEquals("/v1.0/me/todo/lists/list-1/tasks?\$top=50", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("Buy milk", tasks.single().title)
        assertEquals("notStarted", tasks.single().status)
    }

    @Test
    fun updateTaskPatchesStatus() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"task-1","title":"Buy milk","status":"completed"}""")
        )

        val task = client.updateTask(
            accessToken = "token",
            listId = "list-1",
            taskId = "task-1",
            requestBody = UpdateTodoTaskRequest(status = "completed")
        )
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("/v1.0/me/todo/lists/list-1/tasks/task-1", request.path)
        assertEquals("PATCH", request.method)
        assertTrue(body.contains("\"status\":\"completed\""))
        assertEquals("completed", task.status)
    }

    @Test
    fun updateTaskPatchesTitleAndBody() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"task-1","title":"Buy oat milk","body":{"content":"Two bottles","contentType":"text"}}""")
        )

        val task = client.updateTask(
            accessToken = "token",
            listId = "list-1",
            taskId = "task-1",
            requestBody = UpdateTodoTaskRequest(
                title = "Buy oat milk",
                body = ItemBody(content = "Two bottles")
            )
        )
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("/v1.0/me/todo/lists/list-1/tasks/task-1", request.path)
        assertEquals("PATCH", request.method)
        assertTrue(body.contains("\"title\":\"Buy oat milk\""))
        assertTrue(body.contains("\"content\":\"Two bottles\""))
        assertEquals("Two bottles", task.body?.content)
    }

    @Test
    fun deleteTaskCallsDeleteEndpoint() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        client.deleteTask("token", "list-1", "task-1")
        val request = server.takeRequest()

        assertEquals("/v1.0/me/todo/lists/list-1/tasks/task-1", request.path)
        assertEquals("DELETE", request.method)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }
}

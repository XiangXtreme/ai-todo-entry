package com.xiang.ai.todoentry.graph

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GraphClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
    private val graphBaseUrl: String = GRAPH_BASE
) {
    private val listResponseAdapter = moshi.adapter(TodoListResponse::class.java)
    private val taskResponseAdapter = moshi.adapter(TodoTaskResponse::class.java)
    private val createTaskAdapter = moshi.adapter(CreateTodoTaskRequest::class.java)
    private val updateTaskAdapter = moshi.adapter(UpdateTodoTaskRequest::class.java)
    private val taskAdapter = moshi.adapter(TodoTaskDto::class.java)

    suspend fun getLists(accessToken: String): List<TodoListDto> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${graphBaseUrl.trimEnd('/')}/me/todo/lists")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GraphException(response.code, body)
            listResponseAdapter.fromJson(body)?.value.orEmpty()
        }
    }

    suspend fun createTask(accessToken: String, listId: String, requestBody: CreateTodoTaskRequest): TodoTaskDto =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${graphBaseUrl.trimEnd('/')}/me/todo/lists/$listId/tasks")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(createTaskAdapter.toJson(requestBody).toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw GraphException(response.code, body)
                taskAdapter.fromJson(body) ?: TodoTaskDto(title = requestBody.title)
            }
        }

    suspend fun getTasks(accessToken: String, listId: String): List<TodoTaskDto> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${graphBaseUrl.trimEnd('/')}/me/todo/lists/$listId/tasks?\$top=50")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GraphException(response.code, body)
            taskResponseAdapter.fromJson(body)?.value.orEmpty()
        }
    }

    suspend fun getTask(accessToken: String, listId: String, taskId: String): TodoTaskDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${graphBaseUrl.trimEnd('/')}/me/todo/lists/$listId/tasks/$taskId")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GraphException(response.code, body)
            taskAdapter.fromJson(body) ?: throw GraphException(response.code, body)
        }
    }

    suspend fun updateTask(accessToken: String, listId: String, taskId: String, requestBody: UpdateTodoTaskRequest): TodoTaskDto =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${graphBaseUrl.trimEnd('/')}/me/todo/lists/$listId/tasks/$taskId")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .patch(updateTaskAdapter.toJson(requestBody).toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw GraphException(response.code, body)
                taskAdapter.fromJson(body) ?: TodoTaskDto(id = taskId, title = requestBody.title.orEmpty(), status = requestBody.status)
            }
        }

    suspend fun deleteTask(accessToken: String, listId: String, taskId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${graphBaseUrl.trimEnd('/')}/me/todo/lists/$listId/tasks/$taskId")
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GraphException(response.code, body)
        }
    }

    companion object {
        private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class GraphException(val statusCode: Int, val responseBody: String) :
    RuntimeException("Graph request failed: HTTP $statusCode")

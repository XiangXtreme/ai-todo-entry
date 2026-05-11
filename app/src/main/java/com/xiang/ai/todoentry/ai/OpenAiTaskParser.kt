package com.xiang.ai.todoentry.ai

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.xiang.ai.todoentry.settings.AppSettings
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class OpenAiTaskParser(
    private val client: OkHttpClient = OkHttpClient(),
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) : TaskParser {
    private val chatAdapter = moshi.adapter(ChatRequest::class.java)
    private val responseAdapter = moshi.adapter(ChatResponse::class.java)
    private val taskAdapter = moshi.adapter(LlmTaskJson::class.java)
    private val taskListAdapter = moshi.adapter<List<LlmTaskJson>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, LlmTaskJson::class.java)
    )

    override suspend fun parse(input: String, settings: AppSettings, apiKey: String): ParsedTaskBatch = withContext(Dispatchers.IO) {
        val body = chatAdapter.toJson(
            ChatRequest(
                model = settings.llmModel,
                messages = listOf(
                    Message("system", buildSystemPrompt()),
                    Message("user", input)
                ),
                temperature = 0.1
            )
        ).toRequestBody(JSON)

        val request = Request.Builder()
            .url("${settings.llmBaseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("LLM request failed: HTTP ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            val content = responseAdapter.fromJson(payload)
                ?.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                ?: throw IllegalStateException("LLM response did not include content")
            val parsed = parseTasks(content)
            if (parsed.tasks.isEmpty()) {
                throw IllegalStateException("LLM response did not include any valid tasks")
            }
            parsed
        }
    }

    private fun parseTasks(content: String): ParsedTaskBatch {
        val json = extractJson(content)
        val tasks = if (json.startsWith("[")) {
            taskListAdapter.fromJson(json).orEmpty()
        } else {
            listOfNotNull(taskAdapter.fromJson(json))
        }
        return ParsedTaskBatch(tasks.mapNotNull { it.toDomainOrNull() })
    }

    private fun LlmTaskJson.toDomainOrNull(): ParsedTask? {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null
        return ParsedTask(
            title = cleanTitle,
            body = body?.trim()?.takeIf { it.isNotBlank() },
            dueDateTime = dueDateTime?.trim()?.takeIf { it.isNotBlank() },
            reminderDateTime = reminderDateTime?.trim()?.takeIf { it.isNotBlank() },
            importance = TaskImportance.from(importance),
            targetListName = targetListName?.trim()?.takeIf { it.isNotBlank() },
            confidence = confidence?.coerceIn(0f, 1f) ?: 0f
        )
    }

    private fun extractJson(content: String): String {
        val objectStart = content.indexOf('{')
        val arrayStart = content.indexOf('[')
        val start = when {
            arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart) -> arrayStart
            objectStart >= 0 -> objectStart
            else -> -1
        }
        val end = if (start >= 0 && content[start] == '[') content.lastIndexOf(']') else content.lastIndexOf('}')
        require(start >= 0 && end > start) { "LLM response did not contain JSON" }
        return content.substring(start, end + 1)
    }

    private fun buildSystemPrompt(): String {
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault().id
        return SYSTEM_PROMPT_TEMPLATE
            .replace("{{CURRENT_LOCAL_DATETIME}}", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .replace("{{CURRENT_TIME_ZONE}}", zoneId)
    }

    @JsonClass(generateAdapter = false)
    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double
    )

    @JsonClass(generateAdapter = false)
    data class Message(
        val role: String,
        val content: String
    )

    @JsonClass(generateAdapter = false)
    data class ChatResponse(
        val choices: List<Choice> = emptyList()
    )

    @JsonClass(generateAdapter = false)
    data class Choice(
        val message: Message? = null
    )

    @JsonClass(generateAdapter = false)
    data class LlmTaskJson(
        val title: String = "",
        val body: String? = null,
        val dueDateTime: String? = null,
        val reminderDateTime: String? = null,
        val importance: String? = null,
        val targetListName: String? = null,
        val confidence: Float? = null
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private const val SYSTEM_PROMPT_TEMPLATE = """
You parse natural language into one or more Microsoft To Do tasks.
Return only a JSON array, no markdown.
Each item must use this shape:
{"title":"string","body":null,"dueDateTime":null,"reminderDateTime":null,"importance":"normal","targetListName":null,"confidence":0.0}
Current local date-time is {{CURRENT_LOCAL_DATETIME}} in {{CURRENT_TIME_ZONE}}.
Resolve relative dates like today, tomorrow, next week from the current local date-time.
Use ISO-8601 local date-time strings when dates are explicit or strongly implied.
Use this exact date-time format: yyyy-MM-ddTHH:mm:ss.
Do not include timezone offsets such as Z or +08:00 in dueDateTime or reminderDateTime.
importance must be one of low, normal, high.
Split clearly separate requests into separate tasks.
If unsure, set nullable fields to null and lower confidence.
"""
    }
}

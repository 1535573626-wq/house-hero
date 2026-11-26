package com.roomhero.app.network

import android.content.Context
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector3
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Tripo 3D 模型生成客户端（v3 API）。
 *
 * - 无 token 或 token 为空时返回本地程序化五角星 MeshResource
 * - 有 token 时调用 Tripo v3 API 文本生成 3D 模型，下载 GLB 后加载为 MeshResource
 * - 结果按 prompt 内存缓存 + 本地文件缓存，避免重复请求
 *
 * 使用方法：
 * ```kotlin
 * TripoClient.init(context)
 * TripoClient.setToken("YOUR_TRIPO_API_KEY")
 * val mesh = TripoClient.generate("golden trophy", rank = TripoRank.GOLD)
 * ```
 */
object TripoClient {

    // Tripo v2 已于 2026-10-31 下线，默认使用 v3 全球端点。
    // 可在 local.properties 里加 TRIPO_API_ENDPOINT 覆盖，例如 https://openapi.tripo3d.com/v3
    private var baseUrl: String = "https://openapi.tripo3d.ai/v3"
    private const val POLL_INTERVAL_MS = 3000L
    private const val MAX_POLL_SECONDS = 180L

    private var appContext: Context? = null
    private var apiToken: String? = null
    private val cache = mutableMapOf<String, MeshResource>()

    fun init(context: Context) {
        appContext = context.applicationContext
        // 优先从 local.properties 注入的 BuildConfig 读取 token 与端点
        if (apiToken.isNullOrBlank()) {
            apiToken = com.roomhero.app.BuildConfig.TRIPO_API_TOKEN
        }
        val endpoint = com.roomhero.app.BuildConfig.TRIPO_API_ENDPOINT
        if (endpoint.isNotBlank()) {
            baseUrl = endpoint
        }
    }

    fun setToken(token: String) {
        apiToken = token
    }

    /**
     * 根据 [prompt] 异步生成一个 3D 模型 MeshResource。
     *
     * 无 token 或网络失败时返回本地程序化五角星作为 fallback。
     */
    suspend fun generate(prompt: String, rank: TripoRank = TripoRank.BRONZE): MeshResource? {
        val token = apiToken
        if (token.isNullOrBlank()) {
            return getOrCreateFallback()
        }

        val enhancedPrompt = "${rank.promptPrefix}, low poly, game asset, solid color, centered"
        cache[enhancedPrompt]?.let { return it }

        // 本地文件缓存：相同 prompt 不重复调用 API
        val cachedFile = getCachedFile(enhancedPrompt)
        if (cachedFile != null) {
            return MeshResource.load(cachedFile.absolutePath, LoadType.FROM_STORAGE)?.also {
                cache[enhancedPrompt] = it
            } ?: getOrCreateFallback()
        }

        return withContext(Dispatchers.IO) {
            try {
                val taskId = createTask(enhancedPrompt, token)
                val modelUrl = pollTask(taskId, token)
                val glbFile = downloadModel(modelUrl, enhancedPrompt)
                val mesh = MeshResource.load(glbFile.absolutePath, LoadType.FROM_STORAGE)
                    ?: throw IllegalStateException("MeshResource.load returned null")
                cache[enhancedPrompt] = mesh
                mesh
            } catch (e: Exception) {
                e.printStackTrace()
                getOrCreateFallback()
            }
        }
    }

    private fun createTask(prompt: String, token: String): String {
        val url = URL("$baseUrl/generation/text-to-model")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }

        val body = JSONObject().apply {
            put("prompt", prompt)
            put("model", "v3.1-20260211")
            put("texture", true)
            put("pbr", true)
            put("face_limit", 30000)
            put("auto_size", true)
            put("geometry_quality", "standard")
        }.toString()

        connection.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = connection.responseCode
        val response = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
        }
        val json = JSONObject(response)
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException("Tripo create task failed ($responseCode): $response")
        return data.getString("task_id")
    }

    private suspend fun pollTask(taskId: String, token: String): String {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(MAX_POLL_SECONDS)

        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)

            val url = URL("$baseUrl/tasks/$taskId")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val responseCode = connection.responseCode
            val response = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            }
            val json = JSONObject(response)
            val data = json.optJSONObject("data")
                ?: throw IllegalStateException("Tripo poll task failed ($responseCode): $response")

            val status = data.optString("status", "").lowercase()
            when (status) {
                "success", "completed" -> {
                    val output = data.optJSONObject("output")
                        ?: throw IllegalStateException("Tripo task has no output: $response")
                    return output.getString("model_url")
                }
                "failed", "error", "cancelled" -> throw IllegalStateException("Tripo task failed: $response")
                else -> continue
            }
        }

        throw IllegalStateException("Tripo task polling timeout")
    }

    private fun downloadModel(urlString: String, prompt: String): File {
        val context = appContext ?: throw IllegalStateException("TripoClient not initialized")
        val cacheDir = File(context.cacheDir, "tripo_models").apply { mkdirs() }
        val promptHash = prompt.hashCode().toUInt().toString(16)
        val outputFile = File(cacheDir, "reward_${promptHash}.glb")

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return outputFile
    }

    private fun getCachedFile(prompt: String): File? {
        val context = appContext ?: return null
        val cacheDir = File(context.cacheDir, "tripo_models").apply { mkdirs() }
        val promptHash = prompt.hashCode().toUInt().toString(16)
        val file = File(cacheDir, "reward_${promptHash}.glb")
        return if (file.exists() && file.length() > 0) file else null
    }

    private fun getOrCreateFallback(): MeshResource {
        return fallbackStar ?: createStarMesh().also { fallbackStar = it }
    }

    private var fallbackStar: MeshResource? = null

    private fun createStarMesh(): MeshResource {
        val radiusOuter = 0.15f
        val radiusInner = 0.06f
        val height = 0.04f
        val vertices = mutableListOf<Vector3>()
        val indices = mutableListOf<Int>()

        val topCenter = vertices.size
        vertices.add(Vector3(0f, height / 2f, 0f))
        val bottomCenter = vertices.size
        vertices.add(Vector3(0f, -height / 2f, 0f))

        val topStart = vertices.size
        for (i in 0 until 10) {
            val angle = Math.PI * 2 * i / 10 - Math.PI / 2
            val r = if (i % 2 == 0) radiusOuter else radiusInner
            vertices.add(
                Vector3(
                    (r * kotlin.math.cos(angle)).toFloat(),
                    height / 2f,
                    (r * kotlin.math.sin(angle)).toFloat(),
                ),
            )
        }

        val bottomStart = vertices.size
        for (i in 0 until 10) {
            val angle = Math.PI * 2 * i / 10 - Math.PI / 2
            val r = if (i % 2 == 0) radiusOuter else radiusInner
            vertices.add(
                Vector3(
                    (r * kotlin.math.cos(angle)).toFloat(),
                    -height / 2f,
                    (r * kotlin.math.sin(angle)).toFloat(),
                ),
            )
        }

        for (i in 0 until 10) {
            val next = (i + 1) % 10
            indices.add(topCenter)
            indices.add(topStart + next)
            indices.add(topStart + i)
        }

        for (i in 0 until 10) {
            val next = (i + 1) % 10
            indices.add(bottomCenter)
            indices.add(bottomStart + i)
            indices.add(bottomStart + next)
        }

        for (i in 0 until 10) {
            val next = (i + 1) % 10
            val t0 = topStart + i
            val t1 = topStart + next
            val b0 = bottomStart + i
            val b1 = bottomStart + next
            indices.add(t0)
            indices.add(b0)
            indices.add(t1)
            indices.add(t1)
            indices.add(b0)
            indices.add(b1)
        }

        val model = MeshModel(
            positions = vertices,
            triangleIndices = indices,
        )
        return MeshResource.createWithMeshModel(model, name = "fallback_star")
    }
}

enum class TripoRank(val starCount: Int, val promptPrefix: String) {
    GOLD(3, "shiny golden trophy"),
    SILVER(2, "silver medal trophy"),
    BRONZE(1, "bronze trophy"),
}

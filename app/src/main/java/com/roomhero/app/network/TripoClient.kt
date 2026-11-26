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
 * Tripo 3D 模型生成客户端。
 *
 * - 无 token 或 token 为空时返回本地程序化五角星 MeshResource
 * - 有 token 时调用 Tripo API 文本生成 3D 模型，下载 GLB 后加载为 MeshResource
 * - 结果按 prompt 缓存，避免重复请求
 *
 * 使用方法：
 * ```kotlin
 * TripoClient.init(context)
 * TripoClient.setToken("YOUR_TRIPO_API_KEY")
 * val mesh = TripoClient.generate("golden trophy", rank = TripoRank.GOLD)
 * ```
 */
object TripoClient {

    private const val BASE_URL = "https://api.tripo3d.ai/v2/openapi"
    private const val POLL_INTERVAL_MS = 2000L
    private const val MAX_POLL_SECONDS = 120L

    private var appContext: Context? = null
    private var apiToken: String? = null
    private val cache = mutableMapOf<String, MeshResource>()

    fun init(context: Context) {
        appContext = context.applicationContext
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

        cache[prompt]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val enhancedPrompt = "${rank.promptPrefix} $prompt, low poly, game asset, solid color"
                val taskId = createTask(enhancedPrompt, token)
                val modelUrl = pollTask(taskId, token)
                val glbFile = downloadModel(modelUrl)
                val mesh = MeshResource.load(glbFile.absolutePath, LoadType.FROM_STORAGE)
                    ?: throw IllegalStateException("MeshResource.load returned null")
                cache[prompt] = mesh
                mesh
            } catch (e: Exception) {
                e.printStackTrace()
                getOrCreateFallback()
            }
        }
    }

    private fun createTask(prompt: String, token: String): String {
        val url = URL("$BASE_URL/task")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }

        val body = JSONObject().apply {
            put("type", "text_to_model")
            put("prompt", prompt)
            // 可选：控制生成质量/速度，具体以 Tripo 文档为准
            put("version", "v2.0-20240919")
        }.toString()

        connection.outputStream.use { it.write(body.toByteArray()) }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException("Tripo create task failed: $response")
        return data.getString("task_id")
    }

    private suspend fun pollTask(taskId: String, token: String): String {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(MAX_POLL_SECONDS)

        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)

            val url = URL("$BASE_URL/task/$taskId")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val data = json.optJSONObject("data")
                ?: throw IllegalStateException("Tripo poll task failed: $response")

            val status = data.optString("status", "")
            when (status.lowercase()) {
                "success" -> {
                    val model = data.optJSONObject("model")
                        ?: throw IllegalStateException("Tripo task has no model: $response")
                    return model.getString("url")
                }
                "failed", "error" -> throw IllegalStateException("Tripo task failed: $response")
                else -> continue
            }
        }

        throw IllegalStateException("Tripo task polling timeout")
    }

    private fun downloadModel(urlString: String): File {
        val context = appContext ?: throw IllegalStateException("TripoClient not initialized")
        val cacheDir = File(context.cacheDir, "tripo_models").apply { mkdirs() }
        val outputFile = File(cacheDir, "reward_${System.currentTimeMillis()}.glb")

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return outputFile
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

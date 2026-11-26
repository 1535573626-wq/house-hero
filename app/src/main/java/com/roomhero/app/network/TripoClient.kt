package com.roomhero.app.network

import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector3

/**
 * Tripo 3D 模型生成客户端占位实现。
 *
 * 当前 token 尚未分发，因此所有生成请求都返回本地程序化 MeshResource。
 * 拿到真实 token 后，把 [generate] 替换为 HTTP 调用：
 * 1. POST /v2/openapi/task 创建文本生成任务
 * 2. 轮询 /v2/openapi/task/{task_id} 获取模型下载 URL
 * 3. 下载 GLB/OBJ 后通过 [MeshResource.load] 加载
 */
object TripoClient {

    private var apiToken: String? = null

    fun setToken(token: String) {
        apiToken = token
    }

    /**
     * 根据 [prompt] 生成一个 3D 模型 MeshResource。
     *
     * 当前为占位实现，直接返回一个五角星 MeshResource。
     */
    fun generate(prompt: String): MeshResource? {
        // TODO: 替换为真实 Tripo API 调用
        return createStarMesh()
    }

    private fun createStarMesh(): MeshResource? {
        // 用细长三角面片拼一个简单五角星（扁平，面向 Y 轴上方）
        val radiusOuter = 0.15f
        val radiusInner = 0.06f
        val height = 0.04f
        val vertices = mutableListOf<Vector3>()
        val indices = mutableListOf<Int>()

        // 上表面中心
        val topCenter = vertices.size
        vertices.add(Vector3(0f, height / 2f, 0f))
        // 下表面中心
        val bottomCenter = vertices.size
        vertices.add(Vector3(0f, -height / 2f, 0f))

        val topStart = vertices.size
        for (i in 0 until 10) {
            val angle = Math.PI * 2 * i / 10 - Math.PI / 2
            val r = if (i % 2 == 0) radiusOuter else radiusInner
            vertices.add(Vector3((r * kotlin.math.cos(angle)).toFloat(), height / 2f, (r * kotlin.math.sin(angle)).toFloat()))
        }

        val bottomStart = vertices.size
        for (i in 0 until 10) {
            val angle = Math.PI * 2 * i / 10 - Math.PI / 2
            val r = if (i % 2 == 0) radiusOuter else radiusInner
            vertices.add(Vector3((r * kotlin.math.cos(angle)).toFloat(), -height / 2f, (r * kotlin.math.sin(angle)).toFloat()))
        }

        // 上表面三角面
        for (i in 0 until 10) {
            val next = (i + 1) % 10
            indices.add(topCenter)
            indices.add(topStart + next)
            indices.add(topStart + i)
        }

        // 下表面三角面
        for (i in 0 until 10) {
            val next = (i + 1) % 10
            indices.add(bottomCenter)
            indices.add(bottomStart + i)
            indices.add(bottomStart + next)
        }

        // 侧面四边形（拆成两个三角）
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

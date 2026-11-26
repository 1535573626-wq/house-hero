package com.roomhero.app.manager

import android.content.Context
import android.content.SharedPreferences
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.sense.world.WorldAnchor
import com.pico.spatial.sense.world.WorldTrackingManager
import com.pico.spatial.sense.world.WorldTrackingResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "house_hero_furniture"
private const val KEY_LAYOUT = "furniture_layout"
private const val KEY_ANCHORS = "furniture_anchor_uuids"

/**
 * 家具布局持久化与空间锚点管理器。
 *
 * - 使用 [SharedPreferences] 保存家具世界坐标/旋转作为 fallback
 * - 使用 [WorldTrackingManager.createAnchor] 创建空间锚点，让布局在真实空间中更稳定
 * - 加载时优先恢复锚点；锚点失效时回退到 SharedPreferences 坐标
 */
object FurnitureAnchorManager {

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存当前家具布局。
     *
     * 同时写入本地坐标并为每个家具创建 WorldAnchor。
     */
    suspend fun saveLayout(items: List<FurnitureSnapshot>) {
        val pref = prefs ?: return

        val layoutArray = JSONArray()
        items.forEach { snapshot ->
            layoutArray.put(
                JSONObject().apply {
                    put("x", snapshot.position.x)
                    put("y", snapshot.position.y)
                    put("z", snapshot.position.z)
                    put("qx", snapshot.rotation.x)
                    put("qy", snapshot.rotation.y)
                    put("qz", snapshot.rotation.z)
                    put("qw", snapshot.rotation.w)
                    put("anchorUUID", snapshot.anchorUUID?.toString() ?: "")
                    put("label", snapshot.label ?: "")
                },
            )
        }

        // 创建 WorldAnchor
        val uuids = mutableListOf<String>()
        items.mapIndexed { index, snapshot ->
            val euler = snapshot.rotation.toEulerAngles()
            val result = WorldTrackingManager.createAnchor(
                position = snapshot.position,
                rotation = com.pico.spatial.core.math.EulerAngles(euler.pitch, euler.yaw, euler.roll),
                name = "house_hero_furniture_$index",
            )
            if (result is WorldTrackingResult.Success) {
                result.data?.anchorUUID?.toString()?.let { uuids.add(it) }
            }
        }

        pref.edit().apply {
            putString(KEY_LAYOUT, layoutArray.toString())
            putString(KEY_ANCHORS, uuids.joinToString(","))
            apply()
        }
    }

    /**
     * 加载上次保存的家具布局。
     *
     * 优先使用 WorldAnchor 的当前位置；锚点加载失败则使用本地坐标。
     */
    suspend fun loadLayout(): List<FurnitureSnapshot>? {
        val pref = prefs ?: return null
        val layoutJson = pref.getString(KEY_LAYOUT, null) ?: return null

        val snapshots = mutableListOf<FurnitureSnapshot>()
        val layoutArray = JSONArray(layoutJson)
        for (i in 0 until layoutArray.length()) {
            val obj = layoutArray.getJSONObject(i)
            val anchorUUID = obj.optString("anchorUUID", "").takeIf { it.isNotBlank() }?.let {
                try {
                    java.util.UUID.fromString(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            snapshots.add(
                FurnitureSnapshot(
                    position = Vector3(
                        obj.getDouble("x").toFloat(),
                        obj.getDouble("y").toFloat(),
                        obj.getDouble("z").toFloat(),
                    ),
                    rotation = Quat(
                        obj.getDouble("qx").toFloat(),
                        obj.getDouble("qy").toFloat(),
                        obj.getDouble("qz").toFloat(),
                        obj.getDouble("qw").toFloat(),
                    ),
                    anchorUUID = anchorUUID,
                    label = obj.optString("label", "").takeIf { it.isNotBlank() },
                ),
            )
        }

        // 尝试加载 WorldAnchor 并覆盖坐标
        val uuidString = pref.getString(KEY_ANCHORS, null)
        if (!uuidString.isNullOrBlank()) {
            val uuids = uuidString.split(",").mapNotNull {
                try {
                    java.util.UUID.fromString(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }.toTypedArray()

            val result = WorldTrackingManager.loadAnchor(uuids)
            if (result is WorldTrackingResult.Success) {
                result.data?.forEachIndexed { index, anchor ->
                    anchor?.let {
                        if (index < snapshots.size) {
                            snapshots[index] = snapshots[index].copy(
                                position = it.transform.position,
                                rotation = it.transform.quaternion,
                            )
                        }
                    }
                }
            }
        }

        return snapshots.ifEmpty { null }
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}

/**
 * 单个家具的持久化快照。
 *
 * @param anchorUUID 对应真实家具扫描锚点的 UUID，用于跨会话匹配
 * @param label 家具语义标签，如 SOFA、CABINET 等
 */
data class FurnitureSnapshot(
    val position: Vector3,
    val rotation: Quat,
    val anchorUUID: java.util.UUID? = null,
    val label: String? = null,
)

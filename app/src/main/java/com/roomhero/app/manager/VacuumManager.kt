package com.roomhero.app.manager

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val VACUUM_DISTANCE = 0.8f

/**
 * 管理虚拟吸尘器。
 *
 * MVP 版本将吸尘器固定在 HMD 前方 [VACUUM_DISTANCE] 米处，跟随头部移动。
 * 后续可扩展为手柄追踪或手势追踪。
 */
object VacuumManager {
    val root: Entity = Entity()

    private var hmdProvider: HMDTrackingProvider? = null
    private var updateJob: Job? = null

    var currentPosition: Vector3 = Vector3(0f, 0f, 0f)
        private set

    private val vacuumMaterial by lazy {
        PhysicallyBasedMaterial.create().apply {
            setBaseColor(Color4(1.0f, 0.9f, 0.2f, 1.0f))
            setOpacity(0.6f)
        }
    }

    /**
     * 初始化吸尘器可视化模型并启动位置更新。
     */
    fun start() {
        stop()

        val mesh = MeshResource.createSphere(0.08f)
            ?: MeshResource.createBox(Vector3(0.15f, 0.15f, 0.15f), 0f)
            ?: return

        val visual = Entity().apply {
            components.set(ModelComponent(mesh, vacuumMaterial))
        }
        root.addChild(visual)

        hmdProvider = HMDTrackingProvider().apply { start() }

        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updatePosition()
                delay(16) // ~60fps
            }
        }
    }

    /**
     * 停止追踪并清理资源。
     */
    fun stop() {
        updateJob?.cancel()
        updateJob = null
        hmdProvider?.stop()
        hmdProvider = null

        root.getChildren().forEach { it.destroy() }
    }

    private fun updatePosition() {
        // 优先使用手部追踪位置；未检测到手时回退到 HMD 前方固定点
        if (HandTrackingManager.isTracking) {
            val handPos = HandTrackingManager.currentHandPosition
            root.components[TransformComponent::class.java]?.position = handPos
            currentPosition = handPos
            return
        }

        val provider = hmdProvider ?: return
        val pose = provider.latestData.hmdPose

        val forward = pose.rotation.rotateVector(Vector3(0f, 0f, -1f))
        val position = pose.position + forward * VACUUM_DISTANCE

        root.components[TransformComponent::class.java]?.position = position
        currentPosition = position
    }
}

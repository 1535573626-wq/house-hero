package com.roomhero.app.manager

import android.content.Context
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ParticleComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.AudioResource
import com.pico.spatial.core.ecs.particle.ParticleColorVaryingProperty
import com.pico.spatial.core.ecs.particle.ParticleEmitterShape
import com.pico.spatial.core.ecs.particle.ParticleEmitterShapeType
import com.pico.spatial.core.ecs.particle.ParticleEmissionLocation
import com.pico.spatial.core.ecs.particle.ParticleEmissionSpace
import com.pico.spatial.core.ecs.particle.ParticleFloatVaryingProperty
import com.pico.spatial.core.ecs.particle.ParticleVaryingPropertyType
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Int3
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 游戏反馈管理器：空间音效 + 消失动画 + 粒子特效。
 *
 * - 清洁/整理成功时播放 3D 空间音效
 * - 成功时被清除的实体会先执行缩小动画再销毁
 * - 游戏结束时触发彩带雨粒子
 */
object FeedbackManager {

    private var context: Context? = null
    private var successAudio: AudioResource? = null
    private var placeAudio: AudioResource? = null
    private var finishAudio: AudioResource? = null

    private val animatingEntities = mutableListOf<AnimatingEntity>()
    private val activeParticles = mutableListOf<Entity>()

    private data class AnimatingEntity(
        val entity: Entity,
        var scale: Float,
        val shrinkSpeed: Float,
    )

    /**
     * 在应用启动时保存 Context，音频资源延迟到首次播放时加载。
     *
     * 必须在 Spatial SDK 初始化完成后再加载音频，否则 native 库未就绪会崩溃。
     */
    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private fun ensureAudioLoaded() {
        if (successAudio != null || placeAudio != null || finishAudio != null) return

        try {
            successAudio = AudioResource.load("success", "success.wav", LoadType.FROM_ASSETS)
            placeAudio = AudioResource.load("place", "place.wav", LoadType.FROM_ASSETS)
            finishAudio = AudioResource.load("finish", "finish.wav", LoadType.FROM_ASSETS)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 播放清洁成功音效（在 [position] 处）。
     */
    fun playCleanSuccess(position: Vector3) {
        playSpatial(successAudio, position)
    }

    /**
     * 播放整理成功音效（在 [position] 处）。
     */
    fun playPlaceSuccess(position: Vector3) {
        playSpatial(placeAudio, position)
    }

    /**
     * 播放游戏结束音效（在 HMD 前方），并触发彩带雨粒子特效。
     */
    fun playFinish(position: Vector3) {
        playSpatial(finishAudio, position)
        playConfettiRain(position)
    }

    /**
     * 在 [center] 上方触发一场向下飘落的彩带雨，持续数秒后自动销毁。
     */
    fun playConfettiRain(center: Vector3) {
        val emitter = Entity().apply {
            components[TransformComponent::class.java]?.position =
                Vector3(center.x, center.y + 2.5f, center.z)
        }

        val particle = ParticleComponent().apply {
            emitterShape = ParticleEmitterShape(
                type = ParticleEmitterShapeType.BOX,
                size = Vector3(2.5f, 0.1f, 2.5f),
                emissionLocation = ParticleEmissionLocation.SURFACE,
                vertexCount = Int3(2, 2, 2),
                emissionSpace = ParticleEmissionSpace.WORLD,
                emissionDirection = Vector3(0f, -1f, 0f),
                radialSpan = 360f,
                torusInnerRadius = 0f,
            )
            mainSimulation.apply {
                maxParticleCount = 2000
                startColor = ParticleColorVaryingProperty(
                    ParticleVaryingPropertyType.RANDOM,
                    Color4(1f, 0.2f, 0.4f, 1f),
                    Color4(0.2f, 0.8f, 1f, 1f),
                )
                startSize = ParticleFloatVaryingProperty(
                    ParticleVaryingPropertyType.RANDOM, 0.02f, 0.06f,
                )
                startSpeed = ParticleFloatVaryingProperty(
                    ParticleVaryingPropertyType.RANDOM, 1.0f, 2.5f,
                )
                lifetime = ParticleFloatVaryingProperty(
                    ParticleVaryingPropertyType.RANDOM, 2.0f, 3.5f,
                )
                emitSpreadingAngle = 25f
                isMotionEnabled = true
                acceleration = Vector3(0f, -2.0f, 0f)
            }
            isEmitting = true
        }

        emitter.components.set(particle)
        activeParticles.add(emitter)

        // 5 秒后停止发射并销毁
        CoroutineScope(Dispatchers.Main).launch {
            delay(5000)
            particle.isEmitting = false
            delay(4000)
            emitter.destroy()
            activeParticles.remove(emitter)
        }
    }

    private fun playSpatial(audio: AudioResource?, position: Vector3) {
        ensureAudioLoaded()
        val resource = audio ?: return
        val entity = Entity().apply {
            components[TransformComponent::class.java]?.position = position
        }
        entity.playAudio(resource)
        // 音效实体短暂存在后销毁
        fadeOutEntity(entity)
    }

    /**
     * 让 [entity] 执行缩小消失动画。
     */
    fun fadeOutEntity(entity: Entity) {
        animatingEntities.add(AnimatingEntity(entity, 1f, shrinkSpeed = 4f))
    }

    /**
     * 每帧更新动画，返回是否还有未完成的动画。
     */
    fun updateAnimations(dtSeconds: Float): Boolean {
        val iterator = animatingEntities.iterator()
        while (iterator.hasNext()) {
            val anim = iterator.next()
            anim.scale -= anim.shrinkSpeed * dtSeconds
            if (anim.scale <= 0.05f) {
                anim.entity.destroy()
                iterator.remove()
            } else {
                anim.entity.components[TransformComponent::class.java]?.scaleVector =
                    Vector3(anim.scale, anim.scale, anim.scale)
            }
        }
        return animatingEntities.isNotEmpty()
    }

    fun release() {
        successAudio?.close()
        placeAudio?.close()
        finishAudio?.close()
        successAudio = null
        placeAudio = null
        finishAudio = null
    }
}

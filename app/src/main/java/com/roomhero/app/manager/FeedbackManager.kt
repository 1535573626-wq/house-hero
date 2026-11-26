package com.roomhero.app.manager

import android.content.Context
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.AudioResource
import com.pico.spatial.core.math.Vector3

/**
 * 游戏反馈管理器：空间音效 + 消失动画。
 *
 * - 清洁/整理成功时播放 3D 空间音效
 * - 成功时被清除的实体会先执行缩小动画再销毁
 */
object FeedbackManager {

    private var context: Context? = null
    private var successAudio: AudioResource? = null
    private var placeAudio: AudioResource? = null
    private var finishAudio: AudioResource? = null

    private val animatingEntities = mutableListOf<AnimatingEntity>()

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
     * 播放游戏结束音效（在 HMD 前方）。
     */
    fun playFinish(position: Vector3) {
        playSpatial(finishAudio, position)
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

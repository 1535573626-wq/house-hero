package com.roomhero.app.manager

import android.content.Context
import android.content.SharedPreferences
import com.roomhero.app.viewmodel.AppMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "house_hero_achievements"
private const val KEY_TOTAL_DIRT = "total_dirt_cleaned"
private const val KEY_TOTAL_ITEMS = "total_items_organized"
private const val KEY_BEST_CLEAN_TIME = "best_clean_time_seconds"
private const val KEY_BEST_ORGANIZE_TIME = "best_organize_time_seconds"
private const val KEY_HIGHEST_SCORE = "highest_score"
private const val KEY_LAST_PLAY_DATE = "last_play_date"
private const val KEY_STREAK_DAYS = "streak_days"
private const val KEY_UNLOCKED = "unlocked_achievements"

/**
 * 成就定义。
 *
 * @param id 唯一标识，也用于 SharedPreferences 持久化
 * @param title 展示名称
 * @param description 解锁条件描述
 * @param check 根据当前统计判断是否已经解锁
 */
enum class Achievement(
    val id: String,
    val title: String,
    val description: String,
) {
    FIRST_BLOOD(
        "first_blood",
        "初次清洁",
        "累计清理 1 片彩带",
    ) {
        override fun check(stats: Stats) = stats.totalDirtCleaned >= 1
    },

    CLEAN_MASTER(
        "clean_master",
        "清洁大师",
        "累计清理 50 片彩带",
    ) {
        override fun check(stats: Stats) = stats.totalDirtCleaned >= 50
    },

    ORGANIZE_NOVICE(
        "organize_novice",
        "整理新手",
        "累计整理 1 件物品",
    ) {
        override fun check(stats: Stats) = stats.totalItemsOrganized >= 1
    },

    ORGANIZE_PRO(
        "organize_pro",
        "整理专家",
        "累计整理 20 件物品",
    ) {
        override fun check(stats: Stats) = stats.totalItemsOrganized >= 20
    },

    SPEED_CLEANER(
        "speed_cleaner",
        "速清达人",
        "清洁模式 30 秒内完成且进度 > 80%",
    ) {
        override fun check(stats: Stats) =
            stats.bestCleanTimeSeconds in 1..30 && stats.lastCleanProgress > 80
    },

    PERFECT_CLEANER(
        "perfect_cleaner",
        "完美清洁",
        "清洁模式完成度 100%",
    ) {
        override fun check(stats: Stats) = stats.lastCleanProgress >= 100
    },

    SPEED_ORGANIZER(
        "speed_organizer",
        "速整达人",
        "整理模式 30 秒内完成且进度 > 80%",
    ) {
        override fun check(stats: Stats) =
            stats.bestOrganizeTimeSeconds in 1..30 && stats.lastOrganizeProgress > 80
    },

    PERFECT_ORGANIZER(
        "perfect_organizer",
        "完美整理",
        "整理模式完成度 100%",
    ) {
        override fun check(stats: Stats) = stats.lastOrganizeProgress >= 100
    },

    HIGH_SCORER(
        "high_scorer",
        "高分玩家",
        "单局得分 ≥ 500",
    ) {
        override fun check(stats: Stats) = stats.highestScore >= 500
    },

    FURNITURE_PLANNER(
        "furniture_planner",
        "空间规划师",
        "保存过一次家具布局",
    ) {
        override fun check(stats: Stats) = stats.furnitureLayoutsSaved >= 1
    },

    STREAK_3(
        "streak_3",
        "三日连击",
        "连续 3 天打开应用",
    ) {
        override fun check(stats: Stats) = stats.streakDays >= 3
    };

    abstract fun check(stats: Stats): Boolean
}

/**
 * 用于判断成就的当前统计快照。
 */
data class Stats(
    val totalDirtCleaned: Int = 0,
    val totalItemsOrganized: Int = 0,
    val bestCleanTimeSeconds: Int = Int.MAX_VALUE,
    val bestOrganizeTimeSeconds: Int = Int.MAX_VALUE,
    val highestScore: Int = 0,
    val furnitureLayoutsSaved: Int = 0,
    val streakDays: Int = 0,
    val lastCleanProgress: Int = 0,
    val lastOrganizeProgress: Int = 0,
)

/**
 * 本地成就与统计管理器。
 *
 * - 累计清理彩带数、整理物品数、最佳用时、最高分数、连续天数
 * - 单局结束后检测并解锁新成就
 * - 通过 [newUnlocks] Flow 把新成就抛给 HUD 展示
 */
object AchievementManager {

    private var prefs: SharedPreferences? = null

    private val _newUnlocks = MutableStateFlow<List<Achievement>>(emptyList())
    val newUnlocks: StateFlow<List<Achievement>> = _newUnlocks

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 单局游戏结束时调用，更新统计并检测成就。
     *
     * @param mode 游戏模式
     * @param score 本局得分
     * @param progressPercent 完成百分比
     * @param elapsedSeconds 本局已用秒数（从开始到结束）
     * @param completedCount 本局完成的目标数量
     */
    fun onGameFinished(
        mode: AppMode,
        score: Int,
        progressPercent: Int,
        elapsedSeconds: Int,
        completedCount: Int,
    ) {
        val pref = prefs ?: return

        updateStreak(pref)

        val editor = pref.edit()
        val totalDirt = pref.getInt(KEY_TOTAL_DIRT, 0) + if (mode == AppMode.Clean) completedCount else 0
        val totalItems = pref.getInt(KEY_TOTAL_ITEMS, 0) + if (mode == AppMode.Organize) completedCount else 0
        val highestScore = maxOf(pref.getInt(KEY_HIGHEST_SCORE, 0), score)

        editor.putInt(KEY_TOTAL_DIRT, totalDirt)
        editor.putInt(KEY_TOTAL_ITEMS, totalItems)
        editor.putInt(KEY_HIGHEST_SCORE, highestScore)

        if (mode == AppMode.Clean && progressPercent > 0) {
            val bestClean = pref.getInt(KEY_BEST_CLEAN_TIME, Int.MAX_VALUE)
            if (elapsedSeconds in 1..<bestClean) {
                editor.putInt(KEY_BEST_CLEAN_TIME, elapsedSeconds)
            }
        }
        if (mode == AppMode.Organize && progressPercent > 0) {
            val bestOrganize = pref.getInt(KEY_BEST_ORGANIZE_TIME, Int.MAX_VALUE)
            if (elapsedSeconds in 1..<bestOrganize) {
                editor.putInt(KEY_BEST_ORGANIZE_TIME, elapsedSeconds)
            }
        }

        editor.apply()

        val stats = buildStats(
            lastCleanProgress = if (mode == AppMode.Clean) progressPercent else 0,
            lastOrganizeProgress = if (mode == AppMode.Organize) progressPercent else 0,
        )
        checkAndUnlock(stats)
    }

    /**
     * 家具布局保存成功时调用。
     */
    fun onFurnitureLayoutSaved() {
        val pref = prefs ?: return
        val current = pref.getInt("furniture_layouts_saved", 0)
        pref.edit().putInt("furniture_layouts_saved", current + 1).apply()
        checkAndUnlock(buildStats())
    }

    /**
     * 应用启动时调用，仅用于更新连续天数成就。
     */
    fun onAppOpened() {
        updateStreak(prefs ?: return)
        checkAndUnlock(buildStats())
    }

    /**
     * 消费掉 [newUnlocks] 里待展示的成就，避免重复显示。
     */
    fun consumeNewUnlocks() {
        _newUnlocks.value = emptyList()
    }

    /**
     * 获取当前所有已解锁成就的 id 集合。
     */
    fun unlockedIds(): Set<String> {
        val pref = prefs ?: return emptySet()
        return pref.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()
    }

    private fun updateStreak(pref: SharedPreferences) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = pref.getString(KEY_LAST_PLAY_DATE, null)
        val streak = pref.getInt(KEY_STREAK_DAYS, 0)

        val newStreak = when (lastDate) {
            today -> streak
            getYesterdayString() -> streak + 1
            else -> 1
        }

        pref.edit()
            .putString(KEY_LAST_PLAY_DATE, today)
            .putInt(KEY_STREAK_DAYS, newStreak)
            .apply()
    }

    private fun getYesterdayString(): String {
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun buildStats(
        lastCleanProgress: Int = 0,
        lastOrganizeProgress: Int = 0,
    ): Stats {
        val pref = prefs ?: return Stats()
        val bestClean = pref.getInt(KEY_BEST_CLEAN_TIME, Int.MAX_VALUE)
        val bestOrganize = pref.getInt(KEY_BEST_ORGANIZE_TIME, Int.MAX_VALUE)
        return Stats(
            totalDirtCleaned = pref.getInt(KEY_TOTAL_DIRT, 0),
            totalItemsOrganized = pref.getInt(KEY_TOTAL_ITEMS, 0),
            bestCleanTimeSeconds = if (bestClean == Int.MAX_VALUE) 0 else bestClean,
            bestOrganizeTimeSeconds = if (bestOrganize == Int.MAX_VALUE) 0 else bestOrganize,
            highestScore = pref.getInt(KEY_HIGHEST_SCORE, 0),
            furnitureLayoutsSaved = pref.getInt("furniture_layouts_saved", 0),
            streakDays = pref.getInt(KEY_STREAK_DAYS, 0),
            lastCleanProgress = lastCleanProgress,
            lastOrganizeProgress = lastOrganizeProgress,
        )
    }

    private fun checkAndUnlock(stats: Stats) {
        val pref = prefs ?: return
        val already = unlockedIds().toMutableSet()
        val newly = mutableListOf<Achievement>()

        Achievement.entries.forEach { achievement ->
            if (!already.contains(achievement.id) && achievement.check(stats)) {
                already.add(achievement.id)
                newly.add(achievement)
            }
        }

        if (newly.isNotEmpty()) {
            pref.edit().putStringSet(KEY_UNLOCKED, already).apply()
            _newUnlocks.value = _newUnlocks.value + newly
        }
    }
}

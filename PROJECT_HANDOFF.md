# House Hero — 项目交接/恢复速查

> 当你切换对话、换人接手或一段时间没看项目时，先读这个文件。  
> 它浓缩了项目目标、当前状态、关键文件、已知阻塞和下一步建议。

## 1. 这是什么项目

**House Hero** 是参加 PICO Space Program 2026 —— **Spatial 空间应用创作（Android Kotlin Compose）** 赛道的 MR 应用。

核心创意：把真实家务（扫地、整理桌面、规划家具摆放）游戏化。戴上 PICO 头显后，真实环境被扫描并叠加虚拟游戏层，用户在打扫/整理真实房间的过程中获得分数、成就和 3D 奖励。

## 2. 当前做到什么程度

| 模块 | 状态 | 说明 |
|---|---|---|
| 项目骨架（Kotlin + Compose + Spatial SDK 6.2.8） | ✅ | 可编译、可在 PICO Emulator 运行 |
| 清洁模式 | ✅ | 真实地面/虚拟地面生成彩带，HMD 光标吸取 |
| 整理模式 | ✅ | 优先识别真实桌面物体 mesh，未识别时回退预设方块；拖入收纳虚线框 |
| 摆家具模式 | ✅ | 真实家具 mesh 生成线框占位模型，可拖动试摆，布局持久化 |
| 成就系统 | ✅ | 11 个本地成就，SharedPreferences 持久化，结束阶段弹窗 |
| Tripo 3D 奖励 | ✅ 代码已接 | v3 API、本地文件缓存、并发加载、loading 占位；**真实调用需真机/可联网环境验证** |
| 性能优化 | ✅ 第一轮+第二轮 | 材质共享、空间分区、LOD、分帧生成、对象池、并发加载、性能 HUD |
| 手势追踪 | 🟡 代码已接 | Emulator 无法授予 `HAND_TRACKING` 权限，**需真机验证** |
| 真机验证 | ❌ 未做 | 当前无真机 |

## 3. 关键文件位置

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/roomhero/app/content/GameStage.kt` | 三种模式舞台生命周期 |
| `app/src/main/java/com/roomhero/app/manager/DirtManager.kt` | 清洁模式彩带生成/清洁/LOD/空间分区 |
| `app/src/main/java/com/roomhero/app/manager/OrganizeManager.kt` | 整理模式真实物体识别与拖放 |
| `app/src/main/java/com/roomhero/app/manager/FurnitureManager.kt` | 摆家具模式占位模型与拖动 |
| `app/src/main/java/com/roomhero/app/manager/RoomSurfaceManager.kt` | 平面/网格追踪管理 |
| `app/src/main/java/com/roomhero/app/manager/RewardManager.kt` | 3D 奖励对象池、Tripo 并发加载 |
| `app/src/main/java/com/roomhero/app/manager/AchievementManager.kt` | 成就与统计 |
| `app/src/main/java/com/roomhero/app/manager/PerformanceManager.kt` | FPS/帧时间/DrawCall/三角面监控 |
| `app/src/main/java/com/roomhero/app/ui/GameHUDWindow.kt` | SpatialUI HUD |
| `app/src/main/java/com/roomhero/app/network/TripoClient.kt` | Tripo v3 API 客户端 |
| `ROADMAP.md` | 详细进度与计划 |
| `TRIPO_TOKEN_PLAN.md` | Tripo 积分使用计划 |
| `README.md` | 项目对外介绍 |

## 4. 环境配置

### 4.1 必须配置在 `local.properties`（不要提交到 Git）

```properties
sdk.dir=/Users/ec/Library/Android/sdk
spatial.tools.dir=/Users/ec/Library/PICO/sdk
TRIPO_API_TOKEN=你的_tripo_token
# 可选：中国区可换成 https://openapi.tripo3d.com/v3
TRIPO_API_ENDPOINT=https://openapi.tripo3d.ai/v3
```

当前 `local.properties` 已有 token，**请勿把它 push 到 GitHub**。

### 4.2 运行命令

```bash
cd "/Users/ec/Desktop/飞书/house hero/house-hero"
./gradlew :app:assembleDebug

# 如果 Emulator 没启动
pico-cli emulator start

# 安装并启动
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.roomhero.app
```

## 5. 已知阻塞与限制

1. **无真机**
   - 所有验证都在 PICO Emulator 上进行。
   - 手部追踪权限在 Emulator 上无法授予（日志：`Hand tracking provider create failed`）。
   - SpatialML 真实物体识别效果需在真机上确认。
   - Tripo API 在当前开发环境无法直连 `openapi.tripo3d.ai`，需在真机或可访问该域名的网络下验证。

2. **Android SDK Platform 37 警告**
   - AGP 8.13.2 对 SDK 37 支持未测试，已通过 `android.suppressUnsupportedCompileSdk=37.0` 绕过。
   - 不影响 Emulator 运行，后续可考虑升级 AGP。

3. **Emulator 性能**
   - Emulator 不是真实硬件，FPS 和渲染压力不能代表真机。
   - 性能 HUD 上的数据主要用于相对对比和调试。

## 6. 下一步建议（按优先级）

### 如果拿到真机
1. **真机运行并验证基础流程** — 清洁/整理/摆家具三模式是否都能正常进入和退出。
2. **验证手部追踪权限** — 检查 manifest 权限是否自动授予，HUD 捏合点击是否可用。
3. **验证 Tripo 真实调用** — 运行到结束，点击「生成 3D 奖励」，确认任务创建→轮询→下载 GLB→显示完整流程。
4. **验证整理模式真实物体识别** — 在真实桌面上放几本书/杯子/充电器，看是否能被识别为独立 MeshAnchor。
5. **真机性能测试** — 观察 FPS/帧时间，决定是否进一步降彩带数量、简化 mesh 或加更激进 LOD。

### 如果没有真机
1. **继续打磨 Emulator 可验证的体验** — 粒子效果、UI 动效、音效、成就反馈。
2. **预置 Tripo 模型到 assets** — 如果无法实时调用 API，可预先用少量积分生成典型 GLB 放入 `app/src/main/assets/tripo/`，运行时优先加载 assets。
3. **录制 Emulator 演示视频** — 用于比赛提交材料。
4. **生成 release APK** — `./gradlew :app:assembleRelease`。

## 7. 注意事项

- 不要修改 `local.properties` 中的敏感信息并提交。
- 新增依赖前先确认 Spatial SDK 是否已有对应能力。
- 所有耗时操作（Tripo 加载、大批量 entity 创建）都应放在 IO 线程或分帧处理，避免主线程卡顿。
- 任何真机相关的结论都需要日志/截图/视频证据支撑。

# House Hero — PICO Spatial 家务游戏化 MR 应用

> 赛道：**Spatial 空间应用创作 — Android Kotlin Compose**  
> 仓库：https://github.com/1535573626-wq/house-hero

## 一句话介绍

**House Hero** 把无聊的真实家务变成 MR 游戏：戴上 PICO 头显，自家地面变成满地派对彩带，桌面上的真实物品变成待整理目标，摆家具可以先在 MR 里试摆。打扫/整理/规划的过程被计时、计分、给成就，结束还有 3D 奖励从天而降。

## 核心玩法

| 模式 | 玩法 | 真实空间交互 |
|---|---|---|
| **清洁模式** | 移动吸尘器光标吸走地面彩带 | 真实地面 / 彩带 LOD / 空间音效 / 成功粒子 |
| **整理模式** | 把桌上物品拖入对应虚线收纳框 | 真实桌面 mesh / 真实物体 mesh 识别 / 智能分类标签 |
| **摆家具** | 抓起家具占位模型试摆新布局 | 真实家具 mesh / 线框 ghost / 碰撞警告 / 布局持久化 |

## 已集成的 PICO Spatial SDK 能力

- [x] **Spatial Mesh / Plane Tracking** — 扫描地面、桌面
- [x] **HMD Tracking** — 头部前方光标
- [x] **Hand Tracking / Gesture** — 捏合确认（开始/放下家具/重置）
- [x] **Spatial UI** — HUD 面板、模式选择
- [x] **Spatial Audio** — 3D 音效反馈
- [x] **Physics** — RigidBody/Collision 家具物理
- [x] **Spatial ML / Sense Mesh** — 真实家具语义识别（TABLE/CABINET/SOFA/BED/CHAIR 等）+ 桌面真实物体识别
- [x] **Spatial Anchor** — 家具布局持久化
- [x] **Particle Component** — 游戏结束彩带雨
- [x] **Animation** — 奖励旋转浮动、成功缩放动画
- [x] **Tripo 3D 生成** — 游戏结束后 AI 生成 3D 奖励模型（v3 API，本地缓存，并发加载）

## 性能优化亮点

- **材质共享**：DirtManager 按颜色缓存 `PhysicallyBasedMaterial`，200 片彩带仅 8 个材质实例
- **空间分区**：彩带清洁检测使用二维网格，从 O(n) 降到接近 O(1)
- **距离 LOD**：近距离彩带用 plane mesh（2 tri），远距离用 box mesh（12 tri）
- **分帧生成**：彩带每 20 片 `yield()` 一帧，避免启动跳帧
- **对象池**：RewardManager 复用奖励 Entity/Material
- **并发加载**：多个 Tripo 奖杯 `async` 并行生成，生成中显示 loading 占位
- **性能 HUD**：实时显示 FPS / 帧时间 / 实体数 / 估算 DrawCall / 估算三角面

## 运行方式

```bash
# 1. 编译 debug
./gradlew :app:assembleDebug

# 2. 启动 PICO Emulator（如未启动）
pico-cli emulator start

# 3. 安装并运行
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.roomhero.app
```

Release APK：
```bash
./gradlew :app:assembleRelease
# 输出：app/build/outputs/apk/release/app-release-unsigned.apk
```

## 项目结构

```
app/src/main/java/com/roomhero/app/
├── content/           # GameStage（三种模式舞台）
├── manager/           # DirtManager / OrganizeManager / FurnitureManager /
│                      #  HandTrackingManager / RoomSurfaceManager /
│                      #  FurnitureAnchorManager / FeedbackManager /
│                      #  RewardManager / PerformanceManager / AchievementManager
├── ui/                # GameHUDWindow（SpatialUI HUD）
├── viewmodel/         # GameStateManager
├── network/           # TripoClient（v3 API，本地缓存）
└── platform/          # SpatialApplication / LaunchActivity
```

## 待完成 / 阻塞项

- [ ] **真机验证** — 当前只有 PICO Emulator，手部追踪权限、SpatialML 真实物体识别、Tripo 真实 API 调用需在真机上确认
- [ ] **性能最终调优** — 已做第一轮/第二轮性能压榨，真机后再根据 FPS/帧时间进一步压 LOD/DrawCall

## 开发环境

- Android Studio（最新稳定版）
- Android SDK Platform 37
- PICO Spatial SDK 6.2.8
- PICO Emulator 6.2
- Gradle 8.13
- Kotlin 2.1.20

## 相关文档

- [ROADMAP.md](ROADMAP.md) — 项目全景与进度
- [TRIPO_TOKEN_PLAN.md](TRIPO_TOKEN_PLAN.md) — Tripo token 使用计划
- [PROJECT_HANDOFF.md](PROJECT_HANDOFF.md) — 交接/恢复上下文速查

# House Hero — PICO Spatial 家务游戏化 MR 应用

> 赛道：**Spatial 空间应用创作 — Android Kotlin Compose**  
> 仓库：https://github.com/1535573626-wq/house-hero

## 一句话介绍

**House Hero** 把无聊的真实家务变成 MR 游戏：戴上 PICO 头显，自家地面变成满地派对彩带，桌面变成待整理物品，打扫/整理/摆家具的过程被计时、计分、给成就，结束还有 3D 奖励从天而降。

## 核心玩法

| 模式 | 玩法 | 真实空间交互 |
|---|---|---|
| **清洁模式** | 移动吸尘器光标吸走地面彩带 | 真实地面 / 彩带雨粒子 / 空间音效 |
| **整理模式** | 把桌上物品拖入对应虚线框 | 真实桌面 mesh / 模拟物品分类标签 |
| **摆家具** | 抓起虚拟家具摆放，受重力碰撞 | 真实家具 mesh 碰撞警告 / 布局持久化 |

## 已集成的 PICO Spatial SDK 能力

- [x] **Spatial Mesh / Plane Tracking** — 扫描地面、桌面
- [x] **HMD Tracking** — 头部前方光标
- [x] **Hand Tracking / Gesture** — 捏合确认（开始/放下家具/重置）
- [x] **Spatial UI** — HUD 面板、模式选择
- [x] **Spatial Audio** — 3D 音效反馈
- [x] **Physics** — RigidBody/Collision 家具物理
- [x] **Spatial ML / Sense Mesh** — 真实家具语义识别（TABLE/CABINET/SOFA/BED/CHAIR）
- [x] **Spatial Anchor** — 家具布局持久化
- [x] **Particle Component** — 游戏结束彩带雨
- [x] **Animation** — 奖励旋转浮动、成功缩放动画

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
│                      /  RewardManager / PerformanceManager
├── ui/                # GameHUDWindow（SpatialUI HUD）
├── viewmodel/         # GameStateManager
├── network/           # TripoClient（占位，待 token）
└── platform/          # SpatialApplication / LaunchActivity
```

## 待完成 / 阻塞项

- [ ] **Tripo 3D 真实奖励模型** — `TripoClient` 已接入 Tripo API 异步流程（创建任务→轮询→下载 GLB→加载 MeshResource），无 token 时自动 fallback 五角星，拿到 token 后 `TripoClient.setToken("...")` 即可
- [ ] **真机验证** — Emulator 无法授予手部追踪权限，手部交互/SpatialML 效果需真机确认
- [ ] **性能最终调优** — 已在 HUD 显示 FPS/实体数，真机后再压 LOD/DrawCall

## 开发环境

- Android Studio（最新稳定版）
- Android SDK Platform 37
- PICO Spatial SDK 6.2.8
- PICO Emulator 6.2
- Gradle 8.13
- Kotlin 2.1.20

# House Hero — 项目全景与进度

> 交互备忘：切换对话后快速恢复上下文用。目标、已做、未做、下一步都在这里。  
> 更新时间：2026-11-27

## 1. 一句话创意

**House Hero** = 把真实家务变成 MR 游戏的 PICO Spatial 应用。戴上头显后，自家地面变成满屏派对彩带，桌面变成待整理物品；打扫/整理过程被计分、限时、给成就，结束还能生成 3D 奖杯。自由整理模式则把真实家具扫描成线框占位模型，让用户在 MR 里试摆新布局。

## 2. 我们要把 PICO 性能/能力用到什么程度

赛道：**Spatial 空间应用创作 — Android Kotlin Compose**。  
官方给的 SDK 能力几乎都要覆盖：

| SDK 能力 | 计划用法 | 状态 |
|---|---|---|
| **Spatial Mesh / Plane Tracking** | 扫描真实地面、桌面，锚定彩带/物品/家具 | ✅ 已用 |
| **HMD Tracking** | 头部前方固定光标（吸尘器/抓取点） | ✅ 已用 |
| **Hand Tracking / Gesture** | 捏合右手确认：Ready 开始游戏 / Furniture 抓起时放下 / Finished 重置 | ✅ 已用（Emulator 上报缺权限，功能代码已接入） |
| **Spatial UI (WindowContainer / PicoTheme)** | 悬浮 HUD、模式选择、计时、分数、结束按钮 | ✅ 已用 |
| **Spatial Audio** | 清洁成功、放置成功、游戏结束 3D 音效 | ✅ 已用 |
| **Physics (RigidBody/Collision)** | 早期 Furniture 模式：抓起、释放、重力碰撞 | ✅ 已用；当前自由整理改为 Kinematic 无重力，更贴合“试摆”场景 |
| **Spatial ML / Sense Mesh** | 整理模式：用 MeshTrackingManager 语义 TABLE 对齐真实桌面；家具模式：**加载真实家具 mesh 生成线框占位模型** | ✅ 真实家具 wireframe 占位已上线 |
| **Tripo 3D 生成** | 游戏结束后按分数生成 3D 奖杯/奖励模型；整理模式目标收纳容器；家具模式预置模型库 | 🟡 v3 接口已接 + 本地缓存；当前环境无法直连 `.ai`，需在真机/可联网环境验证 |
| **Spatial Anchor / Persistence** | 保存房间布局、家具位置，下次进应用恢复 | ✅ 已用（UUID 匹配真实家具锚点） |
| **Spatial Video / Passthrough VFX** | 彩带飘落、清洁完成时的粒子/彩带雨、地面 shader 反馈 | 🟡 彩带雨粒子已做；地面 shader / 清洁轨迹待补 |
| **Animation** | 奖励奖杯旋转浮动、成功反馈缩放动画 | 🟡 基础缩放+旋转浮动已做 |

## 3. 比赛加分项与我们对齐情况

根据 PICO Space Program 评分维度（创新性、技术深度、完成度、体验、商业价值）：

| 加分项 | 当前对齐 | 备注 |
|---|---|---|
| 创新性：把无聊家务游戏化 | ✅ | 清洁+整理+摆家具三合一 |
| 深度使用 Spatial SDK | ✅ | Mesh语义+Anchor+粒子+Physics+SpatialUI 已整合 |
| 手势追踪交互 | 🟡 | 捏合确认代码已覆盖 Ready/Playing(Furniture)/Finished；Emulator 缺权限未实际验证 |
| 真实空间理解（mesh/平面/语义） | ✅ | 地面/桌面检测 + 真实家具 mesh 占位 |
| 物理模拟与真实环境结合 | ✅ | 家具模式保留物理碰撞警告；自由整理用 Kinematic 试摆 |
| AI/ML 能力（SpatialML + Tripo） | 🟡 | Sense Mesh 语义已用；Tripo token 已配，待真实调用 |
| 完整 UX 流程（开始→游戏→结束→奖励） | 🟡 | 流程通，奖励 Tripo 接口已接，待真实模型验证 |
| 性能优化 / 压榨硬件 | 🟡 | FPS/实体计数 HUD、彩带数量上限已做；真机 LOD 待测 |
| 可扩展性 / 商业潜力 | 🟡 | 思路有，需包装 |

## 4. 当前已完成的模块

- [x] 项目骨架：Kotlin + Compose + Spatial SDK 6.2.8
- [x] 三种模式切换：清洁 / 整理 / 自由整理（摆家具）
- [x] RoomSurfaceManager：启动 PlaneTracking + MeshTracking，等待地面/桌面/真实家具 mesh，fallback 虚拟表面
- [x] DirtManager：在地面上生成彩色派对彩带，HMD 光标靠近清除
- [x] OrganizeManager：桌面生成物品+虚线目标框，优先对齐真实 TABLE mesh，模拟 SpatialML 物品标签
- [x] **FurnitureManager 升级**：从 `RoomSurfaceManager` 读取真实家具 mesh，按语义生成彩色线框占位模型；可拖动试摆新布局；放下后保持位置不坠落
- [x] FurnitureAnchorManager：SharedPreferences + WorldTrackingManager 空间锚点保存/恢复家具布局；快照带 `anchorUUID` + label，跨会话匹配真实家具
- [x] VacuumManager：HMD 前方光标，跟随头部移动
- [x] FeedbackManager：空间音效 + 缩放消失动画 + 游戏结束彩带雨粒子
- [x] PerformanceManager：FPS 与活跃实体数统计，HUD 实时显示
- [x] DirtManager：派对彩带 + 超大房间自动限制数量（MAX 200）
- [x] GameStateManager：阶段机（Scanning / Ready / Playing / Finished）+ 计分/倒计时
- [x] GameHUDWindow：SpatialUI 悬浮面板，显示模式选择、计时、分数、进度
- [x] RewardManager：游戏结束后按分数生成 1~3 个旋转浮动的 3D 奖杯（本地五角星占位）
- [x] TripoClient 占位：无 token 时返回程序化五角星 mesh；token 已注入 `BuildConfig`

## 5. 还没做但计划做（优先级排序）

### 5.1 当前这一轮要做的（自由整理继续升级）

1. **占位模型视觉升级** ✅
   - 线框 + 半透明填充 ghost 同时显示，更像“可拖动规划模型”
   - 每个占位模型顶部加语义颜色标记小球
   - 拖动时放大+高亮，放下后恢复

2. **整理模式目标收纳容器** ✅ 本地版已完成
   - 每个物品对应一个目标收纳容器虚线框：Book→书架、Cup→杯托、Charger→长条盒、Toy→玩具箱
   - 每个容器顶部有语义颜色标记小球
   - 后续拿到 Tripo token 后，可把 wireframe 框替换为真实 AI 生成的 3D 容器模型

3. **手势交互真正可用**
   - 修复 Emulator 上 `HAND_TRACKING` 权限问题
   - 让 HUD 按钮支持手部 hover + 捏合点击
   - 清洁/整理模式下也能用手势“抓取/确认”

### 5.2 下一阶段要做的

4. **Tripo 真实 3D 模型落地** 🟡 代码已升级到 v3
   - ✅ 客户端从 Tripo v2 迁移到 v3：`/generation/text-to-model` + `/tasks/{task_id}`
   - ✅ 本地文件缓存：相同 prompt 只生成一次，GLB 存 `cacheDir/tripo_models/`
   - ✅ 端点可在 `local.properties` 用 `TRIPO_API_ENDPOINT` 覆盖（默认全球 `.ai`，中国区可换 `.com`）
   - ⏳ Phase 1：在真机/可联网环境联调 1 个奖杯模型（~25 积分）
   - Phase 2：整理容器 + 家具预置模型（8-12 个模型，~200-300 积分）
   - 详细预算见 `TRIPO_TOKEN_PLAN.md`

5. **视觉与反馈升级** 🟡 部分完成
   - ✅ 清洁成功时在被吸走的彩带位置生成彩色闪光点
   - ✅ 整理成功时目标框/物品先放大脉冲再消失
   - 🟡 完美清洁时的全屏彩带雨 + 音效已做；地面 shader / 持续轨迹待补
   - ✅ 家具模式：放置碰撞时除了变红，再加空间警告音效（状态上升沿触发，避免连爆）

6. **成就与进度系统**
   - 连续清洁天数、最快完成时间、最高分数
   - 本地成就徽章，分享截图

7. **性能压榨**
   - 帧率监控、DrawCall 控制
   - 大房间下的 mesh LOD / 平面管理
   - 多线程加载 Tripo 模型不卡主线程

8. **打包与提交**
   - 生成 release APK
   - 录制演示视频/截图
   - 补齐 README 与提交材料

## 6. 当前阻塞项

- [ ] **无真机** → 部分 SpatialML / Anchor / 手势功能只能在 Emulator 上有限验证
- [ ] **Hand Tracking 权限在 Emulator 未生效** → 日志显示 `no com.picovr.permission.HAND_TRACKING permission`，需检查 manifest 与运行时权限
- [ ] **Android SDK Platform 35 / 37 警告** → AGP 8.13.2 对 SDK 37 支持未测试，已通过 `suppressUnsupportedCompileSdk` 绕过，但后续建议升级 AGP
- [ ] **Tripo API 尚未真实调用** → v3 接口已接；当前开发环境无法直连 `openapi.tripo3d.ai`，需在真机或能访问该域名的网络下验证

## 7. 下一步（建议）

按“先让 Demo 在 Emulator 跑通，再堆高级功能”的顺序：

1. ✅ 自由整理占位模型已上线；继续视觉升级（ghost 填充 + 标签 + 悬停动画）。
2. 修复 Hand Tracking 权限，让 Emulator 上也能手势点击 HUD。
3. 整理模式接入真实物体 mesh / Tripo 容器模型原型。
4. Phase 1 联调 Tripo 真实奖杯生成（~25 积分）。
5. 加粒子/彩带雨等视觉反馈，让“游戏感”更强。
6. 最后做性能调优与提交材料。

## 8. 仓库与运行命令

```bash
# 编译
cd "/Users/ec/Desktop/飞书/house hero/house-hero"
./gradlew :app:assembleDebug

# 安装并启动（PICO Emulator 需先启动）
pico-cli app install "/Users/ec/Desktop/飞书/house hero/house-hero/app/build/outputs/apk/debug/app-debug.apk"
pico-cli app launch com.roomhero.app
```

GitHub: https://github.com/1535573626-wq/house-hero.git

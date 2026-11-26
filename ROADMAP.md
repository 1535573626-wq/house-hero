# House Hero — 项目全景与进度

> 交互备忘：切换对话后快速恢复上下文用。目标、已做、未做、下一步都在这里。

## 1. 一句话创意

**House Hero** = 把真实家务变成 MR 游戏的 PICO Spatial 应用。戴上头显后，自家地面变成满屏派对彩带，桌面变成待整理物品；打扫/整理过程被计分、限时、给成就，结束还能生成 3D 奖杯。

## 2. 我们要把 PICO 性能/能力用到什么程度

赛道：**Spatial 空间应用创作 — Android Kotlin Compose**。
官方给的 SDK 能力几乎都要覆盖：

| SDK 能力 | 计划用法 | 状态 |
|---|---|---|
| **Spatial Mesh / Plane Tracking** | 扫描真实地面、桌面，锚定彩带/物品/家具 | ✅ 已用 |
| **HMD Tracking** | 头部前方固定光标（吸尘器/抓取点） | ✅ 已用 |
| **Hand Tracking / Gesture** | 捏合右手确认：Ready 开始游戏 / Furniture 抓起时放下 / Finished 重置 | ✅ 已用 |
| **Spatial UI (WindowContainer / PicoTheme)** | 悬浮 HUD、模式选择、计时、分数、结束按钮 | ✅ 已用 |
| **Spatial Audio** | 清洁成功、放置成功、游戏结束 3D 音效 | ✅ 已用 |
| **Physics (RigidBody/Collision)** | 自由摆放家具模式：抓起、释放、重力碰撞 | ✅ 已用 |
| **Spatial ML / Sense Mesh** | 整理模式：用 MeshTrackingManager 语义 TABLE 对齐真实桌面；家具模式：真实家具（CABINET/SOFA/BED/CHAIR/TABLE）包围盒碰撞警告 | 🟡 Mesh 语义已用；SecureMR 自定义模型待补 |
| **Tripo 3D 生成** | 游戏结束后按分数生成 3D 奖杯/奖励模型 | 🟡 占位五角星已做，真实 API 待 token |
| **Spatial Anchor / Persistence** | 保存房间布局、家具位置，下次进应用恢复 | ✅ 已用 |
| **Spatial Video / Passthrough VFX** | 彩带飘落、清洁完成时的粒子/彩带雨、地面 shader 反馈 | 🟡 彩带雨粒子已做；地面 shader / 清洁轨迹待补 |
| **Animation** | 奖励奖杯旋转浮动、成功反馈缩放动画 | 🟡 基础缩放+旋转浮动已做 |

## 3. 比赛加分项与我们对齐情况

根据 PICO Space Program 评分维度（创新性、技术深度、完成度、体验、商业价值）：

| 加分项 | 当前对齐 | 备注 |
|---|---|---|
| 创新性：把无聊家务游戏化 | ✅ | 清洁+整理+摆家具三合一 |
| 深度使用 Spatial SDK | 🟡 | 核心能力+Mesh语义+Anchor+粒子已用；SecureMR 自定义模型+Video待补 |
| 手势追踪交互 | ✅ | 捏合确认已覆盖 Ready/Playing(Furniture)/Finished 阶段 |
| 真实空间理解（mesh/平面/语义） | ✅ | 地面/桌面检测 |
| 物理模拟与真实环境结合 | ✅ | Furniture 模式 |
| AI/ML 能力（SpatialML + Tripo） | 🟡 | Sense Mesh 语义已用；SecureMR 自定义模型 + Tripo token 待补 |
| 完整 UX 流程（开始→游戏→结束→奖励） | 🟡 | 流程通，但奖励是本地占位模型 |
| 性能优化 / 压榨硬件 | 🟡 | FPS/实体计数 HUD、彩带数量上限已做；真机 LOD 待测 |
| 可扩展性 / 商业潜力 | 🟡 | 思路有，需包装 |

## 4. 当前已完成的模块

- [x] 项目骨架：Kotlin + Compose + Spatial SDK 6.2.8
- [x] 三种游戏模式切换：清洁 / 整理 / 摆家具
- [x] RoomSurfaceManager：启动 PlaneTracking + MeshTracking，等待地面/桌面/真实家具 mesh，fallback 虚拟表面
- [x] DirtManager：在地面上生成彩色派对彩带，HMD 光标靠近清除
- [x] OrganizeManager：桌面生成物品+虚线目标框，优先对齐真实 TABLE mesh，模拟 SpatialML 物品标签
- [x] FurnitureManager：物理世界 + 可抓取家具 + 重力释放碰撞 + 真实家具 mesh 碰撞警告 + 布局持久化
- [x] FurnitureAnchorManager：SharedPreferences + WorldTrackingManager 空间锚点保存/恢复家具布局
- [x] VacuumManager：HMD 前方光标，跟随头部移动
- [x] FeedbackManager：空间音效 + 缩放消失动画 + 游戏结束彩带雨粒子
- [x] PerformanceManager：FPS 与活跃实体数统计，HUD 实时显示
- [x] DirtManager：派对彩带 + 超大房间自动限制数量（MAX 200）
- [x] GameStateManager：阶段机（Scanning / Ready / Playing / Finished）+ 计分/倒计时
- [x] GameHUDWindow：SpatialUI 悬浮面板，显示模式选择、计时、分数、进度
- [x] RewardManager：游戏结束后按分数生成 1~3 个旋转浮动的 3D 奖杯（本地五角星占位）
- [x] TripoClient 占位：无 token 时返回程序化五角星 mesh

## 5. 还没做但计划做（优先级排序）

1. **Hand Tracking 手势交互**
   - 用 pinch/air-tap 替代按钮点击，做家务时手空不出来握手柄
   - 面板按钮支持手势 hover + 捏合确认

2. **SpatialML / Sense Mesh 真正落地** ✅ 基础版已完成
   - 整理模式：识别桌上物品（书、充电器、杯子等），自动推荐归类区域
   - 家具模式：识别真实家具轮廓，给出“能否放这里”的碰撞/空间建议
   - 可选：用 SecureMR 做实时物体检测或平面分割

3. **Tripo 3D 真实奖励模型**
   - 拿到 token 后把 `TripoClient.generate()` 改为真实 HTTP 调用
   - 根据分数等级请求不同 prompt（金/银/铜奖杯、皇冠、彩带堆）

4. **Spatial Anchor + 持久化**
   - 保存用户房间的最佳家具布局
   - 下次启动恢复上次的整理/摆放状态

5. **视觉与反馈升级**
   - 清洁时彩带被吸走的粒子轨迹
   - 完美清洁时的彩带雨/全屏反馈
   - 整理成功时目标框发光动画
   -  furniture 放下时的落地冲击波/音效

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

- [ ] **Tripo token 未分发** → 奖励模型只能用占位五角星
- [ ] **无真机** → 部分 SpatialML / Anchor / 手势功能只能在 Emulator 上有限验证
- [ ] **Android SDK Platform 35 / 37 警告** → AGP 8.13.2 对 SDK 37 支持未测试，已通过 `suppressUnsupportedCompileSdk` 绕过，但后续建议升级 AGP

## 7. 下一步（建议）

按“先让 Demo 在 Emulator 跑通，再堆高级功能”的顺序：

1. 把当前 APK 安装到 PICO Emulator，确认三模式都能跑完并看到奖励按钮。
2. 加手势追踪：HUD 按钮支持手捏合点击。
3. 接入 SpatialML：先做一个最简单的“桌面物品分类推荐”原型。
4. 拿到 Tripo token 后替换真实 3D 奖励生成。
5. 加粒子/彩带雨等视觉反馈，让“游戏感”更强。
6. 最后做性能调优与提交材料。

## 8. 仓库与运行命令

```bash
# 编译
cd "/Users/ec/Desktop/飞书/house hero/house-hero"
./gradlew :app:assembleDebug

# 安装并启动（PICO Emulator 需先启动）
pico-cli app install
pico-cli app launch
```

GitHub: https://github.com/1535573626-wq/house-hero.git

# XPlayer

面向本地高清片源的 Android 播放器，复刻 FlClash 的 Material 3 视觉语言，内置 **Media3 + libmpv 双内核**，按片源特征自动选择解码内核，并可在播放页一键热切换。

> 实现依据：`xplayer_prd.md`（XPlayer 产品需求与架构设计文档）

---

## 1. 能力概览

| 域 | 已实现 |
| --- | --- |
| 视觉 | FlClash 风格 4-Tab 药丸底栏、LargeTopAppBar 折叠、分组卡片 + ModalBottomSheet 选择器、动态取色 / 6 套种子色 / 深色·浅色·跟随系统·AMOLED 纯黑 |
| 引擎 | `PlayerEngine` 统一接口，Media3（ExoPlayer + FFmpeg 音频扩展）为主、libmpv 为备；`EngineRouter` 智能路由；播放页药丸按钮热切换；解码失败自动回退 mpv |
| 媒体库 | `MANAGE_EXTERNAL_STORAGE`（Android 11+）/ `READ_EXTERNAL_STORAGE`（Android 10）引导，全盘增量扫描、元数据与封面抽取、4K / DV / HDR / HEVC / ASS 徽章、搜索 + 排序 + 网格/列表切换 |
| 手势 | 亮度·音量竖向滑动、横向滑动快进、双击分区（左/中/右可配）、长按倍速并拖动调节、双指缩放、边缘防误触、锁屏 |
| 字幕 | 内封/外挂轨道切换、外挂字幕导入、±100ms/±500ms 时间轴微调与归零、字号/颜色/描边/阴影/背景条/底部边距自定义、ASS 特效保护开关 |
| 进阶 | 当前帧截图（PixelCopy / mpv screenshot-raw）、MediaSessionService 后台纯音频、画中画（手动 + 按 Home 自动进）、3 秒断点续播与 95% 看完判定、自动连播 |
| 极客 | `mpv.conf` 内置编辑器 + 预设片段、GLSL 着色器导入/启用/排序/总开关、`MediaCodecList` 硬件解码体检面板 |
| 网络 | 首页直链卡片（支持自定义 HTTP Headers）、历史记录管理 |

## 2. 技术栈

- Kotlin 2.3.21 + Jetpack Compose（BOM 2026.09.00）+ Material 3，单 `app` 模块，`com.xplayer.app`
- minSdk 29 / targetSdk 36 / compileSdk 37（Coil 3.6.3 的下限要求）
- AGP 9.5.2 + Gradle 9.9.2（AGP 9 内置 Kotlin 支持，无需再应用 `org.jetbrains.kotlin.android`）
- Media3 1.9.0（exoplayer / hls / dash / ui / session）+ `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1`（**版本严格对齐 1.9**）
- libmpv 预编译 `io.github.abdallahmehiz:mpv-android-lib-android:0.2.2`（无 NDK、无源码编译）
- Room 2.8.5（KSP）、Preferences DataStore 1.3.0、Coil 3.6.3，手写 MVVM + 手工 DI（`AppContainer`）
- 图标全部手写 `ImageVector`，**不引入** `material-icons-extended`（否则 dex 膨胀到 120MB+）

## 3. 目录结构

```
app/src/main/java/com/xplayer/app/
├── data/
│   ├── datastore/      # Prefs 枚举与记录、SettingsRepository（DataStore 映射）
│   ├── local/          # Room 实体 / DAO / Database
│   ├── repository/     # 视频、进度、屏蔽目录、直链、着色器
│   ├── scanner/        # 全盘扫描、元数据与封面、轨道探测、外挂字幕发现
│   └── source/         # MediaSource 抽象（本地文件 / 网络直链，为 V2 预留）
├── player/
│   ├── engine/         # PlayerEngine 接口、Media3EngineImpl、MpvEngineImpl、EngineRouter
│   ├── pip/            # PiP 控制器、RemoteAction 广播接收器
│   ├── service/        # MediaSessionService 后台音频
│   ├── codec/          # MediaCodecList 硬解体检
│   ├── PlayerController.kt
│   └── ScreenshotSaver.kt
├── ui/
│   ├── navigation/     # Routes、FlClash 风格底栏
│   ├── home / videos / folders / settings / player / components / icons / theme / permission
│   └── ...
├── MainActivity.kt     # NavHost 宿主、权限流程、自动扫描、PiP 生命周期回调
└── XPlayerApp.kt       # Application + AppContainer
```

## 4. 构建

### 4.1 GitHub Actions（推荐，零本地编译）

仓库：<https://github.com/wangmw3617/xplayer-wb-ds>（私有）

推送后由 `.github/workflows/android-ci.yml` 自动执行 `compileDebugKotlin lintDebug` → `assembleDebug`，按 ABI 分包上传（保留 14 天）；推送 `v*` 标签会额外签名打包并创建 GitHub Release，**发布页列出每个架构各自的安装包**。

| 文件 | 适用设备 | release 体积 |
| --- | --- | --- |
| `XPlayer-arm64-v8a-release.apk` | 绝大多数手机/平板（2017 年后），首选 | ~36MB |
| `XPlayer-armeabi-v7a-release.apk` | 老设备、32 位 ROM | ~35MB |
| `XPlayer-x86_64-release.apk` | 模拟器（64 位） | ~37MB |
| `XPlayer-x86-release.apk` | 模拟器（32 位） | ~37MB |
| `XPlayer-universal-release.apk` | 不确定架构时的兜底包，四套 .so 全含 | ~94MB |

每个构建类型都会出这五个包（`*-debug.apk` 为调试包，体积略大）。签名：release 用正式证书 `CN=XPlayer`，未配 Secrets 时回退 debug 证书。

判断机型架构：`adb shell getprop ro.product.cpu.abi`（常见返回 `arm64-v8a`）。

下载方式：

```bash
# 下载整批 release 包（四个架构 + universal）
gh release download v1.2.0 --repo wangmw3617/xplayer-wb-ds -D ./apk

# 只下某一个架构
gh release download v1.2.0 --repo wangmw3617/xplayer-wb-ds -p "XPlayer-arm64-v8a-release.apk" -D ./apk

# 或取某次 CI 的 artifact（同样含全部架构）
gh run download -n XPlayer-release-apks -D ./apk --repo wangmw3617/xplayer-wb-ds

# 手动触发一次带 release 包的构建
gh workflow run "Android CI" --repo wangmw3617/xplayer-wb-ds -f build_release=true
```

### 4.2 本地构建（可选）

```bash
# local.properties 写入 sdk.dir=/path/to/Android/sdk
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/
#   app-arm64-v8a-release.apk      ~36MB
#   app-armeabi-v7a-release.apk    ~35MB
#   app-x86-release.apk            ~37MB
#   app-x86_64-release.apk         ~37MB
#   app-universal-release.apk      ~94MB
```

`app/build.gradle.kts` 的 `splits.abi` 打开并列出四个 ABI，`isUniversalApk = true` 额外产出兜底包。若只想要单包，把 `isEnable` 改回 `false`、`isUniversalApk` 保持 `true` 即可。

### 4.3 Release 签名（可选）

配置 GitHub Secrets 后，`v*` 标签构建自动签名：

| Secret | 说明 |
| --- | --- |
| `XPLAYER_KEYSTORE_BASE64` | keystore 的 base64 |
| `XPLAYER_KEYSTORE_PASSWORD` | keystore 口令 |
| `XPLAYER_KEY_ALIAS` | 别名 |
| `XPLAYER_KEY_PASSWORD` | 别名口令 |

未配置时 Release 回退到 debug 签名，仅用于内测安装。

## 5. 双内核路由规则

`EngineRouter` 的判定顺序（Media3 优先）：

1. 用户手动切过内核 → 沿用上次选择（记录在 `playback_history.preferred_engine`）
2. 全局策略为「始终 mpv」→ mpv
3. 启用 GLSL 着色器或填写了自定义 `mpv.conf` → mpv
4. 存在内嵌 ASS/SSA 字幕且开关打开 → mpv（保证特效排版）
5. 其余 → Media3；Media3 解码异常时由 `PlayerController` 自动回退 mpv 并提示

## 6. 权限说明

- Android 11+ 申请「所有文件访问权限」，用于遍历任意目录（含外置存储根与隐藏目录）
- Android 10 使用 `READ_EXTERNAL_STORAGE`
- 拒绝授权时首页展示降级提示，可一键重新授权；库为空时给出引导卡片

## 7. 已知限制

- APK 体积：按架构分包后单包 release 约 35–37MB（arm64 36MB / v7a 35MB / x86 37MB / x86_64 37MB），universal 兜底包 94MB，debug 对应 41–106MB。`.so` 与 dex 均压缩存放。单包构成：原生库约 40–53MB 原始 → 17–20MB（libmpv + libavcodec + libavfilter + libc++_shared + libffmpegJNI），dex 33.4MB 原始 → 12.0MB（未开 R8），mpv OSD 字体 `subfont.ttf` 6.4MB → 2.9MB。双内核意味着 FFmpeg 存在两份（libmpv 自带一套 + Media3 的 `libffmpegJNI.so`），这是体积的主要来源，也是功能必需。
- 进一步压缩的可选项：开启 R8 (`isMinifyEnabled = true`) 可再省约 6MB dex，但需为 libmpv JNI、Media3 renderer、Room 补 keep 规则并做真机回归，当前为稳定性优先未启用。
- v1.0.0 用的是 debug 证书，从 v1.0.1 起换成了正式 keystore（签名指纹变化），**升级前需先卸载旧包**。
- 分包后各 ABI 包的 `versionCode` 相同（AGP 未启用 ABI 偏移），走 GitHub 分发无影响；若要上 Google Play 多 APK 分发，需要给 `splits.abi` 配 versionCode 偏移。
- 着色器「组合预设位」（自定义保存番剧超分方案）尚未实现，目前支持单条启用/排序/总开关与 `mpv.conf` 预设片段。
- 未在真机验证 HDR/DV 点亮与 Anime4K 实际渲染效果，需按 PRD 里程碑做真机验收。

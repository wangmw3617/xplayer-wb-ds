# XPlayer 项目长期约定

## 版本号策略（重要）
- **日常小修改（改 UI、修 bug、调文案等）一律不动 `versionCode` / `versionName`**，只提交推送；等用户明确说「发包」时才统一升版本。
- 升版本规则：
  - **功能更新** → `versionName` 末位 +1（1.3.0 → 1.3.1），`versionCode` +1
  - **重点修改 / 大改版** → `versionName` 第二位 +1（1.3.x → 1.4.0），`versionCode` +1
- 即：一次版本号可能对应多个提交，不要每次改动都 bump。

## 发布（重要）
- 用户说「发包」= 直接执行完整发布流程，不需要再确认：
  1. 此时才升版本号（见上）
  2. `git commit` + `git push origin main`
  3. `git tag vX.Y.Z && git push origin vX.Y.Z`
  4. `gh run watch <tag 运行 id>` 等 CI 绿，然后报告 Release 地址与各架构包体积
- 发布形态固定：ABI 分包（`armeabi-v7a / arm64-v8a / x86 / x86_64` + universal 兜底），附件由 CI 生成，**不要把包下载到本地**，用户自己去 Release 页取。
- 不开 R8（`isMinifyEnabled = false`），体积靠 `useLegacyPackaging` 压缩 .so 与 dex。
- Release 页需要有选包对照表（CI body 里已内置）。

## 视觉语言
- 整体参考 FlClash：Material 3、圆角卡片分组、主题色小标题、胶囊控件、透明渐变遮罩。
- 播放页布局参考 YouTube 安卓端**信息架构**（顶栏极简 / 中央三键 / 底栏功能条），但按钮样式必须是本软件的：圆形、主题强调色、胶囊倍速。
- 内核切换等低频项收进「播放设置」二级菜单，一级界面只留高频按钮。

## 工程约束
- AGP 9.5.2（Kotlin 内置，禁止再应用 `org.jetbrains.kotlin.android`）、Gradle 9.9.2、compileSdk 37、minSdk 29。
- 不引入 `material-icons-extended`；缺图标自己写进 `ui/icons/XPlayerIcons.kt`。
- 签名密钥：`C:\Users\wang\.xplayer-keys\xplayer-release.jks`（口令在 credentials.txt），仓库 Secrets 已配好。

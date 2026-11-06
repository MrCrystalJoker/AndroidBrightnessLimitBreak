# AndroidBrightnessLimitBreak

基于 [Shizuku](https://shizuku.rikka.app/) 的屏幕亮度控制器：从通知栏、快捷磁贴或面板滑块调节亮度，**可以调到系统亮度条达不到的更暗档位**。

> 实测设备：红魔 8 Pro（NX729J）/ RedMagicOS 10.0.12 / Android 15 / Shizuku 13.6.0（非 root）

## 功能

- **常驻通知**：`±1 / ±5 / ±10` 三组按钮，实时显示当前百分比与进度条
- **亮度面板**：0~100 对数刻度滑块，拖动实时跟手
- **QS 磁贴**：下拉快捷设置点一下直接唤起面板
- **极暗档**：背光压到硬件下限后，叠加全屏黑色遮罩（最高 87%）
- **息屏重设**：亮屏后自动重新下发亮度，不会被自动亮度顶掉
- **自动启动**：打开 App 即拉起前台服务

## 原理

两段式，和 EhViewer 的做法一致：

| 区间 | 手段 |
|---|---|
| 30 ~ 100 档 | `cmd display set-brightness`（Shizuku 的 shell 权限），背光 0.0102 ~ 1.0 |
| 0 ~ 30 档 | 背光固定 0.0102，叠加全屏黑色遮罩（alpha 0 ~ 0.87） |

背光到 0.0102 就是这块面板的物理下限（传 0.002 会被系统夹回），再往下只能靠压暗画面。

## 环境要求

- Android 12+（minSdk 31）
- 已安装并运行 [Shizuku](https://shizuku.rikka.app/)（非 root 模式即可）
- 悬浮窗权限（极暗档需要）

## 安装

1. 安装 [Releases](https://github.com/MrCrystalJoker/AndroidBrightnessLimitBreak/releases) 里的 APK
2. 启动 Shizuku
3. 打开 App → 授权 Shizuku → 授权悬浮窗
4. 服务会自动启动，通知栏出现亮度控制

## 构建

```bash
git clone https://github.com/MrCrystalJoker/AndroidBrightnessLimitBreak.git
cd AndroidBrightnessLimitBreak
./gradlew assembleDebug
```

- AGP 9.5.1（内置 Kotlin，不要再加 `org.jetbrains.kotlin.android` 插件）
- Gradle 9.8.2 / JDK 17+
- compileSdk 36 / targetSdk 35 / minSdk 31

`local.properties` 里的 `sdk.dir` 需要指向本机 Android SDK。

## 已知限制

- 遮罩无法覆盖状态栏和下拉通知栏：`TYPE_APPLICATION_OVERLAY` 的层级在系统窗口之下
- 背光下限由面板硬件决定，不同机型可能不同
- 没有开机自启，重启后需要打开一次 App
- 部分 ROM 会裁剪 `cmd display` 的子命令，`get-brightness` 在红魔上就不可用（`set-brightness` 可以）

## 目录结构

```
app/src/main/java/com/mrcrystaljoker/brightness/
├── BrightnessController.kt   # Shizuku 引擎、档位映射、遮罩透明度
├── BrightnessService.kt      # 前台服务、通知、息屏重设
├── BrightnessActionReceiver.kt
├── BrightnessTileService.kt  # QS 磁贴
├── MaskOverlay.kt            # 全屏黑色遮罩
├── PanelActivity.kt          # 亮度面板
└── MainActivity.kt           # 权限引导与状态
```

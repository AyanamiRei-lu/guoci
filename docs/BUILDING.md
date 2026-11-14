# 构建指南（本机实测）

这份文档里的每条命令都在这台机器上跑过。换机器时重点看第 1 节和第 5 节。

> 改这份文档时请用编辑器或 `write` 工具，不要用 PowerShell 的 `Get-Content | Set-Content`
> 转写：在 zh-CN 系统上它会把 UTF-8 中文读成 GBK 再写回，整篇变成乱码（本项目真踩过）。

---

## 0. 环境现状（本机）

| 组件 | 位置 / 版本 | 备注 |
|---|---|---|
| JDK | Temurin **25.0.4.1**（`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`） | 不设 `JAVA_HOME`，由 Gradle toolchain 解析 |
| Android SDK | **`D:\asdk`**（非标准路径） | platform 37、build-tools 37.0.0 |
| Gradle | 由 Wrapper 提供 **9.5.0** | 分发包已在 `D:\gradle-home\wrapper\dists` |
| Gradle 依赖缓存 | **`D:\gradle-home`** | C 盘只剩约 9 GB，必须重定向 |
| AGP / Kotlin | AGP **9.3.0** / Kotlin **2.4.0** | 见第 5 节的三个坑 |
| 真机 | 构建时未连接 | `adb devices` 为空 |

`local.properties` 里写的是 `sdk.dir=D\:\\asdk`，换机器时改这一行即可。

---

## 1. 每次构建前：重定向 Gradle home

**必须**先设环境变量，否则依赖会被写进 C 盘（约 1.5 GB）：

```powershell
$env:GRADLE_USER_HOME = "D:\gradle-home"
```

想永久生效（只在你这台机器上）：

```powershell
Add-Content $PROFILE '$env:GRADLE_USER_HOME = "D:\gradle-home"'
```

---

## 2. 词库校验（改词库后必做）

```powershell
cd D:\Administrator\Documents\project\project2\guoci-android
python tools\validate-words.py app\src\main\assets\words.json
```

要求 3500 条、id/rank 连续唯一、`word` 唯一、`meaning` 非空且不含词典噪声、
`phonetic` 只含 IPA 字符集。失败会以非 0 退出并列出前 30 条问题。

重新生成词库（需要 ECDICT 的 sqlite，见 `docs/DATA-SOURCE.md`）：

```powershell
python tools\build-words.py --db <cdict2w.db 的路径> --out app\src\main\assets\words.json
```

---

## 3. 测试

```powershell
.\gradlew.bat :core:test
```

`:core` 是纯 Kotlin/JVM 模块，**不需要手机**。57 个测试覆盖这个 App 里唯一会算错的那些规则：

| 测试 | 守着什么 |
|---|---|
| `SchedulerTest` | 间隔表推进/降档、队列筛选与排序、每日上限、缺勤封顶 |
| `SessionTest` | 当天会话状态机：学→测→完成、只重考错词、必须全对才完成、排期只做一次 |
| `GraderTest` | 拼写判分（大小写/空格/连字符/引号/标点）、提示、逐字母差异 |
| `StreakTest` | 今天没做完时不显示 0、断签不清最长记录、徽章阈值、下一个目标 |
| `ExporterTest` | CSV 转义与 BOM、TXT 格式、空音标不写斜杠 |
| `SheetPlannerTest` | 默写单选词/数量/题型、seed 可复现的乱序、答案对齐 |
| `LongRunSimulationTest` | 把一年压缩成几毫秒：3500 词 430 天内全部毕业、峰值卡片数、缺勤回归不爆炸 |

只看失败：

```powershell
.\gradlew.bat :core:test --console=plain 2>&1 | Select-String "FAILED"
```

---

## 4. 打包与安装

```powershell
.\gradlew.bat :app:assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk  （约 11.5 MB）
```

装到手机（手机需开 USB 调试）：

```powershell
& D:\g\platform-tools-latest-windows\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
& D:\g\platform-tools-latest-windows\platform-tools\adb.exe shell am start -n com.guoci.vocab3500/.MainActivity
```

没有数据线时：把 APK 拷到手机任一目录，用文件管理器点开安装（需要允许"安装未知应用"）。
这是 **debug 签名**，只适合自用；要分发需要自己生成 keystore 并配置 `signingConfigs`。

检查包里的权限（应只有通知与开机广播，**没有 INTERNET**）：

```powershell
& D:\asdk\build-tools\37.0.0\aapt2.exe dump permissions app\build\outputs\apk\debug\app-debug.apk
```

---

## 5. 三个已经踩过的坑（改构建时别踩回去）

### 5.1 `:core` 的 Kotlin 插件必须钉版本

`:core` 应用 `org.jetbrains.kotlin.jvm` 时**不写版本**，在独立工程里会直接失败：

```
Plugin [id: 'org.jetbrains.kotlin.jvm'] was not found ...
Plugin Repositories (plugin dependency must include a version number for this source)
```

原因：AGP 9 确实把 Kotlin 插件带进了构建 classpath，但要等 `:app` 的 plugins 块解析完；
Gradle 解析 `:core` 的 plugins 块发生在更早。解决办法是在 `settings.gradle.kts` 的
**`pluginManagement { plugins { id("org.jetbrains.kotlin.jvm") version "2.4.0" } }`** 里
声明一次默认版本。**不能**写进 settings 顶层的 `plugins {}` —— 那是把插件应用到 Settings
对象，会报 `Unexpected plugin type`。

副作用：构建时会打印一条警告

```
The Kotlin Gradle plugin was loaded multiple times in different subprojects ...
```

这是"两个模块各加载一次插件实例"的提示，实测不影响编译与测试；如果将来升级 AGP/Kotlin
后它变成报错，做法是去掉 `:core` 模块、把逻辑放进 `:app` 的 `src/test`。

### 5.2 AGP 9 换了两个 DSL 写法

```kotlin
compileSdk { version = release(37) }        // 不是 compileSdk = 37
buildTypes { release { optimization { enable = false } } }   // 不是 isMinifyEnabled
```

另外**不要**应用 `org.jetbrains.kotlin.android`（AGP 9 内置 Kotlin，应用它会直接失败），
但 Compose 编译器插件 `org.jetbrains.kotlin.plugin.compose` **必须**应用。

### 5.3 Maven 直连会被证书拦截

本机访问 `repo.maven.apache.org` 会拿到不匹配的证书（DNS 拦截），首次构建会卡十一分钟。
`settings.gradle.kts` 因此把阿里云镜像放在最前面，google/mavenCentral 兜底。
镜像是一个"信它"的选择：如果不想用，删掉两个 `maven("https://maven.aliyun.com/...")`
块里的三行即可。

---

## 6. 目录结构

```
guoci-android/
├─ core/                          纯 Kotlin/JVM，可单测的全部规则
│  ├─ Model.kt                    Word / WordProgress / Progress / 设置
│  ├─ Scheduler.kt                1/2/4/7/15/30 间隔表、队列构造
│  ├─ Session.kt                  当天会话状态机：学→测→完成、重考、排期只做一次
│  ├─ Grader.kt                   拼写判分、提示、逐字母差异
│  ├─ Streak.kt                   连续天数、徽章、下一个目标
│  ├─ Exporter.kt                 CSV/TXT 导出 + 默写单排版
│  └─ src/test/kotlin/            57 个 JVM 测试
├─ app/
│  ├─ src/main/assets/words.json  3500 条词库（只读，456 KB）
│  ├─ src/main/java/com/guoci/vocab3500/
│  │  ├─ MainActivity.kt          单 Activity + 界面状态机
│  │  ├─ data/                    WordRepo / Store / AppState
│  │  ├─ platform/                Speaker / SheetPdf / ShareFiles / Reminder
│  │  └─ ui/                      六个屏 + 主题 + 弹窗
├─ tools/build-words.py           从 ECDICT 生成词库
├─ tools/validate-words.py        词库结构校验
└─ docs/                          本文 + 数据来源
```

---

## 7. 常见问题

**Q: 构建报 `SDK location not found`？**
改 `local.properties` 的 `sdk.dir`，注意 Windows 路径要写成 `D\:\\asdk`。

**Q: 手机是 Android 7 能装吗？**
不能，`minSdk = 26`（Android 8.0）。再往下需要为 `java.time` 做 desugaring，这个 App
的所有日期逻辑都建在它上面，不值得为一个几乎不存在的机型加一层。

**Q: 为什么 APK 有 11 MB？**
debug 包没有 R8 裁剪，dex 未压缩。真正的体积问题（词库 456 KB）反而不大。

**Q: 每天真的只有 5~8 分钟吗？**
典型日子 30 张卡左右（10 个新词 + 约 20 张复习）。但六个间隔档会周期性撞在同一天，
理论上最坏一天约 70 张（约 14 分钟），大约每 30 天遇到一次 —— 这是固定间隔表换来的
可预测性，`LongRunSimulationTest` 把这条上界写成了断言。嫌重就在设置里把每日新词调到 5。

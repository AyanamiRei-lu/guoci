# 过词 · 高中 3500

[![CI](https://github.com/AyanamiRei-lu/guoci/actions/workflows/ci.yml/badge.svg)](https://github.com/AyanamiRei-lu/guoci/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

一个**只干一件事**的安卓背单词 App：每天 10 个新词 + 到期复习词，学完立刻考拼写，
**全对才算今天完成**。错的词明天还会回来找你；不会的词可以导出去；随时能生成一张能打印的
默写单。

它的两个假想敌是你说的那两句话：**"背了就忘"** 和 **"坚持不下来"**。

---

## 每天打开它会看到什么

```
11 月 14 日 · 周五                                    设置

                      今天
              10 个新词 · 21 个复习
                  约 6 分钟
                  连续 3 天

          ┌──────────────────────────┐
          │   开始今天的 31 个        │
          └──────────────────────────┘

              词库 · 错词本 · 默写单
```

首页**只有**这一个按钮。没有底部导航，没有"今日推荐"，没有签到领金币，没有排行榜。

流程是一条直线：**学**（卡片翻面）→ **测**（中译英拼写，全对才能过）→ **完成页**
（一个大勾、连续天数、下一个目标）→ 被放走。没有"再学一组"。

## 为什么这样设计

### "背了就忘" → 间隔表 + 客观门禁

- 每个词按 **1 → 2 → 4 → 7 → 15 → 30 天** 排期，到期自动混进今天这一批。
- **答错只降一档，不清零**；而且明天一定再考你一次。清零会制造"这个词永远学不完"
  的绝望感，这是背单词 App 最常见的劝退点。
- 连对 6 次（第 30 天）毕业，不再打扰你。
- **判定的唯一依据是拼写**。自评"我记住了"不算数 —— 那正是"背了就忘"的来源：
  感觉记住了，实际写不出来。只有当天队列 100% 拼对，完成页才会出现；答错的词在同一轮
  末尾重考，直到全对。

### "坚持不下来" → 让任务小到无法拒绝

- 每天固定 10 个新词（可调 5/10/15/20），稳态下一天约 30 张卡、5~8 分钟。
  六个间隔档偶尔会撞在同一天，最坏约 70 张（约 14 分钟），大约每 30 天一次。
- **断签不惩罚**：不弹"你失败了"，当前天数从头数，最长记录永远留着。
- 只有一个最近的目标，比如"再 3 天 → 连续 7 天"，而不是同时甩十个数字。
- 缺勤回归保护：到期复习词超过 120 个时**自动不加新词**，先在首页说明"复习积压了 N 个"，
  而不是一次性甩 300 张卡把人劝退。
- 每日提醒默认关闭，打开后一天只发一条，文案是"两分钟，10 个词，做完就结束"。

## 能拿出去的东西

- **错词本**：所有答错过的词，按错误次数排序。可导出 `TXT`、`CSV`（UTF-8 带 BOM，
  Excel 打开不乱码），或复制到剪贴板，走系统分享面板发给微信/QQ/文件。
- **默写单 PDF**：A4，可选「中写英 / 英写中」、10/20/30/50 题、是否打乱、是否给首字母提示、
  是否附答案页。页眉有日期、范围、姓名栏、得分栏，行距按高中生的手写字留够。
  生成后直接分享给打印机或存文件。
- **进度备份**：一个 JSON，换手机时留着。

## 技术

- 原生 Android，Kotlin + Jetpack Compose，单 Activity，**零第三方运行时依赖**
  （PDF 用系统 `PdfDocument`，存储用手写 JSON，提醒用 `AlarmManager`，动画手写）。
- `:core` 是纯 Kotlin/JVM 模块，装着全部会算错的规则：间隔表、拼写判分、连续天数、
  导出、默写单排版，以及当天会话的完整状态机。**57 个 JVM 测试**，不需要手机。
  其中 `LongRunSimulationTest` 把一年压缩成几毫秒：3500 词在 430 天内必须全部毕业、
  单日峰值卡数不得超过理论上界、缺勤 90 天回归时队列必须被封顶。
- 词库 3500 条随包，来自 ECDICT（MIT），见 [docs/DATA-SOURCE.md](docs/DATA-SOURCE.md)。
  学习顺序**不是纯词频**：先学名词、动词、形容词和较复杂的介词（per/upon/beyond 等），
  `the/a/and` 这类小学虚词与 `say/go/get` 这类初中词排在最后 —— 否则"高中 3500"的第一周
  会变成小学词表。前 10 个是：development, report, role, better, effort, decide, rate,
  strong, possible, heart。
- 构建、安装、踩过的坑见 [docs/BUILDING.md](docs/BUILDING.md)。

## 隐私

- **没有 INTERNET 权限**：这个 App 连不出去，词库随包，进度只在手机上。
- 只要两个权限：通知（每日提醒）和开机广播（重启后把提醒重新挂上）。
- 没有账号、没有统计、没有广告、没有内购。

## 已知限制

- `minSdk 26`（Android 8.0）。
- 旋转屏幕会让当天的会话从头开始（已排期的进度不丢，只是要重新过一遍剩下的词）；
  日常竖屏使用不会遇到。
- 词库没有例句（源数据不提供，也不编造）；有音标的词条可以点"听读音"，用系统 TTS。
- 拼写测验要求一次性打对；同一轮里可以重考，但答错会如实记进错词本 —— 这是有意的。
- debug 签名，适合自用；分发需要自己生成 keystore。
- 词库只覆盖"高考 gk 标记"的 3500 词，学习顺序是加权词频，不是课本分册顺序。
- 最常用的 250 个实词（say/go/get/make/time/people 等）被放在中后段；如果想让开头更简单，
  改 `tools/build-words.py` 里的 `BASIC_CONTENT_LAST` 重新生成即可。

## 自己构建

```powershell
$env:GRADLE_USER_HOME = "D:\gradle-home"   # 本机约定：依赖不放 C 盘
.\gradlew.bat :core:test                    # 57 个纯 JVM 测试，不需要手机
.\gradlew.bat :app:assembleDebug            # 产物 app/build/outputs/apk/debug/app-debug.apk
```

坑（AGP 9 的 DSL 变化、`:core` 的 Kotlin 插件版本、Maven 直连被拦截）都写在
[docs/BUILDING.md](docs/BUILDING.md) 里，照着做能一次过。

CI（`.github/workflows/ci.yml`）只跑不需要 Android SDK 的两件事：词库校验 + `:core` 单测。
实测在没有 SDK、没有 `local.properties` 的机器上这两步都能完成。

## 改进方向

- 词库：换成本校教材的分册/单元顺序（现在只有 gk 标记 + 加权词频）。
- 例句：ECDICT 不提供例句，如果接入一个允许再分发的例句库，可以补上。
- 听写：现在是"看中文拼英文"，可以加纯听音拼写。
- 同步：目前刻意没有账号和云同步。

## 许可

- **代码**：MIT，见 [LICENSE](LICENSE)。
- **词库**：ECDICT（MIT）的衍生作品，取数、清洗、重排的规则写在
  [docs/DATA-SOURCE.md](docs/DATA-SOURCE.md)；署名与许可原文见
  [NOTICE.md](NOTICE.md) 和 [licenses/ECDICT-LICENSE.txt](licenses/ECDICT-LICENSE.txt)。
- 再分发时请同时保留 `LICENSE`、`NOTICE.md` 与 `licenses/ECDICT-LICENSE.txt`。


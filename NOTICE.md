# 第三方数据与许可

本仓库的**代码**以 MIT 许可发布，见根目录的 `LICENSE`。

随包分发的**词库数据**（`app/src/main/assets/words.json`）来自第三方，许可见下。

---

## ECDICT（词库来源）

- 上游：https://github.com/skywind3000/ECDICT
- 许可：MIT（原文见 [`licenses/ECDICT-LICENSE.txt`](licenses/ECDICT-LICENSE.txt)，
  `Copyright (c) 2025 Linwei`）
- 取数路径：npm 包 `cdict_query@1.0.0` 内的 `db/cdict2w.db`（数据抽取自 ECDICT），
  其 sha256 记录在 `words.json` 的 `sourceSha256` 字段里。

### 我们改了什么

不是原样复制，而是按可复现的规则重新生成了 3500 条（脚本：`tools/build-words.py`，
规则逐条写在 [`docs/DATA-SOURCE.md`](docs/DATA-SOURCE.md)）：

- 只取 `tag` 含 `gk`（高考）的词条；
- 丢弃释义里的词典噪声行（`[计]` `[医]` 等）；
- 音标归一（`ә→ə`、`є→ɛ`、`^→g`、`'→ˈ`、`:` → `ː`）、多读音只留第一种；
- `a.` / `ad.` 归一成 `adj.` / `adv.`；
- 按"词频名次 + 词性偏移量"重新排列学习顺序，并额外维护 `id`（字母序）与 `rank`
  （学习顺序）两个字段。

因此 `words.json` 是 ECDICT 的**衍生作品**，同样受 MIT 许可约束；再分发时请保留
`licenses/ECDICT-LICENSE.txt` 与本文件的署名。

---

## 依赖

运行时**没有**任何第三方库：界面用 Jetpack Compose（AndroidX，Apache-2.0），PDF 用系统
`PdfDocument`，存储与通知用系统 API。构建期依赖见 `gradle/libs.versions.toml`，
它们不随 APK 分发。

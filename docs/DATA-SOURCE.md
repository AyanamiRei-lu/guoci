# 词库来源与清洗规则

## 结论

`app/src/main/assets/words.json` 里的 3500 条词，来自 **ECDICT**（MIT 许可）里带
`gk`（高考）标记的词条。清洗后剩 3649 条候选，按词频取前 3500 条。

- 词条字段：`id / rank / word / phonetic / pos / meaning`
- `id` 按字母序固定，`rank` 是学习顺序（见下一节）
- 无音标：168 条（4.8%），界面会隐藏音标而不是显示空括号
- 无例句：ECDICT 不提供例句，所以**没有**这个字段 —— 不编造

**少了的 149 条是什么**：90 条没有词频数据（`frq=0`，多为 `Africa / April / America`
这类专有名词和 `colour / centre` 这类英式拼写），它们在按词频截断时排在最后；另外 59 条是
词频最低的（`favourite / typhoon / mutton` 这一档之后）。如果你更希望保留专有名词，改
`build-words.py` 里选词那一步的排序即可。

## 数据从哪来（本机网络受限，这点很关键）

`raw.githubusercontent.com` 在本机网络上超时不可用（ECDICT 的 GitHub 仓库因此拿不到），
可用的是 npm 镜像。实际取数路径：

```
npm 包 cdict_query@1.0.0
  └─ db/cdict2w.db     （10 MB sqlite，56,992 条，字段：word/phonetic/translation/tag/bnc/frq/exchange）
```

下载与解包：

```powershell
$dir = "D:\Administrator\Documents\project\project2\_dataset"
Invoke-WebRequest -Uri "https://registry.npmmirror.com/cdict_query/-/cdict_query-1.0.0.tgz" -OutFile "$dir\cdict.tgz"
tar -xzf "$dir\cdict.tgz" -C "$dir\cdict"
# 之后 db 在 $dir\cdict\package\db\cdict2w.db
```

`cdict2w.db` 的 sha256（生成词库时写进了 words.json 的 `sourceSha256`）：

```
ffecc4c6c1902c106135e6d39c7e160eba993178b20e59fb9e5b7bcdd5cc6071
```

> 注意：`_dataset` 目录只是取数时的临时目录，不随项目分发。重新生成词库时需要按上面
> 三行命令重新下载。

## 为什么不用网上流传的"高考 3500 词表"

那些大多是 Word 文档：缺音标、释义里混着例句和用法、一个词条跨两三行、同词多条重复。
既无法校验也无法署名。ECDICT 是结构化的、带 tag 的、MIT 许可的词典，能写出可复核的
取数规则，也能说清楚"这 3500 个词是按什么标准选出来的"。

## 清洗规则（`tools/build-words.py`，逐条可读）

| 步骤 | 规则 | 原因 |
|---|---|---|
| 取词 | `where ' '||tag||' ' like '% gk %'` | 只要高考标记的词 |
| 丢噪声释义行 | 以 `[计]` `[医]` `[化]` 等开头的行整行丢弃 | 否则学生会去背"累加器""总线允许"（原数据里真有） |
| 去括号注释 | 删掉 `[计]...` / `【…】` 片段 | 同上 |
| 合并多义 | 最多保留前两段，用「；」连接 | 太长的释义在卡片上读不完 |
| 中文标点 | `, ` → `，` | 中文释义里的半角逗号很刺眼 |
| 词性 | 把开头的 `v.` / `n.` / `adj.` 等抽到 `pos` 字段 | 卡片上单独一行显示，更接近词典排版 |
| 音标归一 | `ә→ə`、`є→ɛ`、`:` → `ː`、`^→g`、`'→ˈ`、`"→ˌ`、`(r)→r` | ECDICT 里有历史字符，直接显示会是怪字符 |
| 多读音 | 只留第一种（按 `.` `,` `;` 切分） | 一个词只配一条音标 |
| 去重排序 | 按词频（`frq`，其次 `bnc`）升序，再按字母序 | 先背高频词；同一份数据每次生成结果一致 |
| 取前 3500 | `unique[:3500]` | 与"3500 词"的承诺一致，另外 149 词更生僻 |

## 学习顺序（v1.1 起不再按纯词频）

第一版按词频排序，结果第一天背到 `the / be / and / of / a` —— 对高中生没有难度，还让人以为
这个 App 在拿小学词表糊弄人。现在用「词频名次 + 偏移量」排：

| 类别 | 偏移 | 理由 |
|---|---|---|
| 名词 / 动词 / 形容词 | 0 | 真正要背的东西，开头全是它们 |
| 复杂介词（per/upon/beyond/beneath/beside/besides/via/plus） | −100 | 词频上排在中后段，但难度不低，提前一点 |
| 副词 | +1200 | 不跟名词抢开头 |
| 专有名词（首字母大写） | +1200 | `American/April/Africa` 不该是第一个词 |
| 数词 | +2000 | two/three/four 小学就会 |
| 最常用的 250 个实词 | +2500 | say/go/get/make/time/people，初中已掌握（`BASIC_CONTENT_LAST`） |
| 代词 / 冠词 / 连词 / 助动词 / 无词性 | +2500 | |
| 小学虚词（约 144 个） | +9000 | the/a/and/in/he/be…，仍在 3500 词里，只是排到最后 |

为什么用加法而不是分档：分档会留下空档，空档会被恰好没被规则命中的词填满 —— 第一版分档
时开头是 `two, first, three, something`。加法让所有词落在同一条线上。

排序结果（可复核）：

```
前 10 个：development, report, role, better, effort, decide, rate, strong, possible, heart
复杂介词位置：per:96, upon:114, beyond:151, beneath:987, beside:990, plus:1036, via:1316, besides:1745
分档：实义词 2914, 基础高频实词 249, 小学虚词 144, 副词 81, 专有名词 41, 数词 38, 复杂介词 8
```

**`id` 与 `rank` 是两件事**：`id` 按字母序固定（重排学习顺序时，用户已存的进度不会错位到
别的词上），`rank` 才是学习顺序。数组本身按 `rank` 升序，所以 `rank == 数组序号`。

`tools/validate-words.py` 把这件事钉成了断言：**前 40 个必须是名词/动词/形容词**，
否则校验失败。下次谁再把排序改回去，构建前就会红。

## 校验（`tools/validate-words.py`）

```
count=3500 无音标=168 源=ECDICT (MIT) via npm cdict_query@1.0.0 / db/cdict2w.db
校验通过
```

检查项：条数 3500、`id`/`rank` 连续唯一、`word` 唯一且只含字母/撇号/连字符、
`meaning` 非空且不含噪声标记、`phonetic` 只含 IPA 字符集、`pos` 在允许集合内。

## 许可与署名

ECDICT 以 MIT 许可发布（https://github.com/skywind3000/ECDICT）。App 的「关于」页
也写了这一句，因为这是用户能看到的唯一署名位置。

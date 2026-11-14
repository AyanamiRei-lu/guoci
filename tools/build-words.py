#!/usr/bin/env python3
"""从 ECDICT 的高考(gk)标记词生成 assets/words.json。

为什么要写这一步而不是直接用现成词表
--------------------------------------
本项目面向"高中 3500 词"。网络上流传的 3500 词表大量是 Word 文档，字段不齐
（缺音标、释义里混着例句、一个词条跨两三行），既没法校验也没法署名。ECDICT
是 MIT 许可、带音标与中文释义、并且每条词都有 tag（zk/gk/cet4/cet6/...）的
结构化词典，其中 gk 标记共 3655 个词 —— 与"高考 3500"是对得上的。

处理规则都是可复核的：
  * 只取 tag 含 gk 的词；
  * 释义丢弃词典噪声行（以 "[计]" "[医]" "[化]" 这类开头），否则学生会去背
    "累加器""总线允许"；
  * 音标把 ECDICT 里的几个历史字符归一成标准 IPA（ә→ə、є→ɛ、:→ː）；
  * 按词频（frq，其次 bnc）排序取前 3500 个 —— 先背高频词；
  * 输出带 source/license/count 头，App 内可见来源。

用法
----
    python tools/build-words.py --db <cdict2w.db> --out app/src/main/assets/words.json

数据来源（详见 docs/DATA-SOURCE.md）：
    npm 包 cdict_query@1.0.0 内的 db/cdict2w.db，数据抽取自 ECDICT（MIT）。
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import re
import sqlite3
from pathlib import Path

LIMIT = 3500
MAX_MEANING = 60

# ECDICT 的音标里有几个不是标准 IPA 的字符，混进界面会显示成怪字符。
PHONETIC_MAP = {
    "ә": "ə",   # U+04D9 CYRILLIC SMALL LETTER SCHWA
    "є": "ɛ",   # U+0454 CYRILLIC SMALL LETTER UKRAINIAN IE
    ":": "ː",   # 半角冒号当长音符
    "ɡ": "g",   # U+0261 LATIN SMALL LETTER SCRIPT G
    "'": "ˈ",   # ECDICT 用 ASCII 撇号标重音，界面上要的是 IPA 的重音符
    '"': "ˌ",   # 同理，次要重音
    "^": "g",   # ECDICT 用 ^ 代替 g（"fəˈtɔ^rəfə" 实际是 photographer 的 /g/）
}

POS_RE = re.compile(r"^(n|v|vt|vi|adj|adv|ad|a|prep|conj|pron|art|num|int|aux|abbr|pl)\.", re.I)

# ECDICT 用 "a." 表示形容词、"ad." 表示副词，和课本里的 adj./adv. 不是一套写法。
# 不归一的话，new/great/big/important 这些形容词会因为"没有词性"而拿不到权重。
POS_NORMALIZE = {"a.": "adj.", "ad.": "adv."}

BRACKET_RE = re.compile(r"[\[【][^\]】]*[\]】]")
NOISE_LINE_RE = re.compile(r"^\s*[\[【]")

# ---------------------------------------------------------------------------
# 学习顺序的权重
#
# 原来的顺序是纯词频，结果是第一天背到 "the / be / and / of / a" —— 对高中生来说这
# 不但没有难度，还会让人以为这个 App 在拿小学词表糊弄他。这里改成"先实词、后虚词"：
#
#   * 名词、动词、形容词（以及大部分副词）按词频排在前面 —— 这是真正要背的东西；
#   * 较为复杂的介词（per/upon/beyond/beneath/beside/besides/via/plus）额外加权，
#     让它们在前面就出现，而不是拖到两千多名；
#   * 小学阶段就该会的功能词（the/a/and/in/he/be...）整体挪到最后。它们仍然在
#     3500 词里（高考词表本来也收），只是不该占掉一个高中生的第一周。
# ---------------------------------------------------------------------------

CONTENT_POS = {"n.", "v.", "vt.", "vi.", "adj."}

# 小学就该会的虚词：不算"不考"，只是不该排在最前面。
TRIVIAL = set("""
a an the
i you he she it we they me him her us them my your his its our their mine yours hers ours theirs
myself yourself himself herself itself ourselves themselves
this that these those who whom whose which what
and or but if because so than as though although while when where why how whether
be am is are was were been being do does did done have has had having
will would shall should can could may might must
not no yes
very too also just only even still already always never often sometimes usually again then now here there
all any both each every few some such other another same own much many more most less least several
of in to for with on at from by about into after before between against during without under over through
up down out off onto above below around across along among since until till toward towards near like
""".split())

# 这些介词按词频本来排在中后段，但它们并不简单，值得提前。
COMPLEX_PREPS = {"per", "upon", "beyond", "beneath", "beside", "besides", "via", "plus"}

# 最常用的这一批实词（say/go/get/make/time/year/people...）是初中就掌握的。
# 把它们也一起放到后面，否则"高中 3500"的第一周仍然是小学词表。
#
# 这是一个产品判断，不是数据结论：词频只能说明"常用"，说明不了"高中才学"。
# 250 这个数字是照着"第一周应该像高中课本"调的 —— 想让开头更简单就把它调小，
# 想更快进入难词就调大。
BASIC_CONTENT_LAST = 250

# 权重乘在词频名次上：越小越靠前。
WEIGHT_CONTENT = 1.0
WEIGHT_COMPLEX_PREP = 0.35
WEIGHT_OTHER = 1.2


def clean_meaning(raw: str) -> tuple[str, str]:
    """把 ECDICT 的多行释义压成一行，并顺手取出词性。返回 (pos, meaning)。"""
    lines = []
    for line in (raw or "").split("\n"):
        line = line.strip()
        if not line or NOISE_LINE_RE.match(line):
            continue  # 词典噪声行
        line = BRACKET_RE.sub("", line).strip()
        if line:
            lines.append(line)
    if not lines:
        return "", ""
    # 同一条释义里常有 "v. 放弃, 抛弃；n. 放任" 这种多词性，最多保留前两段。
    merged = "；".join(lines[:2])
    merged = re.sub(r"\s+", " ", merged)
    merged = merged.replace(", ", "，").replace(",", "，")
    merged = re.sub(r"；{2,}", "；", merged).strip(" ；")
    pos = ""
    m = POS_RE.match(merged)
    if m:
        pos = m.group(1).lower() + "."
        pos = POS_NORMALIZE.get(pos, pos)
        merged = merged[m.end():].strip()
    if len(merged) > MAX_MEANING:
        merged = merged[: MAX_MEANING - 1] + "…"
    return pos, merged


def clean_phonetic(raw: str) -> str:
    s = (raw or "").strip().strip("/")
    for k, v in PHONETIC_MAP.items():
        s = s.replace(k, v)
    # 有些条目把两种读音用 "." 或 "," 分开（例如 "ɒːˈgʌst. ˈɒːgəst"、
    # "ˈkɔndʌkt, -dəkt"）。界面上一个词只配一条音标，取第一种。
    s = re.split(r"[.,;]", s)[0].strip()
    if not s:
        return ""
    # "(r)" 这类可选音保留字母、去掉括号；尾部连字符/逗号是 ECDICT 的残留。
    s = s.replace("(", "").replace(")", "")
    return s.strip(" -,")


def penalty_for(item: dict, basic_band: set[str]) -> int:
    """学习顺序上的偏移量（加在词频名次上，越大越靠后）。

    为什么用加法而不是分档：分档会在档与档之间留下空档，空档会被恰好没被规则命中的
    词（数词、副词、无词性的感叹词）填满 —— 第一版就出现过"two, first, three,
    something"排在开头。加法让所有词都落在同一条线上，谁也不会因为别人的缺席被顶到前面。

    数值是相对关系，不是精密的刻度：
      实义词            0      前面全是它们
      复杂介词        -100     比同词频的实义词略靠前（per/upon/beyond/...）
      副词           +1200     不跟名词抢开头
      专有名词       +1200     American/April/Africa 这类不该是第一个词
      数词           +2000     two/three/four 小学就会
      基础高频实词   +2500     say/go/get/make/time/people（见 BASIC_CONTENT_LAST）
      代词/冠词/连词/助动词/无词性  +2500
      小学虚词       +9000     整条线的末尾
    """
    word = item["word"].lower()
    pos = item["pos"]

    if word in TRIVIAL:
        return 9000
    if word in basic_band:
        return 2500
    if word in COMPLEX_PREPS:
        return -100
    if pos == "num.":
        return 2000
    if pos in ("pron.", "art.", "conj.", "aux.", ""):
        return 2500
    if pos == "adv.":
        return 1200
    # 专有名词（首字母大写）不该抢在普通词前面，但也不该像虚词一样沉底。
    extra = 1200 if item["word"][:1].isupper() else 0
    return extra


def study_score(item: dict, basic_band: set[str]) -> tuple:
    """学习顺序的排序键：词频名次 + 偏移量，同分按字母序保证可复现。"""
    return (item["frq"] + penalty_for(item, basic_band), item["bnc"], item["word"].lower())


def label_for(item: dict, basic_band: set[str]) -> str:
    """给生成日志用的分类名（只为让人一眼看懂分档，不参与排序）。"""
    word = item["word"].lower()
    if word in TRIVIAL:
        return "小学虚词"
    if word in basic_band:
        return "基础高频实词"
    if word in COMPLEX_PREPS:
        return "复杂介词"
    if item["pos"] == "num.":
        return "数词"
    if item["pos"] in ("pron.", "art.", "conj.", "aux.", ""):
        return "代词/冠词/连词/无词性"
    if item["pos"] == "adv.":
        return "副词"
    if item["word"][:1].isupper():
        return "专有名词"
    return "实义词"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", required=True, help="cdict2w.db 的路径")
    ap.add_argument("--out", required=True, help="输出 words.json")
    ap.add_argument("--limit", type=int, default=LIMIT)
    args = ap.parse_args()

    db = Path(args.db)
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    rows = con.execute(
        "select word, phonetic, translation, tag, bnc, frq "
        "from stardict where ' '||tag||' ' like '% gk %'"
    ).fetchall()

    cleaned = []
    for word, phonetic, translation, tag, bnc, frq in rows:
        word = (word or "").strip()
        if not word or not re.fullmatch(r"[A-Za-z][A-Za-z'\-]*", word):
            continue
        pos, meaning = clean_meaning(translation)
        if not meaning:
            continue
        cleaned.append({
            "word": word,
            "phonetic": clean_phonetic(phonetic),
            "pos": pos,
            "meaning": meaning,
            "frq": frq or 10**9,
            "bnc": bnc or 10**9,
        })

    # 去重（大小写不敏感），词频优先，其次字母序 —— 保证同一份数据每次跑出同样结果。
    seen: set[str] = set()
    unique = []
    for item in sorted(cleaned, key=lambda x: (x["frq"], x["bnc"], x["word"].lower())):
        key = item["word"].lower()
        if key in seen:
            continue
        seen.add(key)
        unique.append(item)

    # 选词仍按纯词频（谁进 3500 由高频决定），顺序另算 —— 否则虚词被挪到最后就会把
    # 155 个本来不入选的生僻词顶进来。
    picked = unique[: args.limit]

    # id 与学习顺序解耦：id 按字母序分配，重排学习顺序时已存的进度不会错位到别的词上。
    by_alpha = sorted(picked, key=lambda x: (x["word"].lower(), x["word"]))
    id_of = {item["word"]: i for i, item in enumerate(by_alpha, start=1)}

    # 最常用的一批纯实词挪到"基础巩固"位置（见 BASIC_CONTENT_LAST 的说明）。
    content_sorted = sorted(
        [w for w in picked if w["word"].lower() not in TRIVIAL and w["pos"] in CONTENT_POS],
        key=lambda x: (x["frq"], x["bnc"], x["word"].lower()),
    )
    basic_band = {w["word"] for w in content_sorted[:BASIC_CONTENT_LAST]}

    ordered = sorted(picked, key=lambda item: study_score(item, basic_band))

    words = []
    for rank, item in enumerate(ordered, start=1):
        words.append({
            "id": id_of[item["word"]],
            "rank": rank,
            "word": item["word"],
            "phonetic": item["phonetic"],
            "pos": item["pos"],
            "meaning": item["meaning"],
        })

    sha = hashlib.sha256(db.read_bytes()).hexdigest()
    payload = {
        "version": 2,
        "source": "ECDICT (MIT) via npm cdict_query@1.0.0 / db/cdict2w.db",
        "sourceSha256": sha,
        "tag": "gk",
        "order": "词频名次 + 词性偏移量（实义词 0、复杂介词 -100、副词/专有名词 +1200、"
                 "数词 +2000、基础高频实词 +2500、代词/冠词等 +2500、小学虚词 +9000）",
        "candidates": len(unique),
        "count": len(words),
        "words": words,
    }
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"gk candidates: {len(unique)}  picked: {len(words)}  -> {out}")
    print(f"db sha256: {sha}")

    # 生成时就把前 15 个和复杂介词的位置打出来 —— 顺序是这个脚本最容易被调坏的部分，
    # 一眼能看见比事后去手机里翻要快。
    print("前 15 个：", ", ".join(f'{w["word"]}({w["pos"] or "-"})' for w in ordered[:15]))
    pos_map = {w["word"].lower(): i for i, w in enumerate(ordered, start=1)}
    print("复杂介词位置：", ", ".join(f"{p}:{pos_map.get(p, '-')}" for p in sorted(COMPLEX_PREPS)))
    counts = collections.Counter()
    for w in picked:
        counts[label_for(w, basic_band)] += 1
    print("分档：", ", ".join(f"{k} {v}" for k, v in counts.most_common()))


if __name__ == "__main__":
    main()

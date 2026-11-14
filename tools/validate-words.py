#!/usr/bin/env python3
"""校验 assets/words.json —— 坏数据进包会让 App 在用户手机上才暴露问题。

检查项（全部必须通过，退出码非 0 即失败）：
  1. count 与 words 长度一致，且等于 3500；
  2. id / rank 连续且唯一；
  3. word 非空、唯一（大小写不敏感）、且只含字母/撇号/连字符；
  4. meaning 非空、不含字典噪声标记（[计]/[医]/[化] 等）、长度在合理区间；
  5. phonetic 只含 IPA 字符集与空格（允许为空，界面上会隐藏）；
  6. pos 在允许集合内或为空。

同时输出各字段的覆盖率，便于发现"某批词条少了音标"这类整体缺字段问题。

用法：python tools/validate-words.py app/src/main/assets/words.json
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

EXPECTED_COUNT = 3500
WORD_RE = re.compile(r"^[A-Za-z][A-Za-z'\-]*$")
IPA_CHARS = set("abcdefghijklmnopqrstuvwxyzɡɪʊəɜɔæʌɑɒɛɐəˈˌːˈθðʃʒŋɹɾʔɚɝːˑ↓↑")
NOISE_RE = re.compile(r"[\[【](计|医|化|经|机|电|法|数|物|生|农|建|军|海|航|药|冶|纺|林|矿|食|心理|地理|地质|天|气|测|冶)[\]】]")
ALLOWED_POS = {"", "n.", "v.", "vt.", "vi.", "adj.", "adv.", "prep.", "conj.", "pron.",
               "art.", "num.", "int.", "aux.", "abbr.", "pl."}


def fail(msg: str, errors: list[str]) -> None:
    errors.append(msg)


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    path = Path(sys.argv[1])
    data = json.loads(path.read_text(encoding="utf-8"))
    words = data.get("words", [])
    errors: list[str] = []

    if data.get("count") != len(words):
        fail(f"count={data.get('count')} 与 words 长度 {len(words)} 不一致", errors)
    if len(words) != EXPECTED_COUNT:
        fail(f"应为 {EXPECTED_COUNT} 条，实际 {len(words)} 条", errors)

    seen_words: set[str] = set()
    seen_ids: set[int] = set()
    ranks: list[int] = []
    empty_phonetic = 0
    for i, w in enumerate(words):
        wid = w.get("id")
        if not isinstance(wid, int) or wid in seen_ids or not (1 <= wid <= len(words)):
            fail(f"id 非法或重复: {wid!r}（第 {i} 条）", errors)
        else:
            seen_ids.add(wid)
        # id 与 rank 是两件事：id 按字母序固定（重排学习顺序时旧进度不会错位到别的词），
        # rank 是学习顺序。数组本身按 rank 升序排列，所以这里断言 rank == 序号。
        if w.get("rank") != i + 1:
            fail(f"rank 与数组顺序不一致: {w.get('word')!r} rank={w.get('rank')} 序号={i + 1}", errors)
        if isinstance(w.get("rank"), int):
            ranks.append(w["rank"])

        word = (w.get("word") or "").strip()
        if not WORD_RE.match(word):
            fail(f"word 含非法字符: {word!r}", errors)
        key = word.lower()
        if key in seen_words:
            fail(f"word 重复: {word!r}", errors)
        seen_words.add(key)

        meaning = (w.get("meaning") or "").strip()
        if not meaning:
            fail(f"meaning 为空: {word!r}", errors)
        elif len(meaning) > 80:
            fail(f"meaning 过长({len(meaning)}): {word!r}", errors)
        elif NOISE_RE.search(meaning):
            fail(f"meaning 含字典噪声: {word!r} -> {meaning!r}", errors)

        phonetic = (w.get("phonetic") or "").strip()
        if not phonetic:
            empty_phonetic += 1
        else:
            bad = {ch for ch in phonetic if ch not in IPA_CHARS}
            if bad:
                fail(f"phonetic 含非 IPA 字符 {sorted(bad)}: {word!r} -> {phonetic!r}", errors)

        pos = (w.get("pos") or "").strip()
        if pos not in ALLOWED_POS:
            fail(f"pos 不在允许集合: {word!r} -> {pos!r}", errors)

    # 学习顺序的回归检查：开头必须是实义词。
    #
    # 这是用户提过的一个具体抱怨（"刚开始的几个词比较简单"）。它很容易在下次调整
    # 排序时被悄悄改回去，所以在这里钉成断言而不是写在文档里。
    CONTENT_POS = {"n.", "v.", "vt.", "vi.", "adj."}
    FUNCTION_POS = {"art.", "pron.", "conj.", "aux.", "num."}
    head = words[:40]
    wrong_head = [
        f"{w['word']}({w.get('pos') or '-'})" for w in head
        if (w.get("pos") or "") in FUNCTION_POS or (w.get("pos") or "") not in CONTENT_POS
    ]
    if wrong_head:
        fail(f"前 40 个里混进了非实义词：{', '.join(wrong_head[:8])}", errors)

    # 抽样打印，人工核对用
    for idx in (0, 1, 2, len(words) // 2, len(words) - 2, len(words) - 1):
        if 0 <= idx < len(words):
            w = words[idx]
            print(f"  [{w['rank']:>4}] id={w['id']:>4} {w['word']:<16} /{w['phonetic']:<16}/ {w['pos']:<5} {w['meaning']}")

    print(f"前 10 个：{', '.join(w['word'] for w in words[:10])}")
    print(f"count={len(words)} 无音标={empty_phonetic} 源={data.get('source')}")
    if errors:
        print(f"\n失败：{len(errors)} 项")
        for e in errors[:30]:
            print("  -", e)
        if len(errors) > 30:
            print(f"  ... 还有 {len(errors) - 30} 项")
        return 1
    print("校验通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

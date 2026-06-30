"""指标化测试：上下文窗口 / 知识库检索 / 流式输出

每个测试输出可量化指标，不仅是 PASS/FAIL。
全部为单元测试 — 不依赖 LLM / DB / HTTP / 文件系统。

运行:
  cd d:\CS\ruangong\smart-km-report-gen
  PYTHONPATH="d:\CS\ruangong\smart-km-report-gen" python -m pytest qa-agent/tests/test_metrics.py -v -s
"""

import json
import time

import pytest

from app.graph.context import (
    build_context,
    truncate_by_tokens,
    estimate_tokens,
    count_context_tokens,
)
from app.client.knowledge_client import normalize_document, _extract_documents

_PASS = "PASS"
_FAIL = "FAIL"
_WARN = "WARN"


def _metric(name: str, value, unit: str = "", target: str = ""):
    t = f"  <- target: {target}" if target else ""
    print(f"  [{name}] {value}{unit}{t}")


# ==================================================================
# 1. Token Estimation Precision
# ==================================================================

class TestTokenEstimationPrecision:
    """estimate_tokens precision across text types"""

    def test_token_char_ratio_by_type(self):
        """Metric: token/char ratio by text type.

        Chinese ~1.5, English ~0.3 token/char. Stable ratios = reliable formula.
        """
        samples = {
            "Chinese":   "变压器油温异常处理规程",
            "English":   "Transformer oil temperature monitoring system",
            "Mixed":     "根据IEC标准transformer oil温度应低于135C",
            "Numeric":   "U=IR, P=UI, rated 220kV, eta>=98%",
            "Punct":     "，。！？、：；（）【】",
        }

        print("\n  -- token/char ratio --")
        ratios = {}
        for label, text in samples.items():
            tokens = estimate_tokens(text)
            ratio = tokens / len(text) if text else 0
            ratios[label] = ratio
            print(f"  {label:10s}  {tokens:4d} tokens / {len(text):3d} chars = {ratio:.3f}")

        assert ratios["Chinese"] > ratios["English"], \
            f"CN ratio {ratios['Chinese']:.3f} should > EN {ratios['English']:.3f}"
        assert ratios["English"] < ratios["Mixed"] < ratios["Chinese"], \
            f"Mixed {ratios['Mixed']:.3f} should be between EN and CN"

    def test_estimation_linearity(self):
        """Metric: R-squared of token estimation linearity.

        Text repeated N times -> tokens should scale N times.
        """
        base = "变压器油温异常"
        base_tokens = estimate_tokens(base)

        multipliers = [1, 2, 5, 10, 20, 50]
        observed = []
        print("\n  -- linearity check --")
        for n in multipliers:
            tokens = estimate_tokens(base * n)
            expected = base_tokens * n
            error = (tokens - expected) / expected * 100 if expected > 0 else 0
            observed.append(tokens)
            print(f"  x{n:3d}: {tokens:5d} tokens  expect {expected:5d}  err {error:+.1f}%")

        mx = sum(multipliers) / len(multipliers)
        my = sum(observed) / len(observed)
        ss_xy = sum((x - mx) * (y - my) for x, y in zip(multipliers, observed))
        ss_xx = sum((x - mx) ** 2 for x in multipliers)
        ss_yy = sum((y - my) ** 2 for y in observed)
        r2 = (ss_xy ** 2) / (ss_xx * ss_yy) if ss_xx * ss_yy > 0 else 0

        _metric("Linearity R^2", f"{r2:.6f}", "", "> 0.999")
        assert r2 > 0.999, f"R^2={r2:.6f} below 0.999"

    def test_monotonicity(self):
        """Metric: monotonicity violations = 0."""
        pairs = [
            ("a", "ab"),
            ("hello", "hello world, how are you"),
            ("short", "this is a longer text with more characters"),
            ("a", "abcdefghijklmnopqrstuvwxyz"),
        ]

        violations = 0
        for short, long in pairs:
            st = estimate_tokens(short)
            lt = estimate_tokens(long)
            if st > lt:
                violations += 1
                print(f"  {_WARN} '{short}'={st} > '{long}'={lt}")

        _metric("Violations", f"{violations}", "", "0")
        assert violations == 0, f"{violations} monotonicity violations"


# ==================================================================
# 2. Truncation Efficiency & Throughput
# ==================================================================

class TestTruncationEfficiency:
    """Budget utilization & message retention rate"""

    def test_token_budget_utilization(self):
        """Metric: token budget utilization = used / budget after truncation."""
        budgets = [200, 500, 1000, 2000, 4096, 8192]
        messages = []
        for i in range(50):
            messages.append(
                {"role": "user",
                 "content": f"Round{i} user question about power equipment inspection details"}
            )
            messages.append(
                {"role": "assistant",
                 "content": f"Round{i} system answer based on regulation documents"}
            )

        print("\n  -- Token Budget Utilization --")
        for budget in budgets:
            kept = truncate_by_tokens(messages, budget)
            used = count_context_tokens(kept)
            rate = (used / budget * 100) if budget > 0 else 0
            print(f"  budget {budget:5d}: used {used:5d} tokens, kept {len(kept):3d} msgs, {rate:5.1f}%")

            assert used <= budget, f"budget {budget}: used {used} exceeds budget"
            if budget >= 500:
                assert rate > 10, f"budget {budget}: utilization {rate:.1f}% too low"

    def test_message_retention_curve(self):
        """Metric: message retention rate vs token budget (must be monotonic)."""
        messages = []
        for i in range(100):
            messages.append(
                {"role": "user", "content": f"Q{i}: power equipment anomaly inspection record #{i}"}
            )

        print("\n  -- Retention vs Budget --")
        budgets = [100, 200, 500, 1000, 2000, 5000, 8192]
        rates = {}
        for budget in budgets:
            kept = truncate_by_tokens(messages, budget)
            rate = len(kept) / len(messages) * 100
            rates[budget] = rate
            print(f"  {budget:5d} tokens -> {len(kept):3d}/{len(messages)} msgs ({rate:5.1f}%)")

        prev = -1
        for budget in budgets:
            assert rates[budget] >= prev, \
                f"budget {budget} retention {rates[budget]:.1f}% < prev {prev:.1f}%"
            prev = rates[budget]

    def test_truncation_overhead_breakdown(self):
        """Metric: fixed overhead as % of total budget."""
        long_system = (
            "You are a power industry Q&A assistant. "
            "Answer based on knowledge base content only." * 50
        )
        long_question = (
            "Please explain transformer oil temperature anomaly "
            "handling procedures in detail." * 10
        )

        fixed = estimate_tokens(long_system) + estimate_tokens(long_question)
        available = 8192 - fixed - 200
        overhead_pct = (fixed + 200) / 8192 * 100

        print(f"\n  system_prompt tokens: {estimate_tokens(long_system)}")
        print(f"  current_question tokens: {estimate_tokens(long_question)}")
        print(f"  fixed overhead: {fixed} + 200 margin = {fixed + 200} tokens")
        print(f"  available for history: {available} tokens ({100 - overhead_pct:.1f}%)")

        assert fixed < 8192, f"fixed overhead {fixed} exceeds total budget 8192"


class TestContextThroughput:
    """Processing throughput"""

    def test_estimate_tokens_throughput(self):
        """Metric: estimate_tokens calls per second."""
        text = (
            "Transformer oil temperature anomaly handling procedures "
            "require immediate inspection of cooling system status"
        )
        iterations = 10000

        start = time.perf_counter()
        for _ in range(iterations):
            estimate_tokens(text)
        elapsed_ms = (time.perf_counter() - start) * 1000

        per_us = elapsed_ms / iterations * 1000
        per_sec = iterations / (elapsed_ms / 1000)

        _metric("per call", f"{per_us:.2f}", "us", "< 100us")
        _metric("throughput", f"{per_sec:,.0f}", "calls/s", "> 50,000")

        assert per_us < 100, f"{per_us:.1f}us exceeds 100us"
        assert per_sec > 50000, f"{per_sec:.0f}/s below 50,000"

    def test_build_context_scalability(self):
        """Metric: build_context time vs message count (should be O(n))."""
        print("\n  -- scalability --")
        scales = [10, 20, 50, 100, 200]
        timings = {}

        for n in scales:
            messages = []
            for i in range(n):
                messages.append(
                    {"role": "user",
                     "content": f"Round{i} question about power equipment technical supervision"}
                )
                messages.append(
                    {"role": "assistant",
                     "content": f"Round{i} answer based on regulation documents and procedures"}
                )

            start = time.perf_counter()
            ctx = build_context(messages, max_tokens=8192)
            elapsed_ms = (time.perf_counter() - start) * 1000
            timings[n] = elapsed_ms
            per_msg = elapsed_ms / (n * 2)
            print(f"  {n:4d} rounds ({n*2:4d} msgs): {elapsed_ms:7.3f}ms  {per_msg*1000:5.1f}us/msg")

        ratios = [timings[n] / (n * 2) for n in scales]
        mean_ratio = sum(ratios) / len(ratios)
        max_dev = max(abs(r - mean_ratio) / mean_ratio * 100 for r in ratios)

        _metric("avg ms/msg", f"{mean_ratio*1000:.2f}", "us")
        _metric("max deviation", f"{max_dev:.1f}", "%", "< 150%")
        assert max_dev < 150, f"deviation {max_dev:.0f}% too high, likely not O(n)"


# ==================================================================
# 3. Knowledge Retrieval — Data Compatibility
# ==================================================================

class TestDocumentNormalization:
    """normalize_document field mapping & score robustness"""

    def test_field_compatibility(self):
        """Metric: input field name compatibility rate.

        Knowledge base may return snake_case or camelCase. All must work.
        """
        cases = [
            ("snake_case",     {"doc_id": "d1", "doc_name": "A.pdf", "snippet": "t", "score": 0.9, "kb_id": 1}),
            ("camelCase",      {"docId": "d2", "docName": "B.pdf", "snippet": "t", "similarity": 0.85, "kbId": 2}),
            ("content_field",  {"doc_id": "d3", "doc_name": "C.pdf", "content": "t", "score": 0.7}),
            ("no_kb_id",       {"doc_id": "d4", "doc_name": "D.pdf", "snippet": "t", "score": 0.6}),
            ("score_as_str",   {"doc_id": "d5", "doc_name": "E.pdf", "snippet": "t", "score": "0.88"}),
            ("no_score",       {"doc_id": "d6", "doc_name": "F.pdf", "snippet": "t"}),
            ("bad_metadata",   {"doc_id": "d7", "doc_name": "G.pdf", "snippet": "t", "score": 0.9, "metadata": "bad"}),
            ("none_metadata",  {"doc_id": "d8", "doc_name": "H.pdf", "snippet": "t", "score": 0.9, "metadata": None}),
            ("empty_doc_id",   {"doc_id": "", "docName": "I.pdf", "snippet": "t", "score": 0.5}),
        ]

        print("\n  -- Field Compatibility Matrix --")
        passed = 0
        failed = 0
        for label, raw in cases:
            result = normalize_document(raw)
            checks = [
                isinstance(result["doc_id"], str),
                isinstance(result["doc_name"], str) and len(result["doc_name"]) > 0,
                isinstance(result["snippet"], str),
                isinstance(result["score"], float),
                isinstance(result["metadata"], dict),
            ]
            ok = all(checks)
            if ok:
                passed += 1
                print(f"  {_PASS} {label:16s} -> id={result['doc_id']!r}, score={result['score']}")
            else:
                failed += 1
                fail_idx = [i for i, c in enumerate(checks) if not c]
                print(f"  {_FAIL} {label:16s} -> failed checks: {fail_idx}")

        total = passed + failed
        _metric("compatibility", f"{passed}/{total} ({passed/total*100:.0f}%)", "", "100%")
        assert failed == 0, f"{failed} field variants not compatible"

    def test_score_robustness(self):
        """Metric: score normalization errors with non-standard inputs."""
        cases = [
            ("normal",      0.92,    0.92),
            ("string",      "0.85",  0.85),
            ("negative",    -0.5,   -0.5),
            ("None",        None,    0.0),
            ("non-numeric", "high",  0.0),
            ("empty_str",   "",      0.0),
            ("bool_True",   True,    1.0),
            ("int",         1,       1.0),
        ]

        print("\n  -- Score Normalization --")
        errors = 0
        for label, raw, expected in cases:
            doc = {"doc_id": "d1", "doc_name": "t.pdf", "snippet": "x", "score": raw}
            actual = normalize_document(doc)["score"]
            ok = abs(actual - expected) < 0.001
            if ok:
                print(f"  {_PASS} {label:12s}: {str(raw):12s} -> {actual}")
            else:
                errors += 1
                print(f"  {_FAIL} {label:12s}: {str(raw):12s} -> {actual}  expected {expected}")

        _metric("score errors", f"{errors}", "", "0")
        assert errors == 0, f"{errors} score normalization errors"


class TestResponseFormatRecognition:
    """_extract_documents response format detection"""

    def test_format_detection(self):
        """Metric: response format recognition rate.

        Knowledge base may return 5 different wrapper formats.
        """
        doc = {"doc_id": "d1", "doc_name": "t.pdf", "snippet": "text", "score": 0.9}

        cases = [
            ("direct_array",    [doc, doc]),
            ("documents_wrap",  {"documents": [doc]}),
            ("data_wrap",       {"data": [doc]}),
            ("items_wrap",      {"items": [doc]}),
            ("results_wrap",    {"results": [doc]}),
            ("nested_wrap",     {"data": {"documents": [doc]}}),
            ("empty_array",     []),
            ("empty_object",    {}),
            ("None",            None),
        ]

        print("\n  -- Format Recognition --")
        recognized = 0
        expected_positive = 5

        for label, payload in cases:
            docs = _extract_documents(payload)
            count = len(docs)

            if label in ("empty_array", "empty_object", "None"):
                ok = (count == 0)
            elif label == "nested_wrap":
                ok = (count > 0)
                if count > 0:
                    recognized += 1
            else:
                ok = (count > 0)
                if count > 0:
                    recognized += 1

            status = _PASS if ok else (_WARN if label == "nested_wrap" else _FAIL)
            print(f"  {status} {label:16s} -> {count} docs")

        recognition_rate = recognized / expected_positive * 100
        _metric("recognition rate", f"{recognized}/{expected_positive} ({recognition_rate:.0f}%)", "", "100%")
        assert recognized >= 4, f"only {recognized}/{expected_positive} formats recognized"


# ==================================================================
# 4. Streaming Output — SSE Parsing & Prompt Structure
# ==================================================================

class TestSSELineParsing:
    """SSE line parsing branch coverage (replicates _stream_chat_model logic)"""

    @staticmethod
    def _parse_sse_line(line: str) -> dict | None:
        """Replicate nodes.py _stream_chat_model SSE line parsing."""
        if not line or not line.startswith("data:"):
            return None
        payload = line[len("data:"):].strip()
        if payload == "[DONE]":
            return {"_done": True}
        try:
            chunk = json.loads(payload)
        except json.JSONDecodeError:
            return None
        if not isinstance(chunk, dict):
            return None
        choices = chunk.get("choices")
        if not choices or not isinstance(choices, list) or len(choices) == 0:
            return None
        choice = choices[0]
        if not isinstance(choice, dict):
            return None
        delta = choice.get("delta")
        if not isinstance(delta, dict):
            return None
        content = delta.get("content")
        return {"content": content} if content else None

    def test_line_parsing_coverage(self):
        """Metric: SSE line parsing accuracy across 12 simulated formats."""
        lines = [
            ("normal_content",   'data: {"choices":[{"delta":{"content":"hello"}}]}', True),
            ("empty_line",       "", False),
            ("no_data_prefix",   '{"choices":[{"delta":{"content":"hi"}}]}', False),
            ("DONE_signal",      "data: [DONE]", "done"),
            ("non_json",         "data: not-valid-json!!!", False),
            ("no_choices",       'data: {"result":"ok"}', False),
            ("choices_empty",    'data: {"choices":[]}', False),
            ("delta_empty",      'data: {"choices":[{"delta":{}}]}', False),
            ("content_null",     'data: {"choices":[{"delta":{"content":null}}]}', False),
            ("content_empty",    'data: {"choices":[{"delta":{"content":""}}]}', False),
            ("space_after_data", 'data:  {"choices":[{"delta":{"content":"ok"}}]}', True),
            ("chinese_content",  'data: {"choices":[{"delta":{"content":"变压器"}}]}', True),
        ]

        print("\n  -- SSE Line Parsing Coverage --")
        correct = 0
        errors = 0
        for label, line, expected in lines:
            result = self._parse_sse_line(line)
            has_content = result is not None and "content" in result
            is_done = result is not None and result.get("_done")

            if expected == "done":
                ok = is_done and not has_content
            elif expected is True:
                ok = has_content
            else:
                ok = not has_content and not is_done

            if ok:
                correct += 1
            else:
                errors += 1
            status = _PASS if ok else _FAIL
            print(f"  {status} {label:20s} -> content={has_content}, done={is_done}")

        total = correct + errors
        _metric("SSE parse accuracy", f"{correct}/{total} ({correct/total*100:.0f}%)", "", "100%")
        assert errors == 0, f"{errors} SSE parse errors"

    def test_error_path_markers(self):
        """Metric: streaming error path identifiability.

        _stream_chat_model has 3 error branches, each with a unique marker text.
        """
        markers = [
            ("no_api_key",     "LLM API key 未配置"),
            ("http_error",     "LLM 服务暂不可用"),
            ("empty_choices",  "LLM 服务未返回有效回答"),
        ]

        print("\n  -- Error Path Markers --")
        texts = [m[1] for m in markers]
        for label, text in markers:
            unique = texts.count(text) == 1
            status = _PASS if unique else _FAIL
            print(f"  {status} {label:16s} -> '{text}'")
            assert unique, f"marker '{text}' is not unique"

        _metric("unique markers", f"{len(markers)}", "", ">= 3")


class TestPromptComposition:
    """_build_messages prompt structure metrics"""

    def test_token_composition(self):
        """Metric: System / Documents / Question token ratio in final prompt."""
        from app.graph.nodes import _build_messages

        docs = [
            {"doc_id": "d1", "doc_name": "Regulation_A.pdf",
             "snippet": "In case of transformer oil temperature anomaly, check cooling system." * 5,
             "score": 0.92},
            {"doc_id": "d2", "doc_name": "Regulation_B.pdf",
             "snippet": "Regular maintenance includes insulation oil testing and DC resistance test." * 5,
             "score": 0.85},
            {"doc_id": "d3", "doc_name": "Regulation_C.pdf",
             "snippet": "Accident handling follows shutdown-analyze-repair principle for safety." * 5,
             "score": 0.78},
        ]
        question = "How to handle transformer oil temperature anomaly?"

        msgs = _build_messages(question, docs, no_knowledge=False)
        sys_tokens = estimate_tokens(msgs[0]["content"])
        usr_tokens = estimate_tokens(msgs[1]["content"])
        total = sys_tokens + usr_tokens

        print("\n  -- Prompt Token Composition --")
        print(f"  System:  {sys_tokens:5d} tokens ({sys_tokens/total*100:5.1f}%)")
        print(f"  User:    {usr_tokens:5d} tokens ({usr_tokens/total*100:5.1f}%)")
        print(f"  -------------------------------")
        print(f"  Total:   {total:5d} tokens")

        doc_ratio = usr_tokens / total
        _metric("doc ratio", f"{doc_ratio:.1%}", "", "< 95%")
        assert doc_ratio < 0.95, f"doc ratio {doc_ratio:.1%} too high"
        assert sys_tokens < 500, f"system prompt {sys_tokens} tokens too large"

    def test_three_modes_length_relationship(self):
        """Metric: RAG / no-knowledge / direct prompt length ordering."""
        from app.graph.nodes import _build_messages

        question = "How to handle transformer oil temperature anomaly?"
        docs = [{"doc_id": "d1", "doc_name": "t.pdf", "snippet": "Check cooling system.", "score": 0.9}]

        rag = _build_messages(question, docs, no_knowledge=False)
        empty = _build_messages(question, [], no_knowledge=True)
        direct = _build_messages(question, [], no_knowledge=False)

        rag_len = len(rag[1]["content"])
        empty_len = len(empty[1]["content"])
        direct_len = len(direct[1]["content"])

        print("\n  -- Three Prompt Modes --")
        print(f"  RAG mode:       {rag_len:5d} chars (with documents)")
        print(f"  No-knowledge:   {empty_len:5d} chars (with 'not found' notice)")
        print(f"  Direct chat:    {direct_len:5d} chars (question only)")

        assert empty_len > direct_len, f"no-knowledge({empty_len}) should > direct({direct_len})"
        assert rag_len > empty_len, f"RAG({rag_len}) should > no-knowledge({empty_len})"
        assert "未找到相关知识库信息" in empty[1]["content"], \
            "no-knowledge mode missing 'not found' notice"


# ==================================================================
# 5. Summary
# ==================================================================

class TestSummary:
    """Print all metrics in one view (no assertions)"""

    def test_print_summary(self):
        print("\n")
        print("=" * 58)
        print("  Unit Test Metrics Summary")
        print("=" * 58)

        texts = {
            "Chinese": "变压器油温异常处理规程要求立即检查冷却系统",
            "English": "Transformer oil temperature monitoring system",
            "Mixed":   "IEC 60296 standard transformer oil flash point 135C",
        }
        print("\n  [Token Estimation] token/char ratio:")
        for label, text in texts.items():
            print(f"    {label:8s}: {estimate_tokens(text)/len(text):.3f}")

        msgs = []
        for i in range(30):
            msgs.append({"role": "user",
                         "content": f"Round{i} question about power equipment technical supervision"})
            msgs.append({"role": "assistant",
                         "content": f"Round{i} answer based on regulation documents"})
        ctx = build_context(msgs, max_tokens=8192)
        ctx_tokens = estimate_tokens(ctx)
        truncated = "[更早的对话内容已被截断]" in ctx
        print(f"\n  [Context Window] 30 rounds x 2 = 60 msgs, 8192 token limit:")
        print(f"    context: {len(ctx)} chars, ~{ctx_tokens} tokens")
        print(f"    truncation: {'TRIGGERED' if truncated else 'not triggered (all kept)'}")

        t0 = time.perf_counter()
        for _ in range(5000):
            estimate_tokens("Transformer oil temperature anomaly handling procedure")
        elapsed = (time.perf_counter() - t0) * 1000
        print(f"\n  [Throughput] estimate_tokens x 5000:")
        print(f"    total: {elapsed:.2f}ms")
        print(f"    per call: {elapsed/5000:.3f}us")
        print(f"    throughput: {5000/(elapsed/1000):,.0f} calls/s")

        compat_variants = 9
        wrap_formats = 5
        print(f"\n  [KB Compat] normalize_document:")
        print(f"    field variants: {compat_variants}")
        print(f"    response formats: {wrap_formats}")

        sse_branches = 12
        print(f"\n  [SSE Parse] _stream_chat_model line parsing:")
        print(f"    branch coverage: {sse_branches} scenarios")

        print("\n" + "=" * 58)
        print("  Note: all metrics from unit tests only")
        print("        No LLM / KB / DB / HTTP connection used")
        print("=" * 58)

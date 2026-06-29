# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

<!--
Document your project's quality standards here.

Questions to answer:
- What patterns are forbidden?
- What linting rules do you enforce?
- What are your testing requirements?
- What code review standards apply?
-->

(To be filled by the team)

---

## Forbidden Patterns

<!-- Patterns that should never be used and why -->

(To be filled by the team)

---

## Required Patterns

<!-- Patterns that must always be used -->

### Scenario: Agent Intent Routing Contracts

#### 1. Scope / Trigger

- Trigger: changing `qa-agent` intent values, route modes, LangGraph node outputs, or fields consumed by B/C streaming and display code.
- Reason: intent routing is a cross-layer contract. B reads graph state for API/SSE output, C may enrich `thinking_steps` and `citations`, and future knowledge/report modules depend on clear route boundaries.

#### 2. Signatures

- Graph entrypoint: `await agent_graph.ainvoke(state: dict) -> dict`.
- Required input shape: `messages: list[Any]`; recommended `question: str`.
- Optional input shape: `user_id: int`, `conversation_id: int`, `selected_kb_ids: list[int]`.
- Routing function shape: `route_by_intent(state: AgentState) -> Literal["rag", "direct", "clarify"]`.

#### 3. Contracts

- Intent values: `CHAT`, `KNOWLEDGE_QA`, `DOCUMENT_SEARCH`, `REPORT_GENERATION`, `KB_MANAGEMENT`, `TASK_ACTION`.
- Mode values: `direct`, `rag`, `clarify`.
- Classifier output fields: `intent`, `confidence`, `reason`, `needs_clarification`, `source`.
- State observability fields: `intent_confidence`, `route_reason`, `classification_source`, `needs_clarification`.
- Unsupported execution intents (`REPORT_GENERATION`, `KB_MANAGEMENT`, `TASK_ACTION`) must not trigger RAG until their execution nodes exist.

#### 4. Validation & Error Matrix

- Rule match with high confidence -> use `classification_source=rule` and route deterministically.
- Rule miss + valid LLM JSON -> normalize and clamp confidence to `0.0..1.0`.
- Rule miss + missing LLM key -> `classification_source=fallback`, `needs_clarification=true`, `mode=clarify`.
- Rule miss + invalid LLM JSON -> fallback clarify, never default to RAG.
- Empty retrieval on RAG route -> generate a controlled no-knowledge response.

#### 5. Good/Base/Bad Cases

- Good: `查找文档并生成一份报告` -> `REPORT_GENERATION` + `clarify`, because report generation is recognized but unsupported.
- Base: `什么是电力技术监督？` -> `KNOWLEDGE_QA` + `rag`.
- Bad: adding a new intent constant but letting `route_by_intent` fall through to RAG by default.

#### 6. Tests Required

- Golden cases for every intent value and every route mode.
- A mixed-intent case where unsupported execution intent takes precedence over document search.
- A fallback case with LLM classification unavailable or invalid.
- Assertions must cover `intent`, `mode`, `needs_clarification`, and observability fields.

#### 7. Wrong vs Correct

Wrong:

```python
if state.get("intent") == CHAT_INTENT:
    return "direct"
return "rag"
```

Correct:

```python
mode = state.get("mode")
if mode in ("rag", "direct", "clarify"):
    return mode
return "clarify"
```

### Scenario: qa-agent HTTP/SSE Contract Verification

#### 1. Scope / Trigger

- Trigger: changing `qa-agent` FastAPI routes, conversation persistence adapters, `agent_graph` API/SSE integration, or thinking/citation payload conversion.
- Reason: `/api/chat` crosses API, database, LangGraph, and shared service layers. A passing unit test is not enough unless the HTTP contract and SSE event stream are also exercised.

#### 2. Signatures

- App entrypoint: `qa_agent.main:app`.
- Conversation routes: `GET /api/conversations`, `POST /api/conversations`, `PATCH /api/conversations/{conversation_id}`, `DELETE /api/conversations/{conversation_id}`, `GET /api/conversations/{conversation_id}/messages`.
- Agent routes: `POST /api/chat/test -> ChatTestResp`; `POST /api/chat -> text/event-stream`.

#### 3. Contracts

- `POST /api/chat/test` request: `question: str`, `selected_kb_ids: list[int]`, optional `user_id: int`, optional `messages: list[{role, content}]`.
- `POST /api/chat` request: `conversation_id: int`, `question: str`, optional `user_id: int`, `selected_kb_ids: list[int]`.
- SSE event names remain `thinking`, `message`, `citation`, `error`, and `done`.
- Thinking SSE payload shape is `{"type":"thinking", "step_type":"...", "message":"...", "elapsed_ms": number|null}`.
- Citation SSE payload shape is one merged `{"type":"citation", "citations":[...], "merged":true}` event.
- Message streaming must include token-level `message` deltas with `finished=false` and a final `message` event with `delta:""` and `finished=true`.

#### 4. Validation & Error Matrix

- Empty `question` -> HTTP 400 for `POST /api/chat` and `POST /api/chat/test`.
- Missing database session factory -> SSE `error` event followed by `done`.
- Missing conversation during chat stream -> SSE `error` event followed by `done`.
- Missing conversation in conversation CRUD/messages routes -> HTTP 404.
- Agent graph failure after assistant message creation -> persist failed status, then SSE `error` and `done`.

#### 5. Good/Base/Bad Cases

- Good: test the real FastAPI app with dependency overrides or fakes for DB and agent graph, then parse SSE lines and assert event names plus payload fields.
- Base: run `python -m pytest qa-agent/tests/test_chat.py -v` for deterministic utility and integration-contract tests.
- Bad: only calling `_stream_chat` directly and claiming all FastAPI interfaces work.

#### 6. Tests Required

- HTTP test for each registered route under `/api` with success status and response shape assertions.
- SSE test for `POST /api/chat` asserting at least one `thinking`, one streaming `message`, one merged `citation`, a final `message` with `finished=true`, and final `done`.
- Persistence assertion that saved assistant `thinking_steps` and `citations` use the same canonical structures emitted through SSE.
- Tests must avoid real DeepSeek, MySQL, and knowledge-base services by patching graph and DB boundaries.

#### 7. Wrong vs Correct

Wrong:

```python
response = await chat_test(req)
assert response.final_response
```

Correct:

```python
with TestClient(app).stream("POST", "/api/chat", json=req) as response:
    events = parse_sse(response.read().decode("utf-8"))
assert events[-1]["event"] == "done"
```

---

## Testing Requirements

<!-- What level of testing is expected -->

- Agent routing changes require deterministic golden-case tests that do not depend on real LLM or knowledge-base services.
- Tests should live outside C-owned `qa-agent/tests/test_chat.py` unless the team explicitly assigns integration testing to the task.
- FastAPI/SSE changes in `qa-agent` require route-level testing through `qa_agent.main:app` plus dependency overrides for DB and agent graph boundaries.

---

## Code Review Checklist

<!-- What reviewers should check -->

- [ ] New or changed intent values are reflected in constants, classifier normalization, routing, docs, and golden cases.
- [ ] Unsupported execution intents do not silently enter RAG.
- [ ] State fields added for B/C consumers are additive and contain no secrets or raw API keys.
- [ ] Missing external services produce controlled fallback output rather than graph crashes.

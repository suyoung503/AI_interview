# 근거 기반 평가 품질 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Preserve the user's task-level subagent preference.

**Goal:** 고정된 가상 사례 10개로 v4/v5를 비교하고, 항목별 인용에 근거한 새 평가를 구현·검증·커밋한다. portfolio.md는 로컬에만 남긴다.
**Architecture:** 제공자 응답을 세 축별 evidence/coveredPoints/improvements로 구조화하고 기존 공개 필드를 서버에서 조립한다. 구형 JSON은 nullable 추가 필드로 호환한다. 실제 비교는 동일 fixture를 버전별 격리 실행한다.
**Tech Stack:** 기존 Java17+/Spring/JPA, JSON Schema, Node 테스트, Groq gpt-oss-120b.
**Spec:** docs/superpowers/specs/2026-11-22-grounded-evaluation-design.md (사용자 승인 2026-11-22).

## Global Constraints
- 기존 .env/private application.yml/target/live-web/*.env 및 demo-login.txt 내용을 읽거나 출력하지 않는다.
- 사용자 portfolio.md는 local info/exclude에만 등록되어 있다. git add -A 금지, 명시 파일만 커밋한다.
- 기존 실행 중인 실제 API 웹사이트와 DB를 중단하거나 교체하지 않는다. 빌드는 별도 경로에서 수행한다.
- 코드 파일을 더 잘게 분리하지 않는다. 새 모델 타입은 기존 InterviewAiModels에 중첩한다.
- 실제 호출은 baseline10+candidate10 총20 한도. 오류 시 중단, 재시도/유료 전환/다른 모델 없음.
- 기본 테스트에서는 실제 .env를 로드하거나 실제 AI에 연결하지 않는다.
- 자동 push 또는 main 머지는 하지 않는다.

## Review Focus
- 구형 8필드 Evaluation JSON은 신규 필드 없이도 그대로 조회/왕복되는가.
- 긍정 축 개선점을 지우기 전에 malformed/긴 문자열/출처 오류를 전부 검증하는가.
- 원답/꼬리 인용 출처가 정확하고, 답변에 없는 텍스트는 저장되지 않는가.
- 잘못된 followUpAxis/긍정 축의 꼬리를 억제하면서 진짜 오류·정보 부족은 유지하는가.
- 예산/phase/fixture 고정/재시도 차단으로 전후20회 비교가 공정하고 중복 실행되지 않는가.

## Task 1 — 고정 사례와 안전한 비교 도구 (제품 v4 유지)
**Files:** create src/test/resources/quality/evaluation-cases.json,
src/test/java/com/interview/live/GroqQualityEvaluationTest.java. Update existing README/test fixture tests only as needed.
**Interfaces:** fixture top-level cases array of 10 unique {id,title,context,expected,coveredFacts,requiredFeedback,forbiddenFeedback}.
context is current EvaluationContext, including frozen legacy 8-field initialEvaluation for comprehensive samples.
expected uses allowed sets for technicalAccuracy/reasoning/clarity, not literal feedback strings.
No data/rubric changes after baseline calls; hash recorded.
- [ ] Fixture: two partial-correct/missing-reason cases, constrained valid choice, familiarity-only insufficiency,
  clear technical error, honest lack of information, new details absent from resume, clear but wrong answer,
  sufficient follow-up, insufficient final follow-up. All synthetic, no PII.
- [ ] Add one cohesive test harness with normal offline fixture/budget guard tests and a single method opt-in
  live comparison. Keep old GroqLiveClient cap6 unchanged.
- [ ] Live method loads .env key only inside JVM after opt-in, never logs it. Current GroqService public API
  is used with no product prompt override. Serialize evaluation generically to JSON so harness compiles on v4/v5.
- [ ] Execution contract: -Dgroq.quality.confirmed=true -Dgroq.quality.phase=before|after
  -Dgroq.quality.expected-version=resume-interview-v4|v5 -Dgroq.quality.output-dir=<owned ignored absolute dir>.
  Shared durable attempt state caps total20 and each phase10, reserves before HTTP, prevents reruns,
  enforces same fixture SHA and >=65s between all attempts across processes. After requires before completed.
  JVM HTTP no-retry flags and surefire rerun=0 required. Safe errors only, first failure stops.
- [ ] Offline guard tests first (no missing credentials/files causing live mode), loopback only if HTTP needed.
  Default full tests skip the opt-in method. Commit Task1 with approved spec/plan.

Root then independently reviews Task1 and runs baseline10 from an immutable export of this commit.
Product implementation may proceed in parallel with that isolated baseline run.

## Task 2 — v5 평가·저장·근거 보기
**Files:** modify dto/InterviewAiModels.java, service/GroqInterviewContract.java, service/GroqService.java,
static/app.js, existing GroqServiceTest/InterviewFlowTest/InterviewStorageTest/WebPreview and necessary docs.
**Interfaces:** Evaluation gains nullable assessments and followUpAxis while preserving an 8-argument legacy constructor.
AxisAssessment(axis,evidence,coveredPoints,improvements), AnswerEvidence(source,quote), and enums are nested types.
Provider INITIAL_FIELDS are technicalAccuracy,reasoning,clarity,assessments,uncertainties,followUpAxis,followUpQuestion;
COMPREHENSIVE_FIELDS omit both follow-up keys. Public old8 fields remain server-computed where specified.
- [ ] Write failing tests for exact3 distinct axes, quote source/substring/limits, missing/extra fields,
  positive-axis filtering after validation, true negative gaps retained, follow-up target policy, stage termination.
- [ ] Implement exact spec without Redis keyword rules or second AI calls. Version becomes resume-interview-v5.
- [ ] Add old JSON roundtrip/unchanged history and new assessment persistence/HTTP detail regressions.
  Update provider fixtures to v5 without weakening existing malformed/ownership/race cases.
- [ ] UI uses textContent and optional collapsed evidence details; old records without new fields remain usable.
  Update test-only preview responses, no real API in UI verification.
- [ ] Full offline Maven, Node, isolated MySQL regression and test-only browser evidence where available.
  Keep running v4 website/JAR untouched; v5 artifact separate. Commit Task2 after independent review gate.

## Task 3 — 전후 비교와 로컬 기록
**Files:** local-only portfolio.md; update public verification summary/README with truthful synthetic results.
**Interfaces:** unchanged frozen cases, shared attempt ledger before/after JSON and current v5 compiled service.
- [ ] Root runs candidate10 only after Task2 reviewed, within remaining approved budget, no retries.
- [ ] Compare each pair against fixed expected/forbidden/required facts manually: axis consistency, repeated demands,
  unfair penalty for new facts, dropped legitimate problems, unnecessary follow-up. Record misses/ambiguity honestly.
- [ ] portfolio.md: current-code/history-based problem→alternatives→choice→evidence→limits; technical factual style,
  no invented performance/sole-author claims, no credentials. Add today's measured comparison only after collected.
- [ ] Verify portfolio ignored/untracked, public commits contain no portfolio content or keys.
- [ ] Final regression and review, commit results/docs only. No push/main merge.

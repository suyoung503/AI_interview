# 이력서 기반 AI 면접 코치 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 승인된 설계대로 이력서 저장부터 근거 있는 질문 5개, 답변 평가, 1회 꼬리 질문, 재조회까지 동작하는 Groq 기반 백엔드를 만든다.

**Architecture:** 기존 JWT 인증과 JPA 엔티티를 재사용한다. Resume를 추가하고 Question을 세션에 연결하며, 외부 Groq 호출은 DB 트랜잭션 밖에서 수행한다. 저장 시 소유권·상태·중복을 다시 검증하며 과거 기록은 보존한다.

**Tech Stack:** Java 17 target, Spring Boot 4.1.0, Spring Data JPA, MySQL, H2 test profile, RestClient, Jakarta Validation, JUnit. 프로젝트가 사용하는 Jackson 버전을 먼저 확인하여 Boot 4와 호환되는 API를 사용한다.

**Spec:** `docs/superpowers/specs/2026-11-20-resume-interview-coach-design.md` (2026-11-20 사용자 승인)

## Global Constraints

- 기본 질문 **정확히 5개**, 꼬리 질문은 **기본 질문 하나당 최대 1개**, 꼬리의 꼬리 질문 금지.
- 신규 평가는 숫자 점수 없이 기술적 정확성·근거·설명력과 구체적인 피드백을 제공한다.
- 다른 사용자의 자원 및 잘못 조합한 세션/질문 ID는 404로 처리한다.
- AI 실패 시 기본 점수나 부분 결과를 정상 저장하지 않는다.
- Groq 한 제공자/모델만 연동하며 자동 재호출·유료 업그레이드·다른 모델 fallback을 하지 않는다.
- 기존 카테고리·질문·세션·답변 데이터를 삭제하지 않는다.
- PDF, 음성, 프런트엔드, 배포, 벡터 DB/RAG, 다중 제공자 지원은 하지 않는다.
- API 키·이력서·답변·제공자 오류 원문을 일반 로그/오류 응답에 출력하지 않는다.

## Review Focus

- 같은 질문에 동시 답변: 네트워크 호출이 겹쳐도 저장되는 답변/꼬리 질문은 하나여야 한다(Task 4).
- 평가 대기 중 세션 종료: 늦게 도착한 결과를 저장하면 안 된다(Task 4).
- 기존 category/score가 있는 기록과 없는 신규 기록 혼합: 상세 및 통계가 깨지면 안 된다(Task 5).
- 공백·Unicode·JSON 지시문이 포함된 이력서: 입력을 데이터로 전달하고 실제 인용문 근거를 검증해야 한다(Task 2, 3).
- 거부/출력 잘림/429/부분 JSON: 정상 평가가 저장되거나 자동 재호출되면 안 된다(Task 3, 4).

## 파일 책임과 인터페이스

- `entity/Resume.java`, `repository/ResumeRepository.java`: 사용자 소유 불변 텍스트 스냅샷.
- `dto/ResumeCreateRequest.java`, `dto/ResumeResponse.java`, `service/ResumeService.java`, `controller/ResumeController.java`: 저장/목록/단건 조회, 길이 검증.
- `service/GroqService.java`: Groq HTTP 요청/응답, 구조 검증, 단일 모델, 타임아웃 및 안전한 오류 매핑.
- `dto/InterviewAiModels.java`: 생성 질문, 평가 요청 컨텍스트, 평가 결과의 불변 record/enum 계약. DB 저장 표현과 API 표현 간 변환은 구현 시 가장 작은 방식을 택한다.
- `entity/Session.java`, `entity/Question.java`, `entity/Answer.java`: 이력서/세션 소속, 생성 근거/의도/평가 요소, 구조화 평가 저장.
- `service/SessionService.java`: 이력서로 세션 생성, 목록/상세/완료.
- `service/AnswerService.java`: 권한 확인→평가 요청→짧은 저장 트랜잭션으로 결과 반영.
- `service/InterviewPersistenceService.java`: 재검증과 짧은 쓰기 트랜잭션. 별도 Spring 빈으로 @Transactional self-invocation 문제를 피한다.
- 기존 DTO·repository·exception·StatsService: 신규 nullable 필드/오류/조회에 필요한 부분만 수정.
- `src/test/java/com/interview/ResumeApiTest.java`, `InterviewFlowTest.java`, `GroqServiceTest.java`: HTTP 경계·JPA·실제 RestClient와 모의 제공자 응답 검증.
- `src/test/java/com/interview/support/GroqStub.java`: 테스트 전용 모의 HTTP 제공자. 제품 코드에 테스트 전용 메서드를 추가하지 않는다.
- `src/main/resources/db/migration/`: 과거 스키마에서 필요한 nullable 컬럼/FK 변경을 위한 수동 검토 SQL. 새 마이그레이션 프레임워크는 추가하지 않는다.

## Task 1: 이력서 저장·조회와 입력/권한 검증

**Files:** 위 Resume 관련 6개 파일, ErrorCode/GlobalExceptionHandler, ResumeApiTest, test 프로파일.

**Interfaces:** `ResumeService.create(String email, ResumeCreateRequest request): ResumeResponse`, `list(String email): List<ResumeResponse>`, `get(String email, Long id): ResumeResponse`. text는 1~20,000자(공백만 불가). 목록은 원문을 제외하고 id/createdAt를 제공한다.

- [ ] 실패 테스트: 인증 없는 요청 401, 등록 201 후 다른 요청에서 동일 원문 조회 200, 다른 사용자 조회 404, 공백/누락/20,001자 입력 400. 새 엔드포인트가 없어 실패하는지 확인한다.
- [ ] 실행: `./mvnw -o -Dspring.profiles.active=test -Dtest=ResumeApiTest test`.
- [ ] Resume 엔티티/DTO/service/controller와 입력 오류 핸들링을 최소 구현한다. 기존 인증의 동작은 불필요하게 바꾸지 않는다.
- [ ] 해당 테스트와 전체 테스트를 실행하고 통과를 확인한다.
- [ ] 변경 파일만 검토하고 작업 기록에 RED→GREEN 증거를 남긴다. 커밋은 선택한 작업 공간에서 권한이 허용될 때만 수행한다.

## Task 2: 세션 소속 질문과 평가 데이터의 저장 계약

**Files:** Session/Question/Answer, 관련 repository/DTO, `InterviewAiModels.java`, `InterviewFlowTest.java`, 마이그레이션 SQL.

**Interfaces:** `GeneratedQuestion(content, sourceQuote, intent, evaluationCriteria)`, `Evaluation(technicalAccuracy, reasoning, clarity, strengths, improvements, uncertainties, summary, followUpQuestion)`. 열거값은 설계 7절을 그대로 사용한다.

- [ ] 실패 테스트: 세션과 연결된 기본 질문/자식 질문, 이력서 참조, 항목별 평가가 저장 후 새 트랜잭션에서 재조회되어야 한다. 이전 카테고리/점수 기록은 그대로 조회되어야 한다.
- [ ] 테스트를 실행하여 신규 저장 필드/관계 누락에 의한 실패를 확인한다.
- [ ] 기존 필드를 보존하면서 필수 연결과 nullable category/score를 구현한다. 이력서는 불변이고 질문의 parent는 같은 세션의 기본 질문만 허용한다.
- [ ] 실제 DB 자동 변경을 실행하지 않고 수동 마이그레이션 SQL과 사전 점검/백업 절차를 작성한다. 중복 과거 답변 때문에 전역 UNIQUE(question_id)를 무작정 추가하지 않는다.
- [ ] 저장 테스트 및 전체 테스트 통과를 확인한다.

## Task 3: 단일 Groq 클라이언트와 질문/평가 검증

**Files:** `GroqService.java`, `InterviewAiModels.java`, GroqServiceTest/GroqStub, ErrorCode, 기본/예제/test 설정, `.env.example`.

**Interfaces:** `GroqService.generateQuestions(String resumeText): List<GeneratedQuestion>`, `evaluate(EvaluationContext context): Evaluation`. 평가 컨텍스트는 이력서 근거/질문/원답/선택적 이전 평가·꼬리 질문·꼬리 답변을 포함한다.

- [ ] 실패 테스트: 실제 RestClient 요청을 모의 서버로 받아 정상 JSON에서 5개 질문 또는 올바른 평가를 반환하고, 이력서의 Unicode/따옴표/개행이 손실되지 않아야 한다.
- [ ] 실패 테스트: 질문 4/6개, 중복 질문, 원문에 없는 인용문, 빈 필드, 잘못된 평가 enum/JSON, 거부·빈 choices·finish_reason=length, HTTP 401/429/500, 응답 지연을 거부해야 한다. 요청 횟수는 1회이고 결과에 기본 점수가 없어야 한다.
- [ ] 위 테스트의 RED를 확인한다. 모의 서버는 제공자 HTTP만 대체하고 파싱/검증/직렬화는 실제 제품 코드를 실행한다.
- [ ] 공식 Groq 문서에서 무료 계정으로 쓸 수 있는 구조화 출력 모델 하나를 확인한다. endpoint/model을 다른 제공자로 자동 전환하지 않는다. 연결 제한 5초·응답 제한 30초를 두고 테스트에서는 짧은 타임아웃으로 검증한다.
- [ ] system 지침과 사용자 JSON 데이터를 구분한다. 기술적 오류는 지적하고, 정보 부족만 보충 질문으로 확인하며, 실제 경험 검증을 주장하지 않는 지침을 구현한다. JSON 스키마 및 서버 검증을 모두 적용한다.
- [ ] 키 미설정은 네트워크 요청 없이 503, 인증/외부 오류는 안전한 502, rate limit은 429, 타임아웃은 504로 매핑한다. 오류 응답에 provider body를 넣지 않는다.
- [ ] `GroqServiceTest`와 전체 테스트를 실행하여 GREEN을 확인한다. 실제 키 없이도 동작해야 한다.

## Task 4: 면접 생성→평가→1회 꼬리 질문의 원자적 저장

**Files:** SessionService/AnswerService/InterviewPersistenceService, 관련 repository/controller/DTO, InterviewFlowTest/GroqStub.

**Interfaces:** `SessionService.createSession(String email, SessionCreateRequest request)`는 resumeId를 요구한다. `AnswerService.submitAnswer(String email, AnswerSubmitRequest request)`는 기존 sessionId/questionId/userAnswer 형태를 유지한다. 답변은 1~10,000자다.

- [ ] 실패 테스트: 내 이력서에서 5개 질문을 생성하고 답변 제출 시 평가를 저장/반환한다. 원문 ID 혼합·다른 사용자·기존 고정 질문 ID 제출은 404여야 한다.
- [ ] 실패 테스트: 정보 부족이면 꼬리 질문 하나, 꼬리 답변 평가는 원답을 포함하고 추가 질문은 없어야 한다. 중복 답변 409, 완료 세션 답변 거부를 검증한다.
- [ ] 실패 테스트: 생성/평가의 429·잘못된 JSON·타임아웃 뒤 세션/질문/답변 개수에 부분 증가가 없어야 한다. 재시도는 정상적으로 가능해야 한다.
- [ ] 실패 테스트: 모의 AI가 응답을 보류하는 동안 다른 요청으로 종료한 세션에 평가를 저장하지 않는다. 두 동시 답변은 하나만 저장되고 꼬리 질문도 하나다.
- [ ] RED를 확인한 뒤 읽기→AI HTTP→짧은 쓰기 트랜잭션으로 구현한다. 저장 시 세션 행 잠금을 일관된 순서로 획득하여 상태/중복을 재확인한다. 외부 호출 중에는 DB 잠금을 잡지 않는다.
- [ ] 답변/평가/꼬리 질문을 같은 트랜잭션에 저장한다. 이미 꼬리 단계인데 모델이 추가 질문을 반환하면 새 질문을 만들지 않는다.
- [ ] 동시성·실패 테스트와 전체 테스트를 실행하여 GREEN을 확인한다.

## Task 5: 전체 기록 재조회와 과거 기록·통계 회귀

**Files:** SessionController/SessionService/SessionResponse/QuestionResponse/AnswerResponse, StatsService/StatsResponse, InterviewFlowTest.

**Interfaces:** `SessionService.getSession(String email, Long id): SessionResponse`, `GET /api/sessions/{id}`. 상세는 기본 질문의 순서와 부모 관계, 저장된 원답/초기 평가/꼬리 답변/종합 평가를 반환한다.

- [ ] 실패 테스트: 저장 직후 반환값이 아닌 새 HTTP 조회로 이력서·질문·평가·꼬리 기록을 검증한다. 다른 사용자 상세 404, 완료 후 조회 가능을 검증한다.
- [ ] 실패 테스트: 과거 세션/카테고리/점수와 신규 category=null/score=null 기록을 혼합해 목록·상세·통계가 정상 동작해야 한다. 신규 평가가 기존 평균을 0이나 기본점수로 오염시키면 실패해야 한다.
- [ ] RED를 확인한 뒤 null-safe DTO와 상세 쿼리를 구현한다. 통계 응답에 숫자 평균의 집계 대상이 점수가 있는 과거 답변임을 표시한다.
- [ ] 가입→로그인→이력서→세션→원답→꼬리 답변→상세→완료→재조회 전체 HTTP 시나리오와 전체 테스트를 실행한다.

## Task 6: 실제 연동·문서·최종 검증

**Files:** README, 예제 설정, 마이그레이션 안내, 테스트 fixtures, 필요 시 opt-in `GroqLiveTest.java`.

- [ ] README를 이력서 기반 흐름으로 갱신한다. API 요청/응답, 평가 한계, 외부 전송, 무료 플랜 한도, 오류 코드, 기록 보존/마이그레이션 절차를 문서화한다. 신규 코드에서 사용하지 않는 ClaudeService/키 설정만 제거한다.
- [ ] 실제 호출 전 키를 채팅에 요청하지 말고, 사용자가 로컬 비밀 설정에 준비한 키의 존재만 확인한다. 무료 플랜과 모델 접근 확인을 받는다. 과금 위험을 계정 설정 밖에서 보장할 수 없음을 명시한다.
- [ ] 키와 무료 계정이 확인되면 가상 이력서로 생성 및 평가를 실제 호출한다. 비정석이지만 타당한 답변/근거 부족/명백한 오류 3개 샘플을 검토하고, 필요한 꼬리 답변까지 확인한다. 모델 문구나 점수를 고정 정답으로 테스트하지 않는다.
- [ ] 키가 없어도 나머지 구현/모의 테스트/문서는 마친다. 실제 연동 증거가 없으면 Goal 완료로 보고하지 않고 해당 검증만 명확히 남긴다.
- [ ] `./mvnw -o -Dspring.profiles.active=test test` 및 `./mvnw -o -DskipTests package`, `git diff --check`를 실행한다. 기존/신규 기능 및 실패·동시성 전체 테스트 통과와 패키징 성공을 확인한다.
- [ ] 새 검토 에이전트로 전체 변경을 검토한다. 중요한 지적은 회귀 테스트 RED→GREEN으로 수정하고 전체 테스트를 재실행한다.
- [ ] 설계 10절 각 완료 조건의 실제 증거를 기록한다. MySQL 스키마를 검증하지 않았다면 명시한다. 실제 이력서→생성→평가→조회 흐름까지 검증된 후에만 Goal 완료를 판단한다.

## 계획 자체 검토

- 스펙 범위: Task 1 저장·권한, Task 2 데이터 보존, Task 3 생성·평가, Task 4 꼬리·실패·동시성, Task 5 재조회·회귀, Task 6 실제 연동·완료 증거로 대응한다.
- 공통 계약: 기본 5개/질문별 꼬리 1회/신규 숫자 점수 없음/단일 Groq는 모든 task에 동일하게 적용한다.
- Review Focus의 5개 조건을 각 task의 실패 테스트에 포함했다.
- 현재 기준선: 기존 테스트 1개 통과. 실제 Groq 키는 확인한 환경에 없으므로 실호출은 아직 불가능하다.
- 사용자는 작업별 서브에이전트 구현과 별도 워크트리를 선택했다. 앱에서 생성한 resume-interview-coach 워크트리(분리 HEAD)에서 수행하며 원본 main은 변경하지 않는다.

## Task 7: 실제 모델에서 발견한 꼬리 스키마·평가 기준 보정 및 최종 실제 검증
+
+**발견 근거:** 최초 실제 실행 3회 중 생성/초기 평가 성공, 종합 평가 502. 사용자가 추가 진단 1회+최종6회를 승인했다. 동일 문맥의 단발 진단은 `GroqService.evaluate:126`에서 모델이 종합 평가에 null 아닌 두 번째 꼬리를 반환했음을 재현했다. 실제 생성 기준에는 이력서에 없는 Redis 명령 사용을 필수로 전제하는 항목도 있었다.
+
+**Files:** GroqInterviewContract/GroqService/GroqServiceTest, 관련 README·live fixture·계획의 검증 기록.
+
+**Interfaces:** 기존 DTO/API는 변경하지 않는다. 원답은 string/null 꼬리 스키마, 꼬리 답변 평가에서는 null 전용 필드를 가진 별도 strict schema를 전송한다. 기존 클라이언트의 2차 꼬리 거부는 유지한다. 기준 버전은 `resume-interview-v2`로 올리고 이미 저장한 v1 기록은 변경하지 않는다.
+
+- [ ] 실제 요청 payload를 검사하는 회귀 테스트를 먼저 RED로 확인한다. 원답/꼬리 문맥에 따라 외부 스키마가 다르고, 꼬리의 followUpQuestion은 null만 허용해야 한다.
+- [ ] Groq strict JSON Schema 공식 문서의 지원 형식을 확인하고 최소 조건부 스키마를 구현한다. 잘못된 실제 응답을 조용히 버리거나 검증을 완화하지 않는다.
+- [ ] 관찰된 실제 품질 실패를 근거로 생성·평가 지침을 보완한다. 이력서에 없는 저장소/명령/알고리즘을 전제하지 않고 특정 구현·대안 비교·측정 수치를 정답 필수조건으로 강제하지 않는다. 질문별 기준은 개방형 관찰 기준으로 유지한다. 이력서에 없는 사실을 대신 만들어 주지 않는다.
+- [ ] 전체 오프라인 테스트와 패키징 통과 후 독립 Task7 리뷰를 받는다. 정확한 문구를 검사하는 테스트를 모델 의미 검증으로 가장하지 않는다.
+- [ ] 남은 사용자 허용6회 안에서 최종 실제 검증을 단 한 번 진행한다. 최초 생성→원답→선택적 꼬리→상세 조회/완료, 통제된 Redis 답변3종의 평가를 검증한다. 실패 시 즉시 중단하고 횟수를 보고한다. 자동 재시도하지 않는다.
+- [ ] 실제 생성 질문·관찰 기준 및 비교 답변 평가의 의미를 사람이 검토한다. 초기 실패와 후속 성공의 모델/기준버전을 구분하고, 실제 전체 흐름이 입증된 후에만 Goal 완료를 판단한다.
+
+**전체 범위 유지:** Task6의 실연동 완료 조건을 삭제하지 않고 이 Task7의 보정·재검증으로 이어서 수행한다. 문서/오프라인 테스트 성공이나 실패를 안전하게 처리했다는 사실만으로 완료하지 않는다.

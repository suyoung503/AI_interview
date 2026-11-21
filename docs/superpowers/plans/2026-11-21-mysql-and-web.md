# MySQL 검증 및 웹 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** MySQL 8의 실제 저장 계약을 검증하고 브라우저로 사용 가능한 한국어 면접 웹사이트를 추가한다.
**Architecture:** 격리된 MySQL 8과 로컬 AI HTTP 스텁으로 검증한다. 기존 Spring 정적 자원과 API를 연결한다.
**Tech Stack:** Java 17+, Spring Boot, MySQL 8, HTML/CSS/ES modules, Node 내장 test.
**Spec:** docs/superpowers/specs/2026-11-21-mysql-and-web-design.md

## Global Constraints
- 기존 DB/볼륨 및 비공개 application.yml/.env를 변경하거나 출력하지 않는다.
- 실제 Groq 추가 호출은 0회. 승인된 기존 API 계약을 유지한다.
- 단계별 검증 후 별도 커밋. 사용자 요청 없는 push/merge는 하지 않는다.

## Review Focus
- 비전용 DB URL은 거부하고 기본 테스트에서 MySQL 테스트를 건너뛴다.
- 구형 데이터/중복 답변 보존 및 20,000 보조 평면 문자 저장.
- 외부 문자열 HTML 실행 금지와 인증 만료/로그아웃 후 늦은 응답 무시.
- AI 오류 뒤 답변 보존 및 자동 재시도/중복 제출 없음.
- 작은 화면에서 질문/꼬리/평가/기록 모두 접근 가능.

## Task 1: MySQL 8 검증 및 첫 커밋
**Files:** src/test/java/com/interview/MySqlIntegrationTest.java,
src/test/resources/mysql/legacy.sql, scripts/verify-mysql.sh,
src/main/resources/db/migration/README.md, docs/superpowers/verification/2026-11-21-mysql.md.
**Interfaces:** MySqlIntegrationTest inherits existing HTTP-stub/JWT InterviewFlowTest tests;
MYSQL_VERIFICATION_URL/USERNAME/PASSWORD are explicit test-only environment inputs.
- [ ] 전용 테스트 DB만 허용하는 opt-in 테스트와 구형 스키마 fixture 작성.
- [ ] 실제 MySQL 8에서 기존 수동 SQL 적용, schema validate 및 legacy/Unicode/흐름 테스트 실행.
- [ ] 실패가 있다면 원인 재현 후 필요한 최소 수정. H2 전체 회귀 검증.
- [ ] 컨테이너 정리, 재현 방법/증거 문서화 후 Task 1 커밋.

## Task 2: 웹사이트 및 두 번째 커밋
**Files:** src/main/resources/static/{index.html,styles.css,app.js,api.js},
src/main/java/com/interview/security/SecurityConfig.java,
src/test/java/com/interview/WebAccessTest.java, web-tests/*.test.mjs, README.md.
**Interfaces:** existing /api/auth, /api/resumes, /api/sessions, /api/answers JSON DTOs.
- [ ] 공개 정적 경로/보호 API 테스트를 먼저 추가하고 403 실패 확인.
- [ ] 한국어 화면과 메모리 토큰 기반 API 클라이언트 구현. 외부 문자열은 textContent 사용.
- [ ] Node 테스트로 인증/오류/늦은 응답 경계 검증. Spring/H2 전체 테스트 및 패키징.
- [ ] 로컬 AI 스텁으로 브라우저 데스크톱/모바일 면접 전체 흐름, 오류/로그아웃 확인.
- [ ] 독립 리뷰와 수정 후 웹 증거 기록, Task 2 커밋.

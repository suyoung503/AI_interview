# 코드·파일 구조 정리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan as one cohesive refactoring task.

**Goal:** 기능을 유지하면서 중복 DTO와 사용 불가능한 테스트를 줄이고 코드·설정 경로를 정돈해 기존 작업 브랜치에 일반 푸시한다.
**Architecture:** 기존 controller/service/entity/repository 및 AI 호출과 저장 트랜잭션의 경계를 유지한다. 응집력 있는 요청 DTO만 묶고 실제 AI 검증 코드는 테스트 전용 패키지로 모은다.
**Tech Stack:** 기존 Java 17+, Spring Boot, JPA/MySQL, HTML/CSS/JS, Node 내장 테스트.
**Spec:** 아래의 사용자 승인된 짧은 설계. 기존 API와 DB 계약은 2026-11-20 이력서 면접 설계 및 현재 회귀 테스트를 따른다.

## 승인된 설계
2026-11-21 사용자는 다음 범위에 승인했다: 인증·면접 요청 DTO 통합, 깨진 구형 진단/중복 부팅 테스트 제거,
실제 AI 검증 코드·테스트 설정 경로 정리, 개인 설정 로컬 보존·Git 추적 제외,
회귀 후 기존 `codex/resume-interview-web` 브랜치에 일반 푸시. 기능·DB·웹 화면과 과거 커밋은 유지한다.

대안은 전체를 기능별 패키지로 재편하거나 모든 작은 클래스를 한 파일로 합치는 것이었다.
둘 다 필요한 경계를 흐리거나 대량 경로 변경을 만들므로 채택하지 않는다. 파일 수 자체보다 중복 제거와 탐색성을 우선한다.

## Global Constraints
- 실제 `.env`와 `src/main/resources/application.yml` 내용을 읽거나 출력하지 않는다. 로컬 파일을 삭제하지 않는다.
- 개인 application.yml은 Git 추적만 제거하고 ignore를 유지한다. 과거 커밋은 재작성하지 않는다.
- 기존 HTTP 필드·상태, strict ID/문자열 검증, 소유권, 잠금/원자성, AI 스키마·평가 정책을 바꾸지 않는다.
- 실제 Groq 추가 호출 0회. 기능·웹 화면·DB 마이그레이션은 추가하거나 제거하지 않는다.
- 새 코드는 더 쪼개지 않는다. 기존 기본 테스트·실제 MySQL 검사·Node 회귀를 유지한다.

## Review Focus
- 중첩 DTO에서도 Jackson setter, Bean Validation, 20k/10k 코드 포인트 제한 및 ID 강제변환 거부가 그대로인가.
- live 패키지 이동 후 Spring 부트스트랩·조건부 활성화·요청 횟수/재시도 안전장치가 유지되는가.
- 테스트 YAML이 배포 JAR에서 사라지고 테스트 classpath에서는 발견되는가.
- 개인 설정 추적 제거 후 로컬 파일이 그대로이고, 신규 clone 실행 안내가 유효한가.
- 삭제/이동한 파일의 import·스크립트·README 및 테스트 참조가 남지 않는가.

## Task 1: 응집력 있는 단일 정리 작업
**Files and interfaces:**
- `dto/AuthModels.java`: 동일한 회원가입/로그인 Credentials와 TokenResponse를 묶는다.
  기존 LoginRequest/SignupRequest/LoginResponse를 제거하고 AuthController/AuthService의 타입만 변경한다.
- `dto/InterviewRequests.java`: ResumeCreateRequest/SessionCreateRequest/AnswerSubmitRequest 및 strict 정수 변환을 묶는다.
  기존 세 요청 파일과 RequestIds를 제거한다. controller/service import만 갱신하고 외부 JSON은 유지한다.
- `src/test/java/com/interview/live/`: GroqLiveTest, GroqLiveClient(+Test), GroqLiveEvidence(+Test),
  GroqDiagnosticSupport, GroqTechnicalFeedbackProbeTest를 이동한다. 삭제된 scratch 경로를 읽는
  GroqFollowUpDiagnosticTest와 빈 contextLoads만 있는 InterviewApplicationTests는 제거한다.
- `src/test/resources/application-test.yml`: main/resources에서 이동. public example은
  `config/application.yml.example`로 이동하고 실제 `config/application.yml`은 ignore한다.
- `src/main/resources/application.yml`: `git rm --cached`만 사용하여 로컬 파일은 보존한다.
- SessionService/SessionResponse/UserRepository 등의 죽은 설명 코드와 실제 사용하지 않는 import만 정리한다.
  repository 메서드는 테스트에서 쓰는 것까지 확인해 보존한다.
- README/기존 검증 문서: 현재 경로와 실행법 갱신. 과거 증거·숫자는 당시 결과로 명확히 유지한다.

- [x] baseline 전체 테스트를 기록한다 (161 집계,140 통과/21 opt-in skip).
- [x] 기존 테스트 파일에 인증 JSON 계약 회귀를 보강하고 리팩터링 전 성공을 확인한다.
- [x] 위 DTO·경로·정리 변경을 수행한다. private config 삭제 diff 내용은 출력/리뷰하지 않는다.
- [x] stale target 리소스를 제거한 fresh build에서 전체 H2/로컬HTTP 테스트, Node11, 패키징을 확인한다.
- [x] 자체 일회용 컨테이너의 MySQL8 검사39건을 다시 실행하고 정리한다.
- [x] 파일 수 전후, 삭제/이동 목록, 로컬 private 설정 보존, JAR 자원 및 참조를 확인하고 커밋한다.
- [ ] 독립 리뷰 후 일반 push, 원격 HEAD 일치 및 삭제 경로 부재를 확인한다. main/과거 커밋은 보존한다.

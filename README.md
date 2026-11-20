# 이력서 기반 기술 면접 코치

이력서의 기술 경험에서 기본 질문 5개를 만들고, 답변의 기술 정확성·추론·명료성을 따로 평가하는 Spring Boot 백엔드입니다. 답변 근거를 더 확인해야 하면 기본 질문당 꼬리 질문을 최대 한 번 제시합니다. 새 면접에는 숫자 점수가 없고, 이전 카테고리형 면접 기록과 점수는 유지합니다.

**실행 환경:** Java 17 이상, Spring Boot, Spring Security/JWT, JPA, MySQL 8.0. 자동화 테스트는 H2와 루프백 AI 스텁을 사용합니다.

## API

가입·로그인 외에는 `Authorization: Bearer <token>`이 필요합니다. 본인의 이력서와 세션만 접근할 수 있습니다.

| 요청 | 입력 | 성공 응답 |
| --- | --- | --- |
| `POST /api/auth/signup` | `{"email":"coach@example.test","password":"example-password"}` | 201 `{"token":"…"}` |
| `POST /api/auth/login` | 같은 자격 증명 | 200 `{"token":"…"}` |
| `POST /api/resumes` | `{"text":"가상 도서관 서비스에서 Redis 조회 캐시를 설계했습니다. …"}` | 201 `{id, text, createdAt}` |
| `GET /api/resumes` / `GET /api/resumes/{id}` | — | 200 본인 이력서 메타데이터 목록 / 원문 포함 상세 |
| `POST /api/sessions` | `{"resumeId":1}` | 201 기본 질문 5개가 포함된 세션 |
| `POST /api/answers` | `{"sessionId":1,"questionId":12,"userAnswer":"조회 지연 허용 범위와 장애 비용을 비교했습니다."}` | 201 저장된 원답과 초기 평가, 필요 시 꼬리 질문 |
| `GET /api/sessions` / `GET /api/sessions/{id}` | — | 200 본인 세션 목록 / 이력서·질문·답변 상세 |
| `PATCH /api/sessions/{id}/done` | — | 200 상태가 `DONE`인 세션. 세션을 완료해도 미답 상태와 기존 기록은 유지 |
| `GET /api/categories` | — | 200 기존 카테고리 `[{id, name}]` |
| `GET /api/stats` | — | 200 `{totalSessions, completedSessions, overallAverageScore, categoryStats, averageScoreScope}` |

- **이력서:** 공백만으로 이루어지지 않은 문자열, 최대 20,000 유니코드 코드 포인트입니다. 원문의 공백·개행을 보존합니다. 수정 API 대신 새 이력서를 등록하며, 기존 세션은 당시 저장된 원문을 계속 참조합니다. 이력서 목록은 `id, createdAt`만 반환합니다.
- **세션:** `id, categoryName, status, createdAt, questions, resumeId, model, criteriaVersion`을 반환합니다. 생성에는 양의 정수 `resumeId`가 필요합니다(`categoryId`로 새 면접을 만들 수 없음). 생성 결과의 각 질문은 `id, content, categoryName, position, sourceQuote, intent, evaluationCriteria, parentQuestionId`를 포함합니다. `sourceQuote`는 이력서에서 직접 인용한 부분입니다. 버전은 `resume-interview-v1`, 모델은 `openai/gpt-oss-120b`입니다.
- **답변:** 양의 정수 ID 두 개와 공백만으로 이루어지지 않은 `userAnswer`(최대 10,000 코드 포인트)를 보냅니다. 응답 필드는 `id, questionContent, userAnswer, feedback, score, createdAt, evaluation, evaluationStage, model, criteriaVersion, followUpQuestion`입니다. 신규 `feedback`과 `score`는 null입니다. `evaluation` 예시는 아래와 같습니다.

```json
{
  "technicalAccuracy": "NOT_ASSESSABLE",
  "reasoning": "INSUFFICIENT",
  "clarity": "CLEAR",
  "strengths": [],
  "improvements": ["조회 지연 허용 범위와 장애 시 동작을 설명해 보세요."],
  "uncertainties": ["선택한 캐시가 여러 인스턴스에서 필요한 이유"],
  "summary": "선택을 설명했지만 요구사항과의 연결은 아직 불분명합니다.",
  "followUpQuestion": "조회가 지연되어도 되는 범위는 어느 정도였나요?"
}
```

`technicalAccuracy`는 `CORRECT` / `INCORRECT` / `NOT_ASSESSABLE`, `reasoning`은 `SUFFICIENT` / `INSUFFICIENT` / `INVALID`, `clarity`는 `CLEAR` / `UNCLEAR`입니다. 응답 최상위의 `followUpQuestion`은 문장이 아닌 **저장된 질문 요약 객체**(없으면 null)입니다. 꼬리 질문이 있다면 같은 세션에서 그 객체의 `id`를 `questionId`로 제출합니다. 기본 답변은 `INITIAL`, 꼬리 답변은 원답까지 고려한 `COMPREHENSIVE` 평가입니다. 종합 평가가 초기 평가를 덮어쓰거나 두 번째 꼬리를 생성하지 않습니다.

세션 목록은 이력서 본문을 반환하지 않습니다. 상세는 이력서형에서만 `resume: {id, text, createdAt}`을 포함하고, `questions` 배열에 기본 질문 바로 뒤에 꼬리를 배치합니다. 꼬리의 `parentQuestionId`는 기본 질문 ID입니다. 각 질문의 `answers`가 빈 배열이면 아직 답하지 않은 상태입니다. 이미 완료한 세션도 상세를 조회할 수 있지만 답변을 새로 제출할 수 없습니다.

### 오류

컨트롤러가 처리한 오류는 `{"status":429,"message":"면접 AI 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."}` 같은 형태입니다. 아래의 이름은 서버 내부 코드로, 응답에 별도 `code` 필드는 없습니다.

| 상태 | 내부 코드 또는 상황 |
| --- | --- |
| 400 | `INVALID_INPUT` (잘못된 JSON, 필수 입력 누락, 잘못된 ID/길이), `SESSION_ALREADY_DONE` |
| 401 / 403 | 로그인 실패 또는 인증/권한 거부. 인증 없이 이력서 API는 401, 기존 보호 경로는 403이며 보안 필터 응답 형식은 다를 수 있음 |
| 404 | `RESUME_NOT_FOUND`, `SESSION_NOT_FOUND`, `QUESTION_NOT_FOUND` 등. 타인 이력서·세션은 존재하지 않는 것과 같은 응답 |
| 409 | `ANSWER_ALREADY_EXISTS` (같은 질문의 중복 제출), `EMAIL_ALREADY_EXISTS` |
| 422 | `RESUME_INSUFFICIENT_FOR_INTERVIEW`: 구체적인 기술·경험이 부족하다고 제공자가 정상 응답한 경우 |
| 429 | `AI_RATE_LIMITED`: Groq의 요청 또는 토큰 한도에 도달 |
| 502 | `AI_BAD_RESPONSE`: 제공자 인증 실패(예: 401), 거부·잘림·형식 위반, 기타 제공자/통신 오류 |
| 503 | `AI_NOT_CONFIGURED`: Groq 키가 없음 |
| 504 | `AI_TIMEOUT`: 연결·응답 시간이 초과 |

AI 실패는 정상 피드백이나 임의 점수로 대체하지 않으며, 실패한 세션/질문 또는 새 답변/꼬리 질문을 일부만 저장하지 않습니다. 애플리케이션은 AI 오류를 자동으로 재시도하거나 다른 제공자로 전환하지 않습니다. 입력이나 키, 한도 문제를 해결한 뒤 필요할 때만 다시 제출하세요.

### 평가와 개인정보

- 평가 결과는 면접 연습용 참고 자료이지 사실 증명, 합격 기준, 채용 판단이 아닙니다. 일정·팀 경험 같은 현실적 제약도 요구사항과 선택 근거가 연결되면 타당할 수 있습니다. 근거가 부족한 경우와 명백한 기술 오류를 구분하도록 했지만 모델이 틀리거나 편향될 수 있습니다. 인용문이 원문에 있다는 검사는 질문의 의미적 관련성까지 보장하지 않습니다.
- 이력서 저장·기록 조회 자체는 AI를 호출하지 않습니다. 질문을 만들 때는 **이력서 원문**이 Groq로 전송됩니다. 답변 평가에는 질문, 관련 인용문·평가 기준, 답변이 전송되며, 꼬리 평가에는 원답과 초기 평가도 함께 전송됩니다. OpenAI Platform 키나 별도 OpenAI 계정은 사용하지 않습니다.
- 가상 또는 비식별 텍스트를 권장합니다. 이름을 지워도 기술 경험으로 재식별될 수 있으므로 연락처, 내부 코드·문서, 회사 기밀, 키 등은 입력하지 마세요. 제공자의 조건은 [Groq 데이터 처리 안내](https://console.groq.com/docs/your-data)를 확인하세요. 원문과 평가 결과는 앱 DB에도 남습니다. 프롬프트는 입력 안의 명령을 참고 데이터로 취급하도록 하지만 평가 조작을 완전히 막지는 못합니다.
- Groq의 무료 여부는 계정·조직 플랜으로 정해지며 이 앱이 무과금이나 모델 접근을 보장하지 않습니다. 호출 전에 콘솔에서 무료 플랜, 해당 모델 접근, 분·일별 요청 및 토큰 잔여량을 확인하세요. 한도는 조직 단위이며 앱 외의 사용량도 영향을 줍니다. [Groq 사용 한도](https://console.groq.com/docs/rate-limits), [모델 접근 설정](https://console.groq.com/docs/model-permissions)

## 로컬 설정과 실행

1. Java와 MySQL을 준비합니다. 빈 로컬 DB가 필요하다면 `CREATE DATABASE interview_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`로 만들 수 있습니다. MySQL의 기본 포트는 3306입니다.
2. 프로젝트 루트에서 비밀 파일을 준비합니다. 아래 명령은 **새 파일만** 만들고 기존 파일을 덮어쓰지 않습니다.

   ```bash
   if [ ! -e .env ]; then
     (umask 077; set -C; cat .env.example > .env)
   fi
   mkdir -p config
   if [ ! -e config/application.yml ]; then
     (set -C; cat src/main/resources/application.yml.example > config/application.yml)
   fi
   ```

3. 로컬 편집기로 `.env`의 `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`(32바이트 이상 무작위 값), `GROQ_API_KEY`를 설정합니다. `.env`는 암호화되지 않으므로 소유자만 읽도록 `chmod 600 .env`로 제한하세요. 실제 키를 채팅, 커밋, `.env.example`, 빌드 설정/이미지, 로그 또는 스크린샷에 넣지 마세요. 저장소의 `src/main/resources/application.yml`은 추적되므로 배포 JAR에서 제외되더라도 비밀값을 넣지 마세요.
4. `.env`는 **실행 작업 디렉터리** 기준으로 Spring이 Java properties 파일로 읽습니다. 한 줄에 `이름=값`을 쓰고 따옴표, `export`, 인라인 주석, 쉘 확장은 쓰지 마세요. 줄 전체 주석은 `#`로 시작합니다. 쉘에서 `.env`를 실행하거나 `source`하지 않습니다. 이미 내보낸 같은 이름의 환경 변수가 로컬 파일보다 우선하므로, 예를 들어 내보낸 빈 `GROQ_API_KEY`도 파일 값을 덮을 수 있습니다. 선택적으로 `INTERVIEW_ENV_FILE`에 별도 파일 경로를 지정할 수 있습니다.
5. **데이터가 없는 새 로컬 DB에만** 첫 실행에서 `SPRING_JPA_HIBERNATE_DDL_AUTO=update SPRING_SQL_INIT_MODE=always ./mvnw spring-boot:run`으로 테이블과 기존 카테고리 시드를 준비합니다. 이후에는 예제의 기본값(`validate`, SQL init `never`)으로 프로젝트 루트에서 `./mvnw spring-boot:run`을 실행합니다. **기존 데이터가 있다면 첫 실행 옵션을 사용하지 말고** 아래 마이그레이션 절차를 먼저 따르세요.

### 배포 JAR와 Docker

`./mvnw spring-boot:run`은 기존 클래스패스의 `src/main/resources/application.yml`을 계속 사용할 수 있지만, `./mvnw -DskipTests package`로 만든 배포 JAR에는 해당 파일을 넣지 않습니다. `java -jar`로 실행할 때는 런타임 환경 변수(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION`[밀리초], `GROQ_API_KEY` 및 필요한 스키마·초기화 설정)를 주입하거나, 예제를 바탕으로 JAR 밖에 설정을 준비해 `--spring.config.additional-location=file:/absolute/path/application.yml`로 지정하세요. JAR도 작업 디렉터리의 `.env`를 읽을 수 있지만, 배포할 파일에는 비밀을 넣지 않고 실행 환경에서만 제공하세요.

Docker 빌드 컨텍스트에는 Dockerfile과 새로 패키징한 `target/interview-0.0.1-SNAPSHOT.jar`만 허용됩니다. Compose는 루트 `.env` 값을 이미지에 복사하지 않고 컨테이너 실행 시 환경으로 전달합니다. 새 빈 MySQL 볼륨의 첫 시작에만 `SPRING_JPA_HIBERNATE_DDL_AUTO=update SPRING_SQL_INIT_MODE=always docker compose up --build`를 사용하세요. 기존 볼륨은 마이그레이션을 먼저 검증한 다음 기본 `docker compose up --build`를 사용합니다. 볼륨을 삭제하는 `down -v`는 기록을 보존해야 할 때 사용하지 마세요.

### 기존 기록과 MySQL 마이그레이션

기존 DB는 쓰기를 중지하고 백업합니다. [MySQL 수동 마이그레이션 안내 및 SQL](src/main/resources/db/migration/README.md)을 따라 복원한 별도 DB에서 검증한 뒤 적용하세요. 자동으로 실행되지 않으며 기존 DB에서 `ddl-auto=update`를 먼저 실행하면 안 됩니다. 적용 전후 기존 행 수·점수 및 관계를 비교하고 `ddl-auto=validate`로 확인한 후 쓰기를 재개합니다. H2 테스트는 MySQL의 실제 마이그레이션 검증을 대체하지 않습니다.

구형 세션은 `resumeId`가 null이고 상세에 원본 이력서가 없습니다. 해당 세션에서 답변한 기존 고정 질문과 원답·피드백·점수·중복 제출을 그대로 보여 줍니다. 답변하지 않았던 카테고리 질문을 과거 기록에 덧붙이지 않으며 구형 기록에 신규 답변을 추가할 수 없습니다. 통계에서 `averageScoreScope`는 `SCORED_LEGACY_ANSWERS`입니다. 평균은 점수 있는 구형 답변만 집계하고 해당 점수가 없으면 0.0입니다. 세션 수에는 신규 세션도 포함합니다.

## 테스트

```bash
./mvnw -o -Dspring.profiles.active=test test
./mvnw -o -DskipTests package
```

Maven 캐시에 의존성이 아직 없다면 처음에는 `-o` 없이 내려받습니다. 일반 테스트에서는 실제 `.env`를 읽지 않고 Groq에 연결하지 않습니다. 별도 승인이 필요한 실검증과 1회 재현 진단은 기본 실행에서 건너뜁니다.

무료 플랜과 해당 모델 접근을 확인했고 **가상 데이터의 외부 전송 및 최대 6회 요청을 명시적으로 허용하는 경우에만**, 프로젝트 루트에서 아래 명령을 한 번 실행하세요. 키는 확인한 조직의 것을 사용하세요. 이 명령은 키를 인자로 받지 않으며, 다른 환경 변수가 덮어쓰지 않도록 프로젝트 루트의 `.env`를 사용합니다.

```bash
env -u GROQ_API_KEY -u INTERVIEW_ENV_FILE ./mvnw -o -Dspring.profiles.active=test \
  -Dtest=GroqLiveTest -Dgroq.live.confirmed=true -Dsurefire.rerunFailingTestsCount=0 \
  '-DargLine=-Djdk.httpclient.redirects.retrylimit=1 -Djdk.httpclient.disableRetryConnect=true -Djdk.httpclient.enableAllMethodRetry=false' test
```

H2와 JWT/MockMvc로 가상 이력서 등록→생성→원답→필요 시 꼬리 답변→기록 조회·완료를 검증하고, 같은 Redis 질문에 비정석이지만 타당한 답변·근거 부족·명백한 오류를 비교합니다. 실요청 사이에는 65초를 두고 오류가 발생하면 즉시 멈춥니다. 같은 승인으로 재실행하지 마세요. `target/groq-live-evidence.json`에서 `completed`와 실제 가상 응답을 확인하고, 질문의 관련성·평가의 정확성과 세 가지 답변의 구분은 사람이 검토해야 합니다. HTTP 성공이나 JSON 형식 검사만으로 평가 품질이 확인되는 것은 아닙니다.

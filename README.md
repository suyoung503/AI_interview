# 이력서 기반 기술 면접 코치

이력서의 기술 경험에서 기본 질문 5개를 만들고, 답변의 기술적 정확성·근거·설명력을 따로 평가하는 Spring Boot 백엔드입니다. 답변 근거를 더 확인해야 하면 기본 질문당 꼬리 질문을 최대 한 번 제시합니다. 새 면접에는 숫자 점수가 없고, 이전 카테고리형 면접 기록과 점수는 유지합니다.

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
- **세션:** `id, categoryName, status, createdAt, questions, resumeId, model, criteriaVersion`을 반환합니다. 생성에는 양의 정수 `resumeId`가 필요합니다(`categoryId`로 새 면접을 만들 수 없음). 생성 결과의 각 질문은 `id, content, categoryName, position, sourceQuote, intent, evaluationCriteria, parentQuestionId`를 포함합니다. `sourceQuote`는 이력서에서 직접 인용한 부분입니다. 질문 본문·인용문·의도는 AI가 이력서에 맞춰 생성하지만, 신규 `evaluationCriteria`는 서버가 고정한 `["기술적 정확성", "근거", "설명력"]`입니다. 새 질문과 평가는 `resume-interview-v5` 기준과 `openai/gpt-oss-120b` 모델을 사용합니다. 과거 v1/v2/v3/v4 질문을 이어서 평가해도 새 평가 요청에만 공통 3축을 적용하며, 저장된 질문 기준·평가·버전은 바뀌지 않습니다.
- **답변:** 양의 정수 ID 두 개와 공백만으로 이루어지지 않은 `userAnswer`(최대 10,000 코드 포인트)를 보냅니다. 응답 필드는 `id, questionContent, userAnswer, feedback, score, createdAt, evaluation, evaluationStage, model, criteriaVersion, followUpQuestion`입니다. 신규 `feedback`과 `score`는 null입니다. `evaluation` 예시는 아래와 같습니다.

```json
{
  "technicalAccuracy": "NOT_ASSESSABLE",
  "reasoning": "INSUFFICIENT",
  "clarity": "CLEAR",
  "strengths": [],
  "improvements": ["선택과 요구사항의 연결 설명"],
  "uncertainties": ["선택한 캐시가 여러 인스턴스에서 필요한 이유"],
  "summary": "기술적 정확성: 판단할 정보 부족. 근거: 부족함. 설명력: 명확함.",
  "followUpQuestion": "조회가 지연되어도 되는 범위는 어느 정도였나요?",
  "assessments": [
    {"axis":"TECHNICAL_ACCURACY","evidence":[{"source":"ORIGINAL_ANSWER","quote":"캐시를 선택했습니다."}],"coveredPoints":[],"improvements":[]},
    {"axis":"REASONING","evidence":[{"source":"ORIGINAL_ANSWER","quote":"캐시를 선택했습니다."}],"coveredPoints":[],"improvements":["선택과 요구사항의 연결 설명"]},
    {"axis":"CLARITY","evidence":[{"source":"ORIGINAL_ANSWER","quote":"캐시를 선택했습니다."}],"coveredPoints":[],"improvements":[]}
  ],
  "followUpAxis": "REASONING"
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

- v5의 `summary`는 AI가 판정한 세 축을 서버가 각각 요약합니다. AI는 축별 인용·충족 사항·개선점을 작성하며, 서버는 실제 답변에 있는 인용인지 확인하고 긍정인 축의 개선점은 제거합니다. `strengths`와 `improvements`는 축별 항목에서 중복을 제거해 조립합니다. 꼬리 질문은 실제 개선점이 남은 부정 축에만 허용합니다. 웹의 **판단 근거 보기**에서 원답/꼬리 답변 인용을 펼쳐 볼 수 있습니다. 구형 평가에는 신규 필드가 없어도 이전 내용 그대로 표시됩니다. 세 축 모두 긍정이면 강점은 유지하고 개선점·불확실성 목록은 비워 반환하며 꼬리 질문을 만들지 않습니다. 이는 답변의 평가이지 경험이나 수치가 검증됐다는 뜻이 아닙니다.
- 평가 결과는 면접 연습용 참고 자료이지 사실 증명, 합격 기준, 채용 판단이 아닙니다. 일정·팀 경험 같은 현실적 제약도 요구사항과 선택 근거가 연결되면 타당할 수 있습니다. 근거가 부족한 경우와 명백한 기술 오류를 구분하도록 했지만 모델이 틀리거나 편향될 수 있습니다. 인용문이 원문에 있다는 검사는 질문이나 평가의 의미적 관련성·기술적 판단의 진실성까지 보장하지 않습니다.
- 이력서 저장·기록 조회 자체는 AI를 호출하지 않습니다. 질문을 만들 때는 **이력서 원문**이 Groq로 전송됩니다. 답변 평가에는 질문, 관련 인용문·질문 의도·서버 고정 3축, 실제 답변이 전송되며, 꼬리 평가에는 원답과 초기 평가도 함께 전송됩니다. OpenAI Platform 키나 별도 OpenAI 계정은 사용하지 않습니다.
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
     (set -C; cat config/application.yml.example > config/application.yml)
   fi
   ```

3. 로컬 편집기로 `.env`의 `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`(32바이트 이상 무작위 값), `GROQ_API_KEY`를 설정합니다. `.env`는 암호화되지 않으므로 소유자만 읽도록 `chmod 600 .env`로 제한하세요. 실제 키를 채팅, 커밋, `.env.example`, 빌드 설정/이미지, 로그 또는 스크린샷에 넣지 마세요. `config/application.yml`은 Git 추적 대상이 아닙니다. 저장소에는 비밀값이 없는 `config/application.yml.example`만 둡니다.
4. `.env`는 **실행 작업 디렉터리** 기준으로 Spring이 Java properties 파일로 읽습니다. 한 줄에 `이름=값`을 쓰고 따옴표, `export`, 인라인 주석, 쉘 확장은 쓰지 마세요. 줄 전체 주석은 `#`로 시작합니다. 쉘에서 `.env`를 실행하거나 `source`하지 않습니다. 이미 내보낸 같은 이름의 환경 변수가 로컬 파일보다 우선하므로, 예를 들어 내보낸 빈 `GROQ_API_KEY`도 파일 값을 덮을 수 있습니다. 선택적으로 `INTERVIEW_ENV_FILE`에 별도 파일 경로를 지정할 수 있습니다.
5. **데이터가 없는 새 로컬 DB에만** 첫 실행에서 `SPRING_JPA_HIBERNATE_DDL_AUTO=update SPRING_SQL_INIT_MODE=always ./mvnw spring-boot:run`으로 테이블과 기존 카테고리 시드를 준비합니다. 이후에는 예제의 기본값(`validate`, SQL init `never`)으로 프로젝트 루트에서 `./mvnw spring-boot:run`을 실행합니다. **기존 데이터가 있다면 첫 실행 옵션을 사용하지 말고** 아래 마이그레이션 절차를 먼저 따르세요.

### 브라우저에서 면접 연습

Spring을 실행한 뒤 브라우저에서 [http://localhost:8080/](http://localhost:8080/)을 엽니다. 웹 화면은 같은 서버에서 제공되며 프런트엔드 빌드는 필요하지 않습니다.

1. 회원가입 또는 로그인 후 이력서 텍스트(최대 20,000자)를 저장하거나, 저장한 이력서를 선택합니다.
2. **면접 시작**을 눌러 이력서 기반 기본 질문 5개를 생성합니다. 질문을 선택하고 답변을 제출하면 평가가 표시됩니다. 꼬리 질문이 나타나면 같은 화면에서 이어서 답변할 수 있습니다.
3. **면접 기록**에서 진행 중인 면접을 이어서 연습하거나 완료한 평가를 읽을 수 있습니다. 완료한 면접에는 답변을 추가할 수 없습니다.

토큰과 미제출 초안은 현재 탭의 메모리에만 있으므로 새로고침이나 로그아웃 시 사라집니다. 제출 요청이 실패하는 동안에는 현재 탭에서 초안을 유지합니다. 답변은 저장됐지만 조회에 실패했다면 재제출 대신 **기록 새로 불러오기**를 누르세요. 이력서와 답변은 서버에 저장되고, 질문 생성과 평가에는 AI 서비스가 사용됩니다. 연락처·기밀 같은 민감한 정보는 넣지 마세요.

### 배포 JAR와 Docker

`./mvnw spring-boot:run`은 기존 로컬 `src/main/resources/application.yml`이 남아 있다면 계속 읽을 수 있지만, 이 파일은 Git과 배포 JAR에서 제외됩니다. 새 설정은 프로젝트 루트의 `config/application.yml`에 두세요. `java -jar`로 실행할 때는 런타임 환경 변수(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION`[밀리초], `GROQ_API_KEY` 및 필요한 스키마·초기화 설정)를 주입하거나, 예제를 바탕으로 JAR 밖에 설정을 준비해 `--spring.config.additional-location=file:/absolute/path/application.yml`로 지정하세요. JAR도 작업 디렉터리의 `.env`를 읽을 수 있지만, 배포할 파일에는 비밀을 넣지 않고 실행 환경에서만 제공하세요.

Docker 빌드 컨텍스트에는 Dockerfile과 새로 패키징한 `target/interview-0.0.1-SNAPSHOT.jar`만 허용됩니다. Compose는 루트 `.env` 값을 이미지에 복사하지 않고 컨테이너 실행 시 환경으로 전달합니다. 새 빈 MySQL 볼륨의 첫 시작에만 `SPRING_JPA_HIBERNATE_DDL_AUTO=update SPRING_SQL_INIT_MODE=always docker compose up --build`를 사용하세요. 기존 볼륨은 마이그레이션을 먼저 검증한 다음 기본 `docker compose up --build`를 사용합니다. 볼륨을 삭제하는 `down -v`는 기록을 보존해야 할 때 사용하지 마세요.

### 기존 기록과 MySQL 마이그레이션

기존 DB는 쓰기를 중지하고 백업합니다. [MySQL 수동 마이그레이션 안내 및 SQL](src/main/resources/db/migration/README.md)을 따라 복원한 별도 DB에서 검증한 뒤 적용하세요. 자동으로 실행되지 않으며 기존 DB에서 `ddl-auto=update`를 먼저 실행하면 안 됩니다. 적용 전후 기존 행 수·점수 및 관계를 비교하고 `ddl-auto=validate`로 확인한 후 쓰기를 재개합니다. H2 테스트는 MySQL의 실제 마이그레이션 검증을 대체하지 않습니다.

구형 세션은 `resumeId`가 null이고 상세에 원본 이력서가 없습니다. 해당 세션에서 답변한 기존 고정 질문과 원답·피드백·점수·중복 제출을 그대로 보여 줍니다. 답변하지 않았던 카테고리 질문을 과거 기록에 덧붙이지 않으며 구형 기록에 신규 답변을 추가할 수 없습니다. 통계에서 `averageScoreScope`는 `SCORED_LEGACY_ANSWERS`입니다. 평균은 점수 있는 구형 답변만 집계하고 해당 점수가 없으면 0.0입니다. 세션 수에는 신규 세션도 포함합니다.

## 테스트

경로 정리 이전 버전에서 이미 빌드했다면, 업데이트 후 최초 테스트·패키징 전에 `./mvnw -o clean`을 한 번 실행하세요. 이전 클래스와 설정이 남는 것을 막기 위한 조치입니다. `clean`은 `target/` 전체를 삭제하므로 필요한 스크린샷·검증 로그는 먼저 `target/` 밖에 복사하세요.

```bash
./mvnw -o -Dspring.profiles.active=test \
  '-Dspring.config.location=classpath:application.properties,classpath:application-test.yml' test
./mvnw -o -DskipTests package
```

Maven 캐시에 의존성이 아직 없다면 처음에는 `-o` 없이 내려받습니다. 일반 테스트에서는 실제 `.env`를 읽지 않고 Groq에 연결하지 않습니다. 테스트 설정은 `src/test/resources/application-test.yml`에만 있습니다. 별도 승인이 필요한 실제 AI 검증과 기술 교정 단발 프로브는 `src/test/java/com/interview/live/`에 있으며 기본 실행에서 건너뜁니다.

### 웹 검증과 로컬 미리보기

```bash
node --experimental-default-type=module --test web-tests/*.test.mjs
env -u GROQ_API_KEY -u INTERVIEW_ENV_FILE ./mvnw -o \
  -Dspring.profiles.active=test \
  '-Dspring.config.location=classpath:application.properties,classpath:application-test.yml' \
  -Dtest=WebAccessTest test
```

웹 미리보기에는 Python 3과 **수동 마이그레이션이 끝난, 버려도 되는 루프백 MySQL 검증 DB**가 필요합니다. 위 Spring 테스트를 실행한 뒤 `MYSQL_VERIFICATION_URL=jdbc:mysql://127.0.0.1:<포트>/ai_interview_verify_<이름>` 및 전용 DB의 `MYSQL_VERIFICATION_USERNAME`, `MYSQL_VERIFICATION_PASSWORD`를 환경에 주입하고 `scripts/preview-web.sh`를 실행하세요. 운영 DB나 운영 자격 증명을 사용하지 마세요.

[http://127.0.0.1:18080/](http://127.0.0.1:18080/)에서 가상의 계정과 이력서로 연습할 수 있습니다. 로컬 HTTP 스텁은 첫 답변에 꼬리 질문을, 꼬리 답변에 종합 평가를 반환합니다. 답변에 `[오류테스트]`를 넣으면 평가가 실패하고, `[느린테스트]`를 넣으면 응답이 늦어집니다. 저장 후 상세 조회만 실패하는 경우를 확인하려면, 답변을 제출하기 직전에 프로젝트 루트에서 `touch target/preview-fail-next-detail`을 실행하세요. 스텁은 다음 면접 상세 GET만 실패시키며 표시 파일을 제거합니다. 실제 Groq 요청은 발생하지 않으며 미리보기는 배포 JAR에 포함되지 않습니다. 종료는 Ctrl+C입니다.

[웹 검증 기록](docs/superpowers/verification/2026-11-21-web.md)의 스크린샷 경로는 검증을 실행한 로컬의 `target/` 아래이며 저장소에는 포함되지 않습니다.

### 별도 승인 후 실제 AI 검증

무료 플랜과 해당 모델 접근을 확인했고 **가상 데이터의 외부 전송 및 최대 6회 요청을 명시적으로 허용하는 경우에만**, 프로젝트 루트에서 아래 명령을 한 번 실행하세요. 키는 확인한 조직의 것을 사용하세요. 이 명령은 키를 인자로 받지 않으며, 다른 환경 변수가 덮어쓰지 않도록 프로젝트 루트의 `.env`를 사용합니다.

```bash
env -u GROQ_API_KEY -u INTERVIEW_ENV_FILE ./mvnw -o -Dspring.profiles.active=test \
  -Dtest=com.interview.live.GroqLiveTest -Dgroq.live.confirmed=true -Dsurefire.rerunFailingTestsCount=0 \
  '-DargLine=-Djdk.httpclient.redirects.retrylimit=1 -Djdk.httpclient.disableRetryConnect=true -Djdk.httpclient.enableAllMethodRetry=false' test
```

H2와 JWT/MockMvc로 가상 이력서 등록→생성→원답→필요 시 꼬리 답변→기록 조회·완료를 검증하고, 같은 Redis 질문에 비정석이지만 타당한 답변·근거 부족·명백한 오류를 비교합니다. 실요청 사이에는 65초를 두고 오류가 발생하면 즉시 멈춥니다. 같은 승인으로 재실행하지 마세요. `target/groq-live-evidence.json`에서 `completed`는 전체 절차 종료 여부입니다. 꼬리 답변의 실제 종합 평가는 `comprehensiveEvaluationPerformed: true`와 `followUpAnswer`에서 확인하세요. 꼬리가 없으면 절차가 끝나도 종합 평가는 검증되지 않은 것입니다. 질문의 관련성·평가의 정확성과 세 가지 답변의 구분은 사람이 검토해야 합니다. HTTP 성공이나 JSON 형식 검사만으로 평가 품질이 확인되는 것은 아닙니다.


## 최종 검증 기록

2026-11-21 코드·경로 정리 후 Maven 기본 회귀 **140건 통과 / 20건 건너뜀**, Node 웹 테스트 **11건 통과**, 일회용 MySQL 8 통합 검사 **39건 통과**를 확인했습니다. 배포 JAR에서 테스트·개인 YAML이 제외되었고, 이 정리에서 실제 Groq를 호출하지 않았습니다.

2026-11-21 v4 구현 당시 자동화는 **134개 통과**, 명시적 실호출 3개는 기본 비활성으로 건너뜁니다. 실제 검증은 **v3 전체 흐름 6회와 v4 기술 교정 단발 1회**를 구분하며, 조사 과정까지 합쳐 승인된 Groq 호출 15회를 사용했습니다. v4가 전체 흐름을 실모델로 다시 통과했다거나 AI 피드백이 항상 정확하다고 주장하지 않습니다.

[요구사항별 증거·남은 한계·안전한 합성 결과](docs/superpowers/verification/2026-11-21-resume-interview-coach.md)를 확인하세요. 운영 MySQL 적용과 Docker 배포는 검증하지 않았습니다.

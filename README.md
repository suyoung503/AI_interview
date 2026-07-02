# AI 기술 면접 연습 플랫폼

Spring Boot + Claude AI 기반의 백엔드 기술 면접 연습 서비스입니다.  
사용자가 면접 질문에 답변을 제출하면 Claude AI가 즉시 피드백과 점수를 제공합니다.

---

## 주요 기능

- 회원가입 / 로그인 (JWT 인증)
- 카테고리별 면접 세션 시작 (Java, Spring, 데이터베이스, 네트워크, 운영체제)
- 답변 제출 시 Claude AI 피드백 + 점수(1~10) 자동 생성
- 세션 완료 처리
- 내 면접 통계 조회 (전체 평균, 카테고리별 평균)

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| ORM | Spring Data JPA + Hibernate |
| Database | MySQL 8.0 |
| 인증 | Spring Security + JWT (JJWT 0.12.3) |
| AI | Claude API (claude-haiku-4-5-20251001) |
| 배포 | Docker + Docker Compose |

---

## 프로젝트 구조

```
src/main/java/com/interview/
├── controller/       # HTTP 요청 처리
│   ├── AuthController.java
│   ├── CategoryController.java
│   ├── SessionController.java
│   ├── AnswerController.java
│   └── StatsController.java
├── service/          # 비즈니스 로직
│   ├── AuthService.java
│   ├── SessionService.java
│   ├── AnswerService.java
│   ├── StatsService.java
│   └── ClaudeService.java
├── entity/           # JPA 엔티티
│   ├── User.java
│   ├── Category.java
│   ├── Question.java
│   ├── Session.java
│   └── Answer.java
├── repository/       # DB 접근
├── security/         # JWT 인증 필터
├── dto/              # 요청/응답 데이터 형태
└── exception/        # 예외 처리
```

---

## API 명세

### 인증
| Method | URL | 설명 | 인증 필요 |
|---|---|---|---|
| POST | `/api/auth/signup` | 회원가입 | X |
| POST | `/api/auth/login` | 로그인 | X |

### 카테고리
| Method | URL | 설명 | 인증 필요 |
|---|---|---|---|
| GET | `/api/categories` | 카테고리 목록 | O |

### 세션
| Method | URL | 설명 | 인증 필요 |
|---|---|---|---|
| POST | `/api/sessions` | 세션 시작 | O |
| GET | `/api/sessions` | 내 세션 목록 | O |
| PATCH | `/api/sessions/{id}/done` | 세션 완료 | O |

### 답변
| Method | URL | 설명 | 인증 필요 |
|---|---|---|---|
| POST | `/api/answers` | 답변 제출 + AI 피드백 | O |

### 통계
| Method | URL | 설명 | 인증 필요 |
|---|---|---|---|
| GET | `/api/stats` | 내 면접 통계 | O |

---

## 로컬 실행 방법

### 사전 준비
- Java 17
- MySQL 8.0
- Maven

### 1. 저장소 클론
```bash
git clone https://github.com/suyoung503/AI_interview.git
cd AI_interview
```

### 2. application.yml 설정
```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```
`application.yml` 을 열어서 MySQL 비밀번호, Claude API 키 입력

### 3. MySQL DB 생성
```sql
CREATE DATABASE interview_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 4. 실행
```bash
./mvnw spring-boot:run
```

---

## Docker 실행 방법

### 1. .env 파일 생성
```bash
cp .env.example .env
```
`.env` 파일에 실제 값 입력:
```
MYSQL_ROOT_PASSWORD=your_mysql_password
JWT_SECRET=your-jwt-secret-key-must-be-at-least-256-bits-long
CLAUDE_API_KEY=your_claude_api_key
```

### 2. 빌드 및 실행
```bash
./mvnw package -DskipTests
docker-compose up --build
```

### 3. 종료
```bash
docker-compose down
```

---

## 사용 예시

### 회원가입
```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@gmail.com","password":"1234"}'
```

### 로그인 후 토큰 받기
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@gmail.com","password":"1234"}'
# 응답: {"token":"eyJ..."}
```

### 면접 세션 시작 (Java)
```bash
curl -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {토큰}" \
  -d '{"categoryId":1}'
```

### 답변 제출 + AI 피드백
```bash
curl -X POST http://localhost:8080/api/answers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {토큰}" \
  -d '{"sessionId":1,"questionId":1,"userAnswer":"JVM은 Java 프로그램을 실행하는 가상 머신으로..."}'
# 응답: {"feedback":"잘 설명했습니다. 힙 영역의 GC 연관성도 언급하면 더 좋습니다.","score":8}
```

### 내 통계 조회
```bash
curl http://localhost:8080/api/stats \
  -H "Authorization: Bearer {토큰}"
```

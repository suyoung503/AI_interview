# 이력서 면접 데이터 모델: MySQL 8.0 수동 마이그레이션

[참고 SQL](2026-11-20-resume-interview.mysql.sql)은 기존 데이터의 삭제나 변환 없이 새 컬럼을 추가하는 초안이다. 앱이 자동 적용하지 않는다. 운영 변경을 `ddl-auto=update`에 맡기지 않는다. 별도 MySQL 8.0 컨테이너의 가상 구형 데이터로 이 SQL과 애플리케이션 저장 흐름을 검증했다. 운영 DB 자체의 호환성이나 백업 복원을 확인한 것은 아니다.

## 적용 전

1. 서비스 쓰기를 중지하고 대상 DB·MySQL 버전·InnoDB를 확인한다. 스키마와 데이터를 백업하고, 별도 테스트 DB에 복원하여 백업과 SQL을 먼저 검증한다. MySQL의 DDL은 암묵적으로 커밋되므로 여러 문장을 하나의 트랜잭션으로 되돌릴 수 없다. [MySQL DDL의 암묵적 커밋](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)
2. SQL의 읽기 전용 사전 점검을 실행한다. 실제 `SHOW CREATE TABLE`에 맞춰 FK 컬럼의 타입·부호 및 인덱스를 확인한다. 예시는 signed BIGINT 키와 INT 점수를 전제한다. `MODIFY COLUMN`은 기존 DEFAULT, COMMENT 등의 속성을 자동 보존하지 않으므로 차이가 있으면 검토해 적용문을 조정한다. 기존 FK를 비활성화하거나 삭제하지 않는다. [MySQL ALTER TABLE](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html), [MySQL FK 요구사항](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html)
3. DB·애플리케이션 연결에서 `utf8mb4`가 작동하고 기존 `questions.content`·`answers.user_answer`·`answers.feedback`의 문자셋이 `utf8mb4`인지 확인한다. 다르면 실제 인코딩과 기존 데이터의 변환 길이·정렬·복원 결과를 먼저 검증하고 별도 문자셋 변경을 계획한다. 이 SQL은 기존 텍스트의 인코딩을 일괄 변경하지 않는다. `resumes`가 이미 있다면 생성문을 건너뛰고 기존 사용자 FK, 생성 시각, `text MEDIUMTEXT` 및 `utf8mb4` 일치를 확인한다. 이력서 최대 20,000 코드 포인트의 UTF-8 바이트 수는 `TEXT`를 넘을 수 있다.
4. 과거 행 수·기존 점수 건수와 합을 기록한다. 동일한 질문 ID의 기존 답변이 여러 개여도 그대로 둔다. 전역 `UNIQUE(question_id)`나 과거 답변을 정리하는 DML을 실행하지 않는다. 대상 스키마가 일부 변경된 상태라면 문장 전체를 재실행하지 말고 실제 남은 변경만 다시 검토한다.

## 적용 및 검증

- 복원한 테스트 DB에서 합의한 적용문을 순서대로 시험하고, 작업 중 예상치 못한 오류가 나면 다음 DDL을 실행하지 않고 적용된 스키마와 백업을 확인한다. 운영에서는 검증한 문장만 쓰기 중지 상태에서 개별 적용한다.
- 적용 전후의 행수와 기존 점수 지표를 비교한다. 예전 카테고리·질문·세션·답변의 연결 및 내용을 표본 검사하고, 이력서와 신규 질문·평가의 한글/보조 평면 문자와 신규 NULL 카테고리·점수를 검증한다. 스키마 검증 후 애플리케이션의 Hibernate 스키마 동작은 `ddl-auto=validate`로 검증하고 재개한다.
- 신규 칼럼이 과거 행에서 NULL인 것은 의도적이다. 질문의 같은 세션/기본 질문 부모 조건은 엔티티 팩토리가 보장한다. 동시 중복 답변·꼬리 질문 방지는 새 답변 쓰기 흐름에서 세션 잠금과 함께 검증한다. SQL의 FK는 참조 대상의 존재를 보장할 뿐 이 두 비즈니스 규칙은 보장하지 않는다.
- 데이터 문제가 발견되면 쓰기 재개 전 검증한 백업에서 복원한다. 부분 실패나 재개 후 새 쓰기가 있었다면 추가 데이터 보존·복원 계획을 먼저 검토한다.

## 로컬 재현

프로젝트 루트에서 로컬 Docker 엔진을 시작한 뒤 `scripts/verify-mysql.sh`를 실행한다(Java 17+, Docker, OpenSSL 필요). 이미지와 Maven 의존성이 준비돼 있다면 `scripts/verify-mysql.sh -o`로 Maven의 오프라인 모드를 사용한다.

스크립트는 루프백 임시 포트에 MySQL 8.0 컨테이너를 만들고, 전용 빈 스키마에 가상 구형 데이터와 이 SQL을 적용한다. Hibernate는 `ddl-auto=validate`로 실행되며 AI 호출은 로컬 HTTP 스텁으로만 전달된다. 스크립트가 만든 컨테이너와 익명 볼륨만 종료 시 정리한다. 일반 Maven 테스트는 이 검증을 실행하지 않는다. 상세 결과는 [MySQL 8 검증 기록](../../../../../docs/superpowers/verification/2026-11-21-mysql.md)을 참고한다.

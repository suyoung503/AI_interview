-- MySQL 8.0 / InnoDB 수동 참고용. 전체 파일을 운영 DB에 바로 실행하지 않는다.
-- 같은 디렉터리 README의 사전 점검, 백업 및 복원 검증을 먼저 수행한다.
-- 대상 DB를 명시적으로 선택하고 아래 SELECT/SHOW 결과를 확인한 뒤, 검토한 DDL만 개별 실행한다.
SELECT DATABASE(), VERSION(), @@sql_mode, @@character_set_database,
       @@character_set_client, @@character_set_connection, @@character_set_results;

SHOW CREATE TABLE users;
SHOW CREATE TABLE categories;
SHOW CREATE TABLE sessions;
SHOW CREATE TABLE questions;
SHOW CREATE TABLE answers;
-- resumes가 이미 있으면 다음 문장도 개별 실행해 DDL 1과 대조한다.
-- SHOW CREATE TABLE resumes;

SELECT table_name, column_name, column_type, is_nullable, character_set_name, collation_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'questions' AND column_name = 'content')
    OR (table_name = 'answers' AND column_name IN ('user_answer', 'feedback'))
    OR (table_name = 'resumes' AND column_name = 'text'));

-- 실행 전후 같은 결과여야 하는 과거 데이터 지표. 실제 입력 원문은 조회하지 않는다.
SELECT (SELECT COUNT(*) FROM users) AS users,
       (SELECT COUNT(*) FROM categories) AS categories,
       (SELECT COUNT(*) FROM sessions) AS sessions,
       (SELECT COUNT(*) FROM questions) AS questions,
       (SELECT COUNT(*) FROM answers) AS answers,
       (SELECT COUNT(score) FROM answers) AS scored_answers,
       (SELECT SUM(score) FROM answers) AS score_sum;

-- 동일 질문 ID에 답변이 여러 개 있는 것은 구형 모델의 정상 데이터일 수 있다.
SELECT COUNT(*) AS question_ids_with_multiple_answers
FROM (
    SELECT question_id
    FROM answers
    GROUP BY question_id
    HAVING COUNT(*) > 1
) duplicates;

-- DDL 1. resumes가 없는 구형 DB에서만 실행한다.
CREATE TABLE resumes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    text MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_resumes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

-- DDL 2. 기존 category_id의 자료형/부호/default/FK는 보존하고 nullability만 완화한다.
ALTER TABLE sessions
    MODIFY COLUMN category_id BIGINT NULL,
    ADD COLUMN resume_id BIGINT NULL,
    ADD COLUMN model VARCHAR(100) CHARACTER SET utf8mb4 NULL,
    ADD COLUMN criteria_version VARCHAR(50) CHARACTER SET utf8mb4 NULL,
    ADD CONSTRAINT fk_interview_sessions_resume FOREIGN KEY (resume_id) REFERENCES resumes (id);

-- DDL 3. 구형 질문은 category_id/content를 유지하고 새 컬럼은 NULL로 둔다.
ALTER TABLE questions
    MODIFY COLUMN category_id BIGINT NULL,
    ADD COLUMN session_id BIGINT NULL,
    ADD COLUMN question_position INT NULL,
    ADD COLUMN source_quote MEDIUMTEXT CHARACTER SET utf8mb4 NULL,
    ADD COLUMN intent TEXT CHARACTER SET utf8mb4 NULL,
    ADD COLUMN evaluation_criteria MEDIUMTEXT CHARACTER SET utf8mb4 NULL,
    ADD COLUMN parent_question_id BIGINT NULL,
    ADD INDEX ix_questions_session_position (session_id, question_position),
    ADD CONSTRAINT fk_interview_questions_session FOREIGN KEY (session_id) REFERENCES sessions (id),
    ADD CONSTRAINT fk_interview_questions_parent FOREIGN KEY (parent_question_id) REFERENCES questions (id);

-- DDL 4. 이미 score가 nullable이면 MODIFY는 생략 가능하다. 기존 점수와 중복 답변은 보존한다.
ALTER TABLE answers
    MODIFY COLUMN score INT NULL,
    ADD COLUMN evaluation MEDIUMTEXT CHARACTER SET utf8mb4 NULL,
    ADD COLUMN model VARCHAR(100) CHARACTER SET utf8mb4 NULL,
    ADD COLUMN criteria_version VARCHAR(50) CHARACTER SET utf8mb4 NULL;

-- Synthetic, empty MySQL 8 schema representing the pre-resume model.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    status ENUM('DONE', 'IN_PROGRESS') NOT NULL,
    created_at DATETIME(6),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE answers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    user_answer TEXT,
    feedback TEXT,
    score INT NOT NULL,
    created_at DATETIME(6),
    FOREIGN KEY (session_id) REFERENCES sessions (id),
    FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

INSERT INTO users (id, email, password, created_at)
VALUES (1, 'legacy@example.test', 'synthetic-unusable-password', '2026-01-01 00:00:00');
INSERT INTO categories (id, name) VALUES (1, '서버');
INSERT INTO sessions (id, user_id, category_id, status, created_at)
VALUES (1, 1, 1, 'DONE', '2026-01-01 00:00:00');
INSERT INTO questions (id, category_id, content) VALUES (1, 1, '구형 질문 🔍');
INSERT INTO answers (id, session_id, question_id, user_answer, feedback, score, created_at)
VALUES (1, 1, 1, '첫 답변', '유지', 60, '2026-01-01 00:00:00'),
       (2, 1, 1, '두 번째 답변', '보존', 90, '2026-01-01 00:00:00');

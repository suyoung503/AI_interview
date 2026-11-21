package com.interview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(InterviewFlowTest.Config.class)
@EnabledIfEnvironmentVariable(named = "MYSQL_VERIFICATION_URL", matches = ".+")
class MySqlIntegrationTest extends InterviewFlowTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) throws SQLException {
        var url = System.getenv("MYSQL_VERIFICATION_URL");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]{1,5}/ai_interview_verify_[a-z0-9_]+")) {
            throw new IllegalArgumentException("Expected a loopback MySQL URL for ai_interview_verify_*");
        }
        var username = System.getenv("MYSQL_VERIFICATION_USERNAME");
        var password = System.getenv("MYSQL_VERIFICATION_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("MySQL verification requires explicit credentials");
        }
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            var metadata = connection.getMetaData();
            if (!"MySQL".equals(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() != 8) {
                throw new IllegalStateException("MySQL 8 is required for this verification");
            }
            try (var tables = statement.executeQuery("""
                    select count(*) from information_schema.tables where table_schema = database()
                    """)) {
                tables.next();
                if (tables.getInt(1) != 0) {
                    throw new IllegalStateException("MySQL verification requires an empty schema");
                }
            }
            new ResourceDatabasePopulator(false, false, "UTF-8",
                    new ClassPathResource("mysql/legacy.sql"),
                    new ClassPathResource("db/migration/2026-11-20-resume-interview.mysql.sql"))
                    .populate(connection);
        }

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void preservesLegacyRelationshipsTextScoresAndDuplicateAnswers() {
        assertThat(db.queryForList("""
                select u.email, c.name, s.status, q.content, a.user_answer, a.feedback, a.score
                from answers a join questions q on q.id = a.question_id
                join sessions s on s.id = a.session_id
                join users u on u.id = s.user_id
                join categories c on c.id = s.category_id and c.id = q.category_id
                where a.id in (1, 2) order by a.id
                """)).containsExactly(
                Map.of("email", "legacy@example.test", "name", "서버", "status", "DONE",
                        "content", "구형 질문 🔍", "user_answer", "첫 답변", "feedback", "유지", "score", 60),
                Map.of("email", "legacy@example.test", "name", "서버", "status", "DONE",
                        "content", "구형 질문 🔍", "user_answer", "두 번째 답변", "feedback", "보존", "score", 90));
        assertThat(db.queryForObject("select count(*) from answers where question_id = 1", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void roundTripsTwentyThousandSupplementaryCodePointsThroughResumeApi() throws Exception {
        var text = "😀".repeat(20_000);
        var created = mvc.perform(post("/api/resumes").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("text", text))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        var id = json.readTree(created).path("id").asLong();
        var retrieved = mvc.perform(get("/api/resumes/{id}", id).header("Authorization", owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(json.readTree(retrieved).path("text").asString()).isEqualTo(text);
        assertThat(db.queryForObject("select octet_length(text) from resumes where id = ?", Integer.class, id))
                .isEqualTo(80_000);
    }
}

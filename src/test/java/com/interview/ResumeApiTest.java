package com.interview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class ResumeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @ParameterizedTest
    @MethodSource("resumeRequests")
    void rejectsUnauthenticatedResumeRequests(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request).andExpect(status().isUnauthorized());
    }

    static Stream<MockHttpServletRequestBuilder> resumeRequests() {
        return Stream.of(post("/api/resumes"), get("/api/resumes"), get("/api/resumes/1"));
    }

    @Test
    void preservesUnauthenticatedStatusForExistingApi() throws Exception {
        mvc.perform(get("/api/sessions")).andExpect(status().isForbidden());
    }

    @Test
    void persistsOriginalTextAndMetadataForLaterRead() throws Exception {
        String owner = signup();
        String text = "  데이터 API \"개선\" 🚀\n2025년 프로젝트\r\n ";
        JsonNode created = create(owner, text);
        long id = created.path("id").asLong();

        assertThat(id).isPositive();
        assertThat(created.path("text").stringValue()).isEqualTo(text);
        String createdAt = created.path("createdAt").stringValue();
        assertThat(createdAt).isNotBlank();

        mvc.perform(get("/api/resumes/{id}", id).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(equalTo(id), Long.class))
                .andExpect(jsonPath("$.text").value(text))
                .andExpect(jsonPath("$.createdAt").value(createdAt));
    }

    @Test
    void hidesAnotherUsersResumeLikeAMissingResume() throws Exception {
        String owner = signup();
        String stranger = signup();
        long id = create(owner, "소유자 이력서").path("id").asLong();

        String foreign = mvc.perform(get("/api/resumes/{id}", id).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get("/api/resumes/{id}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonMapper.readTree(foreign)).isEqualTo(jsonMapper.readTree(missing));
    }

    @Test
    void listsOnlyOwnersMetadataWithoutATextProperty() throws Exception {
        String owner = signup();
        String stranger = signup();
        JsonNode own = create(owner, "목록에 나오면 안 되는 원문");
        create(stranger, "다른 사람의 이력서");

        mvc.perform(get("/api/resumes").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(equalTo(own.path("id").asLong()), Long.class))
                .andExpect(jsonPath("$[0].createdAt").value(own.path("createdAt").stringValue()))
                .andExpect(jsonPath("$[0].text").doesNotHaveJsonPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "{}", "{\"text\":null}", "{\"text\":\"\"}",
            "{\"text\":\" \\t\\n　\"}", "{\"text\":\" \u00A0\u2007\u202F\"}"
    })
    void rejectsMissingOrBlankText(String body) throws Exception {
        mvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, signup())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void acceptsAndPersistsExactlyTwentyThousandUnicodeCharacters() throws Exception {
        String owner = signup();
        String text = "🚀".repeat(20_000);
        long id = create(owner, text).path("id").asLong();

        String body = mvc.perform(get("/api/resumes/{id}", id).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonMapper.readTree(body).path("text").stringValue()).isEqualTo(text);
    }

    @Test
    void rejectsTwentyThousandAndOneUnicodeCharactersWithoutSaving() throws Exception {
        String owner = signup();
        String body = jsonMapper.writeValueAsString(Map.of("text", "🚀".repeat(20_001)));

        mvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/api/resumes").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest(name = "{index}")
    @ValueSource(strings = {
            "{\"text\":\"review-test-body-fragment\"",
            "{\"text\":{\"value\":\"review-test-body-fragment\"}}",
            "{\"text\":[\"review-test-body-fragment\"]}",
            "{\"text\":123}",
            "{\"text\":true}"
    })
    void rejectsMalformedJsonAndNonStringTextWithoutExposingInput(String body) throws Exception {
        String owner = signup();
        expectSanitizedBadRequest(mvc.perform(post("/api/resumes")
                .header(HttpHeaders.AUTHORIZATION, owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/signup", "/api/auth/login", "/api/sessions", "/api/answers"})
    void returnsSanitizedBadRequestForMalformedJsonInExistingApis(String path) throws Exception {
        MockHttpServletRequestBuilder request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"review-test-body-fragment\"");
        if (!path.startsWith("/api/auth/")) {
            request.header(HttpHeaders.AUTHORIZATION, signup());
        }

        expectSanitizedBadRequest(mvc.perform(request));
    }

    private void expectSanitizedBadRequest(ResultActions result) throws Exception {
        String response = result.andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        JsonNode expected = jsonMapper.readTree("""
                {"status":400,"message":"요청 입력값이 올바르지 않습니다."}
                """);
        assertThat(expected.equals(jsonMapper.readTree(response)))
                .as("use the standard error envelope without request or exception details")
                .isTrue();
    }

    private String signup() throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of(
                "email", UUID.randomUUID() + "@example.test",
                "password", "resume-api-test-password"));
        String response = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + jsonMapper.readTree(response).path("token").stringValue();
    }

    private JsonNode create(String token, String text) throws Exception {
        String response = mvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("text", text))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response);
    }
}

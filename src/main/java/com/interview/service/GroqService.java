package com.interview.service;

import com.interview.dto.InterviewAiModels.Clarity;
import com.interview.dto.InterviewAiModels.Evaluation;
import com.interview.dto.InterviewAiModels.EvaluationContext;
import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import com.interview.dto.InterviewAiModels.Reasoning;
import com.interview.dto.InterviewAiModels.TechnicalAccuracy;
import com.interview.exception.AiProviderException;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.interview.service.GroqInterviewContract.*;

@Service
public class GroqService {

    private static final URI ENDPOINT = URI.create("https://api.groq.com/openai/v1/chat/completions");
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Pattern SPACE = Pattern.compile("(?U)\\s+");
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .build();

    private final RestClient restClient;
    private final URI endpoint;
    private final String apiKey;
    private final Duration responseTimeout;

    @Autowired
    public GroqService(Environment environment) {
        // 환경 변수는 테스트 프로필의 YAML보다 우선하므로 테스트에서는 아예 조회하지 않는다.
        this(environment.matchesProfiles("test") ? "" : environment.getProperty("groq.api-key", ""));
    }

    public GroqService(String apiKey) {
        this(apiKey, ENDPOINT, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    public GroqService(String apiKey, URI endpoint, Duration connectTimeout, Duration responseTimeout) {
        this.apiKey = apiKey.strip();
        this.endpoint = endpoint;
        this.responseTimeout = responseTimeout;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(responseTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public String getModel() {
        return MODEL;
    }

    public String getCriteriaVersion() {
        return VERSION;
    }

    public List<GeneratedQuestion> generateQuestions(String resumeText) {
        JsonNode result = complete(GENERATION_PROMPT, GENERATION_FORMAT, Map.of("resumeText", resumeText));
        requireFields(result, Set.of("status", "questions"));
        String status = result.path("status").stringValue("");
        JsonNode questions = result.path("questions");
        if (!questions.isArray()) {
            throw badResponse();
        }
        if ("INSUFFICIENT_RESUME".equals(status) && questions.isEmpty()) {
            throw new CustomException(ErrorCode.RESUME_INSUFFICIENT_FOR_INTERVIEW);
        }
        if (!"OK".equals(status) || questions.size() != QUESTION_COUNT) {
            throw badResponse();
        }

        Set<String> seen = new HashSet<>();
        List<GeneratedQuestion> generated = new ArrayList<>();
        for (JsonNode question : questions) {
            requireFields(question, Set.of("content", "sourceQuote", "intent"));
            String content = text(question.path("content"), QUESTION_MAX);
            String quote = text(question.path("sourceQuote"), QUOTE_MAX);
            String normalized = SPACE.matcher(Normalizer.normalize(content, Normalizer.Form.NFKC))
                    .replaceAll(" ").trim().toLowerCase(Locale.ROOT);
            if (!seen.add(normalized) || !resumeText.contains(quote)) {
                throw badResponse();
            }
            generated.add(new GeneratedQuestion(content, quote, text(question.path("intent"), INTENT_MAX),
                    EVALUATION_CRITERIA));
        }
        return List.copyOf(generated);
    }

    public Evaluation evaluate(EvaluationContext context) {
        boolean comprehensive = context.followUpAnswer() != null;
        // 과거 질문의 저장된 기준은 그대로 두고, 새 평가 요청에만 현재 서버 기준을 적용한다.
        var original = context.originalQuestion();
        var question = new GeneratedQuestion(original.content(), original.sourceQuote(), original.intent(), EVALUATION_CRITERIA);
        var request = new EvaluationContext(question, context.originalAnswer(), context.initialEvaluation(),
                context.followUpQuestion(), context.followUpAnswer());
        JsonNode result = complete(comprehensive ? COMPREHENSIVE_PROMPT : INITIAL_PROMPT,
                comprehensive ? COMPREHENSIVE_FORMAT : INITIAL_FORMAT, request);
        requireFields(result, (comprehensive ? COMPREHENSIVE_FIELDS : INITIAL_FIELDS).keySet());
        String followUp = comprehensive || result.get("followUpQuestion").isNull()
                ? null : text(result.get("followUpQuestion"), QUESTION_MAX);
        return new Evaluation(
                enumValue(result.path("technicalAccuracy"), TechnicalAccuracy.class),
                enumValue(result.path("reasoning"), Reasoning.class),
                enumValue(result.path("clarity"), Clarity.class),
                textList(result.path("strengths")),
                textList(result.path("improvements")),
                textList(result.path("uncertainties")),
                text(result.path("summary"), SUMMARY_MAX),
                followUp);
    }

    private JsonNode complete(String system, Map<String, Object> responseFormat, Object data) {
        if (apiKey.isEmpty()) {
            throw new CustomException(ErrorCode.AI_NOT_CONFIGURED);
        }
        long requestStarted = System.nanoTime();
        try {
            byte[] payload = JSON.writeValueAsBytes(Map.of(
                    "model", MODEL,
                    "max_completion_tokens", 3_072,
                    "messages", List.of(Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", JSON.writeValueAsString(data))),
                    "response_format", responseFormat));
            byte[] body = restClient.post().uri(endpoint)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(payload.length)
                    // 본문 객체를 RestClient의 디버그 로그에 노출하지 않는다.
                    .body(output -> output.write(payload))
                    .exchange((request, response) -> {
                        // 먼저 닫아야 RestClient의 정리 단계에서 오류/과다 본문을 끝까지 읽지 않는다.
                        try (InputStream stream = response.getBody()) {
                            var status = response.getStatusCode();
                            if (status.value() == 429) {
                                throw new CustomException(ErrorCode.AI_RATE_LIMITED);
                            }
                            if (!status.is2xxSuccessful()) {
                                throw new AiProviderException(status.value());
                            }
                            return stream.readNBytes(MAX_RESPONSE_BYTES + 1);
                        }
                    });
            if (body.length > MAX_RESPONSE_BYTES) {
                throw badResponse();
            }
            JsonNode choices = JSON.readTree(body).path("choices");
            if (!choices.isArray() || choices.size() != 1) {
                throw badResponse();
            }
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            if (!"stop".equals(choice.path("finish_reason").stringValue(""))
                    || !"assistant".equals(message.path("role").stringValue(""))
                    || message.hasNonNull("refusal")) {
                throw badResponse();
            }
            String content = message.path("content").stringValue(null);
            if (content == null || content.isBlank()) {
                throw badResponse();
            }
            return JSON.readTree(content);
        } catch (ResourceAccessException e) {
            // 헤더 수신 후 만료되면 Spring은 응답 스트림을 닫으므로 IOException으로 나타날 수 있다.
            if (e.getMostSpecificCause() instanceof HttpTimeoutException
                    || System.nanoTime() - requestStarted >= responseTimeout.toNanos()) {
                throw new CustomException(ErrorCode.AI_TIMEOUT);
            }
            throw badResponse();
        } catch (JacksonException | RestClientException | IllegalArgumentException e) {
            // 제공자 응답, 요청 데이터 또는 인증 정보가 포함된 예외를 전달하지 않는다.
            throw badResponse();
        }
    }

    private static void requireFields(JsonNode node, Set<String> fields) {
        if (!node.isObject() || node.size() != fields.size()) {
            throw badResponse();
        }
        for (String field : fields) {
            if (!node.has(field)) {
                throw badResponse();
            }
        }
    }

    private static String text(JsonNode node, int maxLength) {
        String value = node.stringValue(null);
        if (value == null || SPACE.matcher(value).matches() || value.isEmpty()
                || value.codePointCount(0, value.length()) > maxLength) {
            throw badResponse();
        }
        return value;
    }

    private static List<String> textList(JsonNode node) {
        if (!node.isArray() || node.size() > LIST_MAX) {
            throw badResponse();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(text(item, LIST_ITEM_MAX));
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, Class<E> type) {
        try {
            return Enum.valueOf(type, node.stringValue(""));
        } catch (IllegalArgumentException e) {
            throw badResponse();
        }
    }

    private static CustomException badResponse() {
        return new CustomException(ErrorCode.AI_BAD_RESPONSE);
    }
}

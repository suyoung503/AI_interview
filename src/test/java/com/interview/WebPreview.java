package com.interview;

import com.interview.service.GroqService;
import com.interview.support.GroqStub;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WebPreview {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static void main(String[] args) throws IOException {
        String url = System.getenv("MYSQL_VERIFICATION_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]{1,5}/ai_interview_verify_[a-z0-9_]+")) {
            throw new IllegalArgumentException("Use a migrated loopback ai_interview_verify_* MySQL database");
        }
        for (String name : List.of("MYSQL_VERIFICATION_USERNAME", "MYSQL_VERIFICATION_PASSWORD")) {
            if (System.getenv(name) == null || System.getenv(name).isBlank()) {
                throw new IllegalArgumentException("Missing explicit preview credentials: " + name);
            }
        }

        HttpServer stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/chat", WebPreview::respond);
        stub.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stub.stop(0)));
        System.out.println("LOCAL PREVIEW: synthetic AI only, migrated test database, http://127.0.0.1:18080/");
        try {
            new SpringApplication(InterviewApplication.class, PreviewConfig.class).run(
                    "--spring.config.location=classpath:application.properties,classpath:application-test.yml",
                    "--spring.profiles.active=test",
                    "--spring.profiles.include=",
                    "--server.address=127.0.0.1",
                    "--server.port=18080",
                    "--spring.main.log-startup-info=false",
                    "--spring.datasource.url=" + url,
                    "--spring.datasource.username=${MYSQL_VERIFICATION_USERNAME}",
                    "--spring.datasource.password=${MYSQL_VERIFICATION_PASSWORD}",
                    "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "--spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                    "--spring.jpa.hibernate.ddl-auto=validate",
                    "--spring.sql.init.mode=never",
                    "--groq.api-key=",
                    "--preview.endpoint=http://127.0.0.1:" + stub.getAddress().getPort() + "/chat");
        } catch (RuntimeException e) {
            stub.stop(0);
            throw e;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PreviewConfig {
        @Bean
        FilterRegistrationBean<jakarta.servlet.Filter> failNextDetail() {
            var filter = new FilterRegistrationBean<jakarta.servlet.Filter>((request, response, chain) -> {
                var httpRequest = (HttpServletRequest) request;
                if (httpRequest.getMethod().equals("GET") && httpRequest.getRequestURI().matches("/api/sessions/[0-9]+")
                        && Files.deleteIfExists(Path.of("target/preview-fail-next-detail"))) {
                    var httpResponse = (HttpServletResponse) response;
                    httpResponse.setStatus(500);
                    httpResponse.setContentType("application/json");
                    httpResponse.getWriter().write("{\"status\":500}");
                    return;
                }
                chain.doFilter(request, response);
            });
            filter.addUrlPatterns("/api/sessions/*");
            return filter;
        }

        @Bean
        @Primary
        GroqService previewGroq(Environment env) {
            return new GroqService("local-preview-only", URI.create(env.getRequiredProperty("preview.endpoint")),
                    Duration.ofSeconds(1), Duration.ofSeconds(8));
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            var request = JSON.readTree(exchange.getRequestBody().readAllBytes());
            var input = JSON.readTree(request.path("messages").path(1).path("content").asString());
            String answer = input.path("followUpAnswer").isString()
                    ? input.path("followUpAnswer").asString() : input.path("originalAnswer").asString("");
            if (answer.contains("[느린테스트]")) Thread.sleep(2500);
            if (answer.contains("[오류테스트]")) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }

            Map<String, Object> result;
            if (input.path("resumeText").isString()) {
                String resume = input.path("resumeText").asString().strip();
                int length = resume.codePointCount(0, resume.length());
                if (length < 8) {
                    result = Map.of("status", "INSUFFICIENT_RESUME", "questions", Collections.emptyList());
                } else {
                    String quote = resume.substring(0, resume.offsetByCodePoints(0, Math.min(72, length))).strip();
                    var questions = List.of(
                            "이력서에서 소개한 경험에서 어떤 문제를 해결하려 했나요?",
                            "그 문제를 해결할 때 고려한 대안과 선택 기준은 무엇이었나요?",
                            "개선 결과를 어떤 방식으로 확인했나요?",
                            "예상과 다른 결과가 나온다면 원인을 어떻게 좁혀 나가겠어요?",
                            "같은 프로젝트를 다시 맡는다면 무엇을 다르게 하겠어요?"
                    ).stream().map(question -> Map.of("content", question, "sourceQuote", quote,
                            "intent", "선택과 근거를 경험에 연결하는지 확인합니다.")).toList();
                    result = Map.of("status", "OK", "questions", questions);
                }
            } else {
                boolean comprehensive = input.path("followUpAnswer").isString();
                result = new HashMap<>();
                result.put("technicalAccuracy", comprehensive ? "CORRECT" : "NOT_ASSESSABLE");
                result.put("reasoning", comprehensive ? "SUFFICIENT" : "INSUFFICIENT");
                result.put("clarity", "CLEAR");
                result.put("strengths", List.of("해결한 문제를 짧게 설명했습니다."));
                result.put("improvements", List.of("비교 기준이나 수치로 판단 근거를 설명해 보세요."));
                result.put("uncertainties", List.of("개선 전후의 조건이 같았는지는 알 수 없습니다."));
                result.put("summary", "로컬 테스트용 평가");
                if (!comprehensive) result.put("followUpQuestion", "선택이 적절했는지 확인한 지표나 비교 기준은 무엇인가요?");
            }
            byte[] body = GroqStub.wrapContent(JSON.writeValueAsString(result), "stop", null).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

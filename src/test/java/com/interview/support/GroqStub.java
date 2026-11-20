package com.interview.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GroqStub implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Response response = new Response(500, "", Duration.ZERO, false, 1);

    public GroqStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/chat/completions");
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public void respondContent(String content) {
        respondContent(content, "stop", null);
    }

    public void respondContent(String content, String finishReason, String refusal) {
        respond(200, wrapContent(content, finishReason, refusal));
    }

    public void respond(int status, String body) {
        response = new Response(status, body, Duration.ZERO, false, 1);
    }

    public void respondSlowly(String content, Duration delay, boolean sendHeadersFirst) {
        response = new Response(200, wrapContent(content, "stop", null), delay, sendHeadersFirst, 1);
    }

    public void dripBody(int status, String body, Duration interval, int chunks) {
        response = new Response(status, body, interval, true, chunks);
    }

    public static String wrapContent(String content, String finishReason, String refusal) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        message.put("refusal", refusal);
        return JSON.writeValueAsString(Map.of(
                "id", "chatcmpl-test",
                "object", "chat.completion",
                "created", 1,
                "model", "openai/gpt-oss-120b",
                "choices", List.of(Map.of("index", 0, "finish_reason", finishReason, "message", message)),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 20, "total_tokens", 30)));
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    JSON.readTree(exchange.getRequestBody().readAllBytes())));
            Response current = response;
            byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (current.status() == 307) {
                exchange.getResponseHeaders().set("Location", "/not-groq");
            }
            if (!current.sendHeadersFirst()) {
                Thread.sleep(current.delay().toMillis());
            }
            exchange.sendResponseHeaders(current.status(), body.length);
            for (int i = 0; i < current.chunks(); i++) {
                if (current.sendHeadersFirst()) {
                    Thread.sleep(current.delay().toMillis());
                }
                int start = body.length * i / current.chunks();
                int end = body.length * (i + 1) / current.chunks();
                exchange.getResponseBody().write(body, start, end - start);
                exchange.getResponseBody().flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public record Request(String method, String path, String authorization, String contentType, JsonNode body) {}
    private record Response(int status, String body, Duration delay, boolean sendHeadersFirst, int chunks) {}
}

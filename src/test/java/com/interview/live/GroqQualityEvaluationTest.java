package com.interview.live;

import com.interview.dto.InterviewAiModels.*;
import com.interview.exception.CustomException;
import com.interview.service.GroqService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;
import static org.assertj.core.api.Assertions.*;

class GroqQualityEvaluationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String HASH = "a2d6bafa3f3f02f410a923335899357e1663585e0fdad62299180c739a603dcb";
    record Expected(List<TechnicalAccuracy> technicalAccuracy, List<Reasoning> reasoning, List<Clarity> clarity) {}
    record Sample(String id, String title, EvaluationContext context, Expected expected,
                  List<String> coveredFacts, List<String> requiredFeedback, List<String> forbiddenFeedback) {}
    record Fixture(List<Sample> cases) {}

    static byte[] bytes() throws Exception {
        try (var in = GroqQualityEvaluationTest.class.getResourceAsStream("/quality/evaluation-cases.json")) {
            return Objects.requireNonNull(in).readAllBytes();
        }
    }

    @Test
    void tenFrozenCasesHaveRubricsAndFixedContexts() throws Exception {
        byte[] data = bytes();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))).isEqualTo(HASH);
        var cases = JSON.readValue(data, Fixture.class).cases();
        assertThat(cases).hasSize(10).extracting(Sample::id).doesNotHaveDuplicates();
        int comprehensive = 0;
        for (var s : cases) {
            assertThat(s.id()).matches("[a-z-]+");
            assertThat(s.title()).isNotBlank();
            assertThat(s.expected().technicalAccuracy()).isNotEmpty();
            assertThat(s.expected().reasoning()).isNotEmpty();
            assertThat(s.expected().clarity()).isNotEmpty();
            assertThat(s.coveredFacts()).isNotEmpty();
            assertThat(s.requiredFeedback()).isNotNull();
            assertThat(s.forbiddenFeedback()).isNotEmpty();
            var c = s.context();
            assertThat(c.originalQuestion().content()).isNotBlank();
            assertThat(c.originalQuestion().sourceQuote()).isNotBlank();
            assertThat(c.originalAnswer()).isNotBlank();
            if (c.followUpAnswer() == null) {
                assertThat(c.initialEvaluation()).isNull();
                assertThat(c.followUpQuestion()).isNull();
            } else {
                assertThat(c.followUpAnswer()).isNotBlank();
                assertThat(c.followUpQuestion()).isEqualTo(c.initialEvaluation().followUpQuestion()).isNotBlank();
                assertThat(JSON.readTree(data).get("cases").get(cases.indexOf(s)).at("/context/initialEvaluation").size()).isEqualTo(8);
                comprehensive++;
            }
        }
        assertThat(comprehensive).isEqualTo(2);
    }

    @Test
    void partialOrFailedRunCannotRestartOrAdvance(@TempDir Path dir) throws Exception {
        var before = new Phase(dir, "before", HASH);
        var after = new Phase(dir, "after", HASH);
        assertThatThrownBy(after::start).isInstanceOf(IllegalStateException.class);
        before.start();
        before.reserve(0, 100_000);
        before.record(0, false, Map.of("error", "UNAVAILABLE"));
        assertThatThrownBy(before::start).isInstanceOf(FileAlreadyExistsException.class);
        assertThatThrownBy(after::start).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> before.reserve(1, 200_000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completePhasesRequireSameHashSpacingAndCap(@TempDir Path dir) throws Exception {
        long now = 100_000;
        for (String name : List.of("before", "after")) {
            if (name.equals("after"))
                assertThatThrownBy(() -> new Phase(dir, name, "changed").start()).isInstanceOf(IllegalStateException.class);
            var p = new Phase(dir, name, HASH);
            p.start();
            for (int i = 0; i < 10; i++, now += 65_000) {
                int index = i;
                long early = now - 1;
                if (i > 0 || name.equals("after"))
                    assertThatThrownBy(() -> p.reserve(index, early)).isInstanceOf(IllegalStateException.class);
                p.reserve(i, now);
                p.record(i, true, Map.of("case", i));
            }
            assertThatThrownBy(() -> p.reserve(10, 9_000_000)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(p::start).isInstanceOf(FileAlreadyExistsException.class);
        }
    }

    // A run holds the directory lock throughout. CREATE_NEW, fsync'd markers survive a failed JVM:
    // even an interrupted phase with no completed response cannot be restarted.
    record Phase(Path dir, String name, String hash) {
        Phase {
            if (!Set.of("before", "after").contains(name)) throw new IllegalArgumentException("잘못된 단계");
        }
        Path file(String suffix) { return dir.resolve(name + "-" + suffix); }
        void start() throws Exception {
            if (name.equals("after")) {
                var before = new Phase(dir, "before", hash);
                require(Files.exists(before.file("started")) && Files.readString(before.file("started")).equals(hash));
                for (int i = 0; i < 10; i++) {
                    require(Files.exists(before.file(i + ".attempt"))
                            && JSON.readTree(Files.readAllBytes(before.file(i + ".json"))).path("success").asBoolean());
                }
            }
            write(file("started"), hash);
        }
        long nextTime(int index) throws Exception {
            if (index == 0 && name.equals("before")) return 0;
            Path previous = index == 0 ? dir.resolve("before-9.attempt") : file((index - 1) + ".attempt");
            return Math.addExact(Long.parseLong(Files.readString(previous)), 65_000);
        }
        void reserve(int index, long now) throws Exception {
            require(index >= 0 && index < 10 && Files.readString(file("started")).equals(hash));
            if (index > 0)
                require(JSON.readTree(Files.readAllBytes(file((index - 1) + ".json"))).path("success").asBoolean());
            require(now >= nextTime(index));
            write(file(index + ".attempt"), Long.toString(now));
        }
        void record(int index, boolean success, Object result) throws Exception {
            require(Files.exists(file(index + ".attempt")));
            write(file(index + ".json"), JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(Map.of("success", success, "result", result)));
        }
        private void write(Path path, String value) throws Exception {
            try (var channel = FileChannel.open(path, CREATE_NEW, WRITE)) {
                var buf = ByteBuffer.wrap(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                while (buf.hasRemaining()) channel.write(buf);
                channel.force(true);
            }
            try (var directory = FileChannel.open(dir, READ)) { directory.force(true); }
        }
        private static void require(boolean allowed) {
            if (!allowed) throw new IllegalStateException("실행 기록·예산·간격 조건이 맞지 않습니다.");
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "groq.quality.confirmed", matches = "true")
    void evaluateFrozenCases() throws Exception {
        GroqLiveEvidence.requireHttpGuards();
        GroqLiveEvidence.requireProbeGuards(System.getProperties());
        tenFrozenCasesHaveRubricsAndFixedContexts();
        var cases = JSON.readValue(bytes(), Fixture.class).cases();
        Path dir = Path.of(System.getProperty("groq.quality.output-dir"));
        assertThat(dir.isAbsolute() && Files.isDirectory(dir) && !Files.isSymbolicLink(dir)).isTrue();
        var p = new Phase(dir, System.getProperty("groq.quality.phase"), HASH);
        String version = p.name().equals("before") ? "resume-interview-v4" : "resume-interview-v5";
        assertThat(System.getProperty("groq.quality.expected-version")).isEqualTo(version);
        Path env = Path.of(System.getenv().getOrDefault("INTERVIEW_ENV_FILE", ".env"));
        var props = new Properties();
        try (var reader = Files.newBufferedReader(env)) { props.load(reader); }
        String key = props.getProperty("GROQ_API_KEY", "").strip();
        assertThat(!key.isEmpty()).as("비밀 설정에 키가 필요합니다.").isTrue();
        var service = new GroqService(key);
        assertThat(service.getCriteriaVersion()).isEqualTo(version);
        try (var channel = FileChannel.open(dir.resolve("quality.lock"), CREATE, WRITE);
             var lock = channel.tryLock()) {
            assertThat(lock).as("다른 비교 프로세스가 실행 중입니다.").isNotNull();
            p.start();
            for (int i = 0; i < cases.size(); i++) {
                var sample = cases.get(i);
                long delay = p.nextTime(i) - System.currentTimeMillis();
                if (delay > 0) Thread.sleep(delay);
                p.reserve(i, System.currentTimeMillis());
                System.out.printf("START %s %s%n", p.name(), sample.id());
                Evaluation result;
                try {
                    result = service.evaluate(sample.context());
                } catch (RuntimeException e) {
                    int status = e instanceof CustomException c ? c.getErrorCode().getStatus().value() : 0;
                    p.record(i, false, Map.of("id", sample.id(), "error", GroqLiveEvidence.failure(status, e)));
                    throw new AssertionError("평가 중단: " + sample.id());
                }
                p.record(i, true, Map.of("id", sample.id(), "criteriaVersion", version,
                        "model", service.getModel(), "evaluation", result));
                System.out.printf("SUCCESS %s %s %s %s %s%n", p.name(), sample.id(),
                        result.technicalAccuracy(), result.reasoning(), result.clarity());
            }
        }
    }
}

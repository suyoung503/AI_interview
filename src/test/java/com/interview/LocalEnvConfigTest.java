package com.interview;

import com.interview.service.GroqService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEnvConfigTest {

    @TempDir Path directory;

    @Test
    void loadsGroqKeyFromLocalPropertiesWhenNotExported() throws IOException {
        try (var context = boot(false, Map.of())) {
            assertThat(configuredKey(context)).isEqualTo("dummy-local-key");
        }
    }

    @Test
    void exportedEnvironmentTakesPriorityOverLocalFile() throws IOException {
        try (var context = boot(false, Map.of("GROQ_API_KEY", "dummy-exported-key"))) {
            assertThat(configuredKey(context)).isEqualTo("dummy-exported-key");
        }
    }

    @Test
    void testProfileDoesNotImportLocalFileAndUsesBlankKey() throws IOException {
        try (var context = boot(true, Map.of("GROQ_API_KEY", "dummy-exported-key"))) {
            assertThat(configuredKey(context)).isEmpty();
            assertThat(context.getEnvironment().getProperty("DOTENV_PROBE")).isNull();
        }
    }

    private ConfigurableApplicationContext boot(boolean testProfile, Map<String, Object> exported)
            throws IOException {
        Path localEnv = directory.resolve(".env");
        Files.writeString(localEnv, "GROQ_API_KEY=dummy-local-key\nDOTENV_PROBE=present\n");
        var environment = new StandardEnvironment();
        var sources = environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource("testEnvironment", exported));
        sources.addFirst(new MapPropertySource("isolatedConfig", Map.of(
                "INTERVIEW_ENV_FILE", localEnv.toAbsolutePath().toString(),
                "spring.config.location", testProfile
                        ? "classpath:/application.properties,classpath:/application-test.yml"
                        : "classpath:/application.properties")));
        var app = new SpringApplicationBuilder(Config.class, GroqService.class).environment(environment)
                .web(WebApplicationType.NONE).logStartupInfo(false);
        return testProfile ? app.profiles("test").run() : app.run();
    }

    private String configuredKey(ConfigurableApplicationContext context) {
        // Only the isolated, dummy property sources above are available here.
        return (String) ReflectionTestUtils.getField(context.getBean(GroqService.class), "apiKey");
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {}
}

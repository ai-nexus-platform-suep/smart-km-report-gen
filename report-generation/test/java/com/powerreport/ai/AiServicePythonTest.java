package com.powerreport.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

class AiServicePythonTest {

    @Test
    void aiServicePythonUnitTestsPassOrSkipWhenDependenciesAreMissing() throws Exception {
        Path testDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path reportGenerationDir = testDir.getParent();
        Path pythonTestDir = testDir.resolve("python");
        Path aiServiceDir = reportGenerationDir.resolve("ai-service");

        ProcessBuilder builder = new ProcessBuilder(command(pythonExecutable(), "-m", "unittest", "discover",
                "-s", pythonTestDir.toString(), "-p", "test_*.py", "-v"));
        builder.directory(reportGenerationDir.toFile());
        builder.redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.put("LLM_MOCK", "true");
        environment.put("PYTHONPATH", joinPythonPath(aiServiceDir, environment.get("PYTHONPATH")));

        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new TestAbortedException("python executable is not available", ex);
        }

        boolean finished = process.waitFor(Duration.ofSeconds(30).toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new AssertionError("Python ai-service tests timed out after 30 seconds");
        }

        String output = process.inputReader(StandardCharsets.UTF_8)
                .lines()
                .collect(Collectors.joining(System.lineSeparator()));
        assertThat(process.exitValue())
                .as(output)
                .isZero();
    }

    private List<String> command(String... parts) {
        return new ArrayList<>(List.of(parts));
    }

    private String pythonExecutable() {
        return System.getenv().getOrDefault("PYTHON", "python");
    }

    private String joinPythonPath(Path aiServiceDir, String existing) {
        if (existing == null || existing.isBlank()) {
            return aiServiceDir.toString();
        }
        return aiServiceDir + System.getProperty("path.separator") + existing;
    }
}

package io.cucumber.teamcityformatter;

import io.cucumber.messages.NdjsonToMessageIterable;
import io.cucumber.messages.types.Envelope;
import io.cucumber.teamcityformatter.MessagesToTeamCityWriter.Builder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.cucumber.teamcityformatter.Jackson.OBJECT_MAPPER;
import static io.cucumber.teamcityformatter.MessageOrderer.originalOrder;
import static io.cucumber.teamcityformatter.MessageOrderer.simulateParallelExecution;
import static io.cucumber.teamcityformatter.MessagesToTeamCityWriter.TeamCityFeature.PRINT_TEST_CASES_AFTER_TEST_RUN;
import static java.nio.file.Files.readAllBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MessagesToTeamCityWriterAcceptanceTest {
    private static final NdjsonToMessageIterable.Deserializer deserializer = (json) -> OBJECT_MAPPER.readValue(json, Envelope.class);

    static List<TestCase> acceptance() throws IOException {
        List<Path> sources = getSources();

        List<TestCase> testCases = new ArrayList<>();
        sources.forEach(path -> testCases.add(new TestCase(path, MessagesToTeamCityWriter.builder())));

        return testCases;
    }

    private static List<Path> getSources() throws IOException {
        try (Stream<Path> paths = Files.list(Paths.get("..", "testdata", "src"))) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".ndjson"))
                    .collect(Collectors.toList());
        }
    }


    private static <T extends OutputStream> T writePrettyReport(TestCase testCase, T out, Builder builder, Consumer<List<Envelope>> orderer) throws IOException {
        List<Envelope> messages = new ArrayList<>();
        try (InputStream in = Files.newInputStream(testCase.source)) {
            try (NdjsonToMessageIterable envelopes = new NdjsonToMessageIterable(in, deserializer)) {
                envelopes.forEach(messages::add);
            }
        }
        orderer.accept(messages);

        try (MessagesToTeamCityWriter writer = builder.build(out)) {
            for (Envelope envelope : messages) {
                writer.write(envelope);
            }
        }
        return out;
    }


    @ParameterizedTest
    @MethodSource("acceptance")
    void test(TestCase testCase) throws IOException {
        ByteArrayOutputStream bytes = writePrettyReport(testCase, new ByteArrayOutputStream(), testCase.builder, originalOrder());
        assertThat(bytes.toString()).isEqualToIgnoringNewLines(new String(readAllBytes(testCase.expected)));
    }
    
    private final List<String> exceptions = Arrays.asList(
            // TODO: Create issue to support global hooks in output
            "global-hooks-attachments", 
            "retry", 
            "multiple-features-reversed"
    );
    
    @ParameterizedTest
    @MethodSource("acceptance")
    void testPrintAfterTestRun(TestCase testCase) throws IOException {
        assumeFalse(() -> exceptions.contains(testCase.name));

        Builder builder = testCase.builder.feature(PRINT_TEST_CASES_AFTER_TEST_RUN, true);
        ByteArrayOutputStream bytes = writePrettyReport(testCase, new ByteArrayOutputStream(), builder, originalOrder());
        assertThat(bytes.toString()).isEqualToIgnoringNewLines(new String(readAllBytes(testCase.expected)));
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void testPrintAfterTestRunWithSimulatedParallelExecution(TestCase testCase) throws IOException {
        assumeFalse(() -> exceptions.contains(testCase.name));
        Builder builder = testCase.builder.feature(PRINT_TEST_CASES_AFTER_TEST_RUN, true);
        ByteArrayOutputStream bytes = writePrettyReport(testCase, new ByteArrayOutputStream(), builder, simulateParallelExecution());
        assertThat(bytes.toString()).isEqualToIgnoringNewLines(new String(readAllBytes(testCase.expected)));
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    @Disabled
    void updateExpectedPrettyFiles(TestCase testCase) throws IOException {
        try (OutputStream out = Files.newOutputStream(testCase.expected)) {
            writePrettyReport(testCase, out, testCase.builder, originalOrder());
        }
    }

    static class TestCase {
        private final Path source;
        private final Builder builder;
        private final Path expected;

        private final String name;

        TestCase(Path source, Builder builder) {
            this.source = source;
            this.builder = builder;
            String fileName = source.getFileName().toString();
            this.name = fileName.substring(0, fileName.lastIndexOf(".ndjson"));
            this.expected = source.getParent().resolve(name + ".log");
        }

        @Override
        public String toString() {
            return name;
        }

    }

}


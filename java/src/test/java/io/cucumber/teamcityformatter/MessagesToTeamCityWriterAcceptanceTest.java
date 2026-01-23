package io.cucumber.teamcityformatter;

import io.cucumber.compatibilitykit.MessageOrderer;
import io.cucumber.messages.NdjsonToMessageIterable;
import io.cucumber.messages.ndjson.Deserializer;
import io.cucumber.messages.types.Envelope;
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
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.cucumber.teamcityformatter.MessagesToTeamCityWriter.TeamCityFeature.PRINT_TEST_CASES_AFTER_TEST_RUN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MessagesToTeamCityWriterAcceptanceTest {
    private static final Random random = new Random(202509121959L);
    private static final MessageOrderer messageOrderer = new MessageOrderer(random);

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

    private static ByteArrayOutputStream writePrettyReport(TestCase testCase, MessagesToTeamCityWriter.Builder builder, Consumer<List<Envelope>> orderer) throws IOException {
        return writePrettyReport(testCase, new ByteArrayOutputStream(), builder, orderer);
    }

    private static <T extends OutputStream> T writePrettyReport(TestCase testCase, T out, MessagesToTeamCityWriter.Builder builder, Consumer<List<Envelope>> orderer) throws IOException {
        List<Envelope> messages = new ArrayList<>();
        try (InputStream in = Files.newInputStream(testCase.source)) {
            try (NdjsonToMessageIterable envelopes = new NdjsonToMessageIterable(in, new Deserializer())) {
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
        ByteArrayOutputStream bytes = writePrettyReport(testCase, testCase.builder, messageOrderer.originalOrder());
        assertThat(bytes.toString(UTF_8)).isEqualToIgnoringNewLines(Files.readString(testCase.expected));
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

        MessagesToTeamCityWriter.Builder builder = testCase.builder.feature(PRINT_TEST_CASES_AFTER_TEST_RUN, true);
        ByteArrayOutputStream bytes = writePrettyReport(testCase, builder, messageOrderer.originalOrder());
        assertThat(bytes.toString(UTF_8)).isEqualToIgnoringNewLines(Files.readString(testCase.expected));
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void testPrintAfterTestRunWithSimulatedParallelExecution(TestCase testCase) throws IOException {
        assumeFalse(() -> exceptions.contains(testCase.name));
        MessagesToTeamCityWriter.Builder builder = testCase.builder.feature(PRINT_TEST_CASES_AFTER_TEST_RUN, true);
        ByteArrayOutputStream bytes = writePrettyReport(testCase, builder, messageOrderer.simulateParallelExecution());
        assertThat(bytes.toString(UTF_8)).isEqualToIgnoringNewLines(Files.readString(testCase.expected));
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    @Disabled
    void updateExpectedFiles(TestCase testCase) throws IOException {
        try (OutputStream out = Files.newOutputStream(testCase.expected)) {
            writePrettyReport(testCase, out, testCase.builder, messageOrderer.originalOrder());
        }
    }

    static class TestCase {
        private final Path source;
        private final MessagesToTeamCityWriter.Builder builder;
        private final Path expected;

        private final String name;

        TestCase(Path source, MessagesToTeamCityWriter.Builder builder) {
            this.source = source;
            this.builder = builder;
            String fileName = source.getFileName().toString();
            this.name = fileName.substring(0, fileName.lastIndexOf(".ndjson"));
            this.expected = requireNonNull(source.getParent()).resolve(name + ".log");
        }

        @Override
        public String toString() {
            return name;
        }

    }

}


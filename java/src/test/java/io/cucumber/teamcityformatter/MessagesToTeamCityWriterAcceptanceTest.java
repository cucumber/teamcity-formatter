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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.cucumber.teamcityformatter.Jackson.OBJECT_MAPPER;
import static java.nio.file.Files.readAllBytes;
import static org.assertj.core.api.Assertions.assertThat;

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

    private static <T extends OutputStream> T writePrettyReport(TestCase testCase, T out, Builder builder) throws IOException {
        try (InputStream in = Files.newInputStream(testCase.source)) {
            try (NdjsonToMessageIterable envelopes = new NdjsonToMessageIterable(in, deserializer)) {
                try (MessagesToTeamCityWriter writer = builder.build(out)) {
                    for (Envelope envelope : envelopes) {
                        writer.write(envelope);
                    }
                }
            }
        }
        return out;
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    void test(TestCase testCase) throws IOException {
        ByteArrayOutputStream bytes = writePrettyReport(testCase, new ByteArrayOutputStream(), testCase.builder);
        assertThat(bytes.toString()).isEqualToIgnoringNewLines(new String(readAllBytes(testCase.expected)));
    }

    @ParameterizedTest
    @MethodSource("acceptance")
    @Disabled
    void updateExpectedPrettyFiles(TestCase testCase) throws IOException {
        try (OutputStream out = Files.newOutputStream(testCase.expected)) {
            writePrettyReport(testCase, out, testCase.builder);
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


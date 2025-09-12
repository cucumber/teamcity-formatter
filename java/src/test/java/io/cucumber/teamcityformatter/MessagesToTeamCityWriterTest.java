package io.cucumber.teamcityformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.TestRunFinished;
import io.cucumber.messages.types.TestRunStarted;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static io.cucumber.messages.Convertor.toMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagesToTeamCityWriterTest {

    @Test
    void it_writes_two_messages_to_messages() throws IOException {
        Instant started = Instant.ofEpochSecond(10);
        Instant finished = Instant.ofEpochSecond(30);

        String html = renderAsPretty(
                Envelope.of(new TestRunStarted(toMessage(started), "some-id")),
                Envelope.of(new TestRunFinished(null, true, toMessage(finished), null, "some-id")));

        assertThat(html).isEqualToNormalizingNewlines("" +
                "##teamcity[enteredTheMatrix timestamp = '1970-01-01T12:00:10.000+0000']\n" +
                "##teamcity[testSuiteStarted timestamp = '1970-01-01T12:00:10.000+0000' name = 'Cucumber']\n" +
                "##teamcity[customProgressStatus testsCategory = 'Scenarios' count = '0' timestamp = '1970-01-01T12:00:10.000+0000']\n" +
                "##teamcity[customProgressStatus testsCategory = '' count = '0' timestamp = '1970-01-01T12:00:30.000+0000']\n" +
                "##teamcity[testSuiteFinished timestamp = '1970-01-01T12:00:30.000+0000' name = 'Cucumber']\n"
        );
    }

    @Test
    void it_writes_no_message_to_pretty() throws IOException {
        String html = renderAsPretty();
        assertThat(html).isEmpty();
    }

    @Test
    void it_throws_when_writing_after_close() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MessagesToTeamCityWriter writer = create(bytes);
        writer.close();
        assertThrows(IOException.class, () -> writer.write(null));
    }

    @Test
    void it_can_be_closed_twice() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MessagesToTeamCityWriter messagesToHtmlWriter = create(bytes);
        messagesToHtmlWriter.close();
        assertDoesNotThrow(messagesToHtmlWriter::close);
    }

    private static String renderAsPretty(Envelope... messages) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MessagesToTeamCityWriter messagesToHtmlWriter = create(bytes)) {
            for (Envelope message : messages) {
                messagesToHtmlWriter.write(message);
            }
        }

        return new String(bytes.toByteArray(), UTF_8);
    }

    private static MessagesToTeamCityWriter create(ByteArrayOutputStream bytes) {
        return MessagesToTeamCityWriter.builder().build(bytes);
    }
}

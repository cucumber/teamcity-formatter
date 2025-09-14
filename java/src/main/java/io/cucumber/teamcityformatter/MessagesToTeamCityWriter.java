package io.cucumber.teamcityformatter;

import io.cucumber.messages.types.Envelope;
import io.cucumber.query.Query;
import io.cucumber.query.Repository;

import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Set;

import static io.cucumber.query.Repository.RepositoryFeature.INCLUDE_GHERKIN_DOCUMENTS;
import static io.cucumber.query.Repository.RepositoryFeature.INCLUDE_HOOKS;
import static io.cucumber.query.Repository.RepositoryFeature.INCLUDE_STEP_DEFINITIONS;
import static io.cucumber.query.Repository.RepositoryFeature.INCLUDE_SUGGESTIONS;
import static io.cucumber.teamcityformatter.MessagesToTeamCityWriter.TeamCityFeature.PRINT_TEST_CASES_AFTER_TEST_RUN;
import static java.util.Objects.requireNonNull;

/**
 * Writes <a href="https://github.com/cucumber/messages">Cucumber Messages</a>
 * as <a href=https://www.jetbrains.com/help/teamcity/service-messages.html>TeamCity - Service Messages</a>.
 */
public final class MessagesToTeamCityWriter implements AutoCloseable {

    private final Repository repository = Repository.builder()
            .feature(INCLUDE_GHERKIN_DOCUMENTS, true)
            .feature(INCLUDE_STEP_DEFINITIONS, true)
            .feature(INCLUDE_HOOKS, true)
            .feature(INCLUDE_SUGGESTIONS, true)
            .build();
    private final Set<TeamCityFeature> features;
    private final TeamCityWriter writer;

    private boolean streamClosed = false;

    private MessagesToTeamCityWriter(OutputStream out, Set<TeamCityFeature> features) {
        this.writer = new TeamCityWriter(
                new TeamCityCommandWriter(out),
                new Query(repository)
        );
        this.features = features;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Writes a cucumber message.
     *
     * @param envelope the message
     * @throws IOException if an IO error occurs
     */
    public void write(Envelope envelope) throws IOException {
        if (streamClosed) {
            throw new IOException("Stream closed");
        }
        repository.update(envelope);
        if (features.contains(PRINT_TEST_CASES_AFTER_TEST_RUN)) {
            writer.printTestCasesAfterTestRun(envelope);
        } else {
            writer.printTestCasesRealTime(envelope);
        }
    }

    /**
     * Closes the stream, flushing it first. Once closed further write()
     * invocations will cause an IOException to be thrown. Closing a closed
     * stream has no effect.
     */
    @Override
    public void close() throws IOException {
        if (streamClosed) {
            return;
        }

        try {
            writer.close();
        } finally {
            streamClosed = true;
        }
    }

    public enum TeamCityFeature {
        /**
         * Prints all test cases messages after the test run has concluded.
         * <p>
         * The TeamCity message protocol does not support parallel execution. 
         * By printing these events at the end the output is still intelligible. 
         * 
         */
        PRINT_TEST_CASES_AFTER_TEST_RUN
    }

    public static final class Builder {

        private final EnumSet<TeamCityFeature> features = EnumSet.noneOf(TeamCityFeature.class);

        private Builder() {
        }

        /**
         * Toggles a given feature.
         */
        public Builder feature(TeamCityFeature feature, boolean enabled) {
            if (enabled) {
                features.add(feature);
            } else {
                features.remove(feature);
            }
            return this;
        }

        public MessagesToTeamCityWriter build(OutputStream out) {
            requireNonNull(out);
            Set<TeamCityFeature> features = EnumSet.copyOf(this.features);
            return new MessagesToTeamCityWriter(out, features);
        }
    }
}

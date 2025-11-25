package io.cucumber.teamcityformatter;

import io.cucumber.messages.Convertor;
import io.cucumber.messages.types.Attachment;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Exception;
import io.cucumber.messages.types.Hook;
import io.cucumber.messages.types.Location;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.PickleStep;
import io.cucumber.messages.types.TestCaseFinished;
import io.cucumber.messages.types.TestCaseStarted;
import io.cucumber.messages.types.TestRunFinished;
import io.cucumber.messages.types.TestRunStarted;
import io.cucumber.messages.types.TestStep;
import io.cucumber.messages.types.TestStepFinished;
import io.cucumber.messages.types.TestStepResult;
import io.cucumber.messages.types.TestStepResultStatus;
import io.cucumber.messages.types.TestStepStarted;
import io.cucumber.messages.types.Timestamp;
import io.cucumber.query.LineageReducer;
import io.cucumber.query.Query;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static io.cucumber.messages.Convertor.toDuration;
import static io.cucumber.query.LineageReducer.descending;
import static io.cucumber.teamcityformatter.SourceReferenceFormatter.formatMethodName;
import static java.util.Collections.emptyList;

/**
 * Writes Cucumber messages as TeamCity messages.
 * <p>
 * IDEA presents tests execution in a tree diagram. Cucumber however does
 * have any hierarchy. Everything is executed as a pickle. 
 * <p>
 * To simulate a hierarchy we use the {@link io.cucumber.query.Lineage} of a
 * {@link Pickle} in a feature file. Whenever the lineage changes we publish
 * the right {@code testStarted} and {@code testFinished} messages.
 */
final class TeamCityWriter implements AutoCloseable {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ss.SSSZ");

    private static final String TEAMCITY_PREFIX = "##teamcity";

    private static final String TEMPLATE_ENTER_THE_MATRIX = TEAMCITY_PREFIX + "[enteredTheMatrix timestamp = '%s']";
    private static final String TEMPLATE_TEST_RUN_STARTED = TEAMCITY_PREFIX
            + "[testSuiteStarted timestamp = '%s' name = 'Cucumber']";
    private static final String TEMPLATE_TEST_RUN_FINISHED = TEAMCITY_PREFIX
            + "[testSuiteFinished timestamp = '%s' name = 'Cucumber']";

    private static final String TEMPLATE_TEST_SUITE_STARTED = TEAMCITY_PREFIX
            + "[testSuiteStarted timestamp = '%s' locationHint = '%s' name = '%s']";
    private static final String TEMPLATE_TEST_SUITE_FINISHED = TEAMCITY_PREFIX
            + "[testSuiteFinished timestamp = '%s' name = '%s']";

    private static final String TEMPLATE_TEST_STARTED = TEAMCITY_PREFIX
            + "[testStarted timestamp = '%s' locationHint = '%s' captureStandardOutput = 'true' name = '%s']";
    private static final String TEMPLATE_TEST_FINISHED = TEAMCITY_PREFIX
            + "[testFinished timestamp = '%s' duration = '%s' name = '%s']";
    private static final String TEMPLATE_TEST_FAILED = TEAMCITY_PREFIX
            + "[testFailed timestamp = '%s' duration = '%s' message = '%s' details = '%s' name = '%s']";

    private static final String TEMPLATE_TEST_COMPARISON_FAILED = TEAMCITY_PREFIX
            + "[testFailed timestamp = '%s' duration = '%s' message = '%s' details = '%s' expected = '%s' actual = '%s' name = '%s']";
    private static final String TEMPLATE_TEST_IGNORED = TEAMCITY_PREFIX
            + "[testIgnored timestamp = '%s' duration = '%s' message = '%s' name = '%s']";

    private static final String TEMPLATE_BEFORE_ALL_AFTER_ALL_STARTED = TEAMCITY_PREFIX
            + "[testStarted timestamp = '%s' name = '%s']";
    private static final String TEMPLATE_BEFORE_ALL_AFTER_ALL_FAILED = TEAMCITY_PREFIX
            + "[testFailed timestamp = '%s' message = '%s' details = '%s' name = '%s']";
    private static final String TEMPLATE_BEFORE_ALL_AFTER_ALL_FINISHED = TEAMCITY_PREFIX
            + "[testFinished timestamp = '%s' name = '%s']";

    private static final String TEMPLATE_PROGRESS_COUNTING_STARTED = TEAMCITY_PREFIX
            + "[customProgressStatus testsCategory = 'Scenarios' count = '0' timestamp = '%s']";
    private static final String TEMPLATE_PROGRESS_COUNTING_FINISHED = TEAMCITY_PREFIX
            + "[customProgressStatus testsCategory = '' count = '0' timestamp = '%s']";
    private static final String TEMPLATE_PROGRESS_TEST_STARTED = TEAMCITY_PREFIX
            + "[customProgressStatus type = 'testStarted' timestamp = '%s']";
    private static final String TEMPLATE_PROGRESS_TEST_FINISHED = TEAMCITY_PREFIX
            + "[customProgressStatus type = 'testFinished' timestamp = '%s']";

    private static final String TEMPLATE_ATTACH_WRITE_EVENT = TEAMCITY_PREFIX + "[message text='%s' status='NORMAL']";

    private final LineageReducer<List<LineageNode>> pathCollector = descending(PathCollector::new);

    // Only used when executing concurrently.
    private final Map<String, List<String>> attachmentMessagesByStepId = new HashMap<>();
    
    private List<LineageNode> currentLineage = new ArrayList<>();

    private final TeamCityCommandWriter out;
    private final Query query;

    TeamCityWriter(TeamCityCommandWriter out, Query query) {
        this.out = out;
        this.query = query;
    }

    void printTestCasesRealTime(Envelope event) {
        event.getTestRunStarted().ifPresent(this::printTestRunStarted);
        event.getTestCaseStarted().ifPresent(this::printTestCaseStarted);
        event.getTestStepStarted().ifPresent(this::printTestStepStarted);
        event.getTestStepFinished().ifPresent(this::printTestStepFinished);
        event.getTestCaseFinished().ifPresent(this::printTestCaseFinished);
        event.getTestRunFinished().ifPresent(this::printTestRunFinished);
        event.getAttachment().ifPresent(this::handleAttachment);
    }

    void printTestCasesAfterTestRun(Envelope event) {
        event.getTestRunStarted().ifPresent(this::printTestRunStarted);
        event.getTestRunFinished().ifPresent(this::printCompleteTestRun);
        event.getAttachment().ifPresent(this::storeStepAttachments);
    }

    private void printCompleteTestRun(TestRunFinished event) {
        findAllTestCaseStartedInCanonicalOrder()
                .forEach(this::printCompleteTestCase);
        printTestRunFinished(event);
    }

    private Stream<TestCaseStarted> findAllTestCaseStartedInCanonicalOrder() {
        return query.findAllTestCaseStarted().stream()
                .map(testCaseStarted -> {
                    Optional<Pickle> pickle = query.findPickleBy(testCaseStarted);
                    String uri = pickle.map(Pickle::getUri).orElse(null);
                    Integer line = pickle.flatMap(query::findLocationOf).map(Location::getLine).orElse(null);
                    return new OrderableEvent<>(testCaseStarted, uri, line);
                })
                .sorted()
                .map(OrderableEvent::getEvent);
    }

    @Override
    public void close() {
        out.close();
    }

    private void printCompleteTestCase(TestCaseStarted testCaseStarted) {
        printTestCaseStarted(testCaseStarted);

        query.findTestStepsStartedBy(testCaseStarted)
                .forEach(testStepStarted -> {
                    printTestStepStarted(testStepStarted);
                    findAttachmentBy(testStepStarted).forEach(this::handleAttachment);
                    findTestStepFinishedBy(testCaseStarted, testStepStarted).ifPresent(this::printTestStepFinished);
                });

        query.findTestCaseFinishedBy(testCaseStarted)
                .ifPresent(this::printTestCaseFinished);
    }

    private List<String> findAttachmentBy(TestStepStarted testStepStarted) {
        return attachmentMessagesByStepId.getOrDefault(testStepStarted.getTestStepId(), emptyList());
    }

    private Optional<TestStepFinished> findTestStepFinishedBy(
            TestCaseStarted testCaseStarted, TestStepStarted testStepStarted
    ) {
        return query.findTestStepsFinishedBy(testCaseStarted).stream()
                .filter(testStepFinished -> testStepFinished.getTestStepId().equals(testStepStarted.getTestStepId()))
                .findFirst();
    }

    private void storeStepAttachments(Attachment event) {
        Optional<String> testStepId = event.getTestStepId();
        if (testStepId.isPresent()) {
            // Store a more minimal version of the attachment.
            // Avoid holding on to large attachments needlessly 
            attachmentMessagesByStepId.compute(testStepId.get(), updateList(extractAttachmentMessage(event)));
        } else {
            handleAttachment(event);
        }
    }

    private <K, E> BiFunction<K, List<E>, List<E>> updateList(E element) {
        return (key, existing) -> {
            if (existing != null) {
                existing.add(element);
                return existing;
            }
            List<E> list = new ArrayList<>();
            list.add(element);
            return list;
        };
    }

    private void printTestRunStarted(TestRunStarted event) {
        String timestamp = formatTimeStamp(event.getTimestamp());
        out.print(TEMPLATE_ENTER_THE_MATRIX, timestamp);
        out.print(TEMPLATE_TEST_RUN_STARTED, timestamp);
        out.print(TEMPLATE_PROGRESS_COUNTING_STARTED, timestamp);
    }

    private void printTestCaseStarted(TestCaseStarted event) {
        query.findPickleBy(event)
                .flatMap(this::createLineageOf)
                .ifPresent(lineage -> {
                    String timestamp = formatTimeStamp(event.getTimestamp());
                    poppedNodes(lineage).forEach(node -> finishNode(timestamp, node));
                    pushedNodes(lineage).forEach(node -> startNode(timestamp, node));
                    this.currentLineage = lineage;
                    out.print(TEMPLATE_PROGRESS_TEST_STARTED, timestamp);
                });
    }

    private Optional<List<LineageNode>> createLineageOf(Pickle pickle) {
        return query.findLineageBy(pickle)
                .map(lineage -> pathCollector.reduce(lineage, pickle));
    }

    private void startNode(String timestamp, LineageNode node) {
        String name = node.getName();
        String location = node.getUri() + ":" + node.getLocation().getLine();
        out.print(TEMPLATE_TEST_SUITE_STARTED, timestamp, location, name);
    }

    private void finishNode(String timestamp, LineageNode node) {
        String name = node.getName();
        out.print(TEMPLATE_TEST_SUITE_FINISHED, timestamp, name);
    }

    private List<LineageNode> poppedNodes(List<LineageNode> newLineage) {
        List<LineageNode> nodes = new ArrayList<>(reversedPoppedNodes(currentLineage, newLineage));
        Collections.reverse(nodes);
        return nodes;
    }

    private List<LineageNode> reversedPoppedNodes(List<LineageNode> currentLineage, List<LineageNode> newLineage) {
        for (int i = 0; i < currentLineage.size() && i < newLineage.size(); i++) {
            if (!currentLineage.get(i).equals(newLineage.get(i))) {
                return currentLineage.subList(i, currentLineage.size());
            }
        }
        if (newLineage.size() < currentLineage.size()) {
            return currentLineage.subList(newLineage.size(), currentLineage.size());
        }
        return emptyList();
    }

    private List<LineageNode> pushedNodes(List<LineageNode> newLineage) {
        for (int i = 0; i < currentLineage.size() && i < newLineage.size(); i++) {
            if (!currentLineage.get(i).equals(newLineage.get(i))) {
                return newLineage.subList(i, newLineage.size());
            }
        }
        if (newLineage.size() < currentLineage.size()) {
            return emptyList();
        }
        return newLineage.subList(currentLineage.size(), newLineage.size());
    }

    private void printTestStepStarted(TestStepStarted event) {
        String timestamp = formatTimeStamp(event.getTimestamp());
        query.findTestStepBy(event).ifPresent(testStep -> {
            String name = formatTestStepName(testStep);
            String location = findPickleTestStepLocation(event, testStep)
                    .orElseGet(() -> findHookStepLocation(testStep)
                            .orElse(""));
            out.print(TEMPLATE_TEST_STARTED, timestamp, location, name);
        });
    }

    private Optional<String> findPickleTestStepLocation(TestStepStarted testStepStarted, TestStep testStep) {
        return query.findPickleStepBy(testStep)
                .flatMap(query::findStepBy)
                .flatMap(step -> query.findPickleBy(testStepStarted)
                        .map(pickle -> pickle.getUri() + ":" + step.getLocation().getLine()));
    }

    private Optional<String> findHookStepLocation(TestStep testStep) {
        return query.findHookBy(testStep)
                .map(Hook::getSourceReference)
                .flatMap(SourceReferenceFormatter::formatLocation);
    }

    private void printTestStepFinished(TestStepFinished event) {
        String timeStamp = formatTimeStamp(event.getTimestamp());
        TestStepResult testStepResult = event.getTestStepResult();
        long duration = toDuration(testStepResult.getDuration()).toMillis();

        query.findTestStepBy(event).ifPresent(testStep -> {
            String name = formatTestStepName(testStep);

            Optional<Exception> error = testStepResult.getException();
            TestStepResultStatus status = testStepResult.getStatus();
            switch (status) {
                case SKIPPED -> {
                    String message = error.flatMap(Exception::getMessage).orElse("Step skipped");
                    out.print(TEMPLATE_TEST_IGNORED, timeStamp, duration, message, name);
                }
                case PENDING -> {
                    String details = error.flatMap(Exception::getMessage).orElse("");
                    out.print(TEMPLATE_TEST_FAILED, timeStamp, duration, "Step pending", details, name);
                }
                case UNDEFINED -> {
                    String snippets = findSnippets(event).orElse("");
                    out.print(TEMPLATE_TEST_FAILED, timeStamp, duration, "Step undefined", snippets, name);
                }
                case AMBIGUOUS, FAILED -> {
                    String details = error.flatMap(Exception::getStackTrace).orElse("");
                    String message = error.flatMap(Exception::getMessage).orElse(null);
                    if (message == null) {
                        out.print(TEMPLATE_TEST_FAILED, timeStamp, duration, "Step failed", details, name);
                        break;
                    }
                    ComparisonFailure comparisonFailure = ComparisonFailure.parse(message.trim());
                    if (comparisonFailure == null) {
                        out.print(TEMPLATE_TEST_FAILED, timeStamp, duration, "Step failed", details, name);
                        break;
                    }
                    out.print(TEMPLATE_TEST_COMPARISON_FAILED, timeStamp, duration, "Step failed", details,
                            comparisonFailure.getExpected(), comparisonFailure.getActual(), name);
                }
                default -> {
                }
            }
            out.print(TEMPLATE_TEST_FINISHED, timeStamp, duration, name);
        });
    }

    private String formatTestStepName(TestStep testStep) {
        return query.findPickleStepBy(testStep)
                .map(PickleStep::getText)
                .orElseGet(() -> query.findHookBy(testStep)
                        .map(TeamCityWriter::formatHookStepName)
                        .orElse("Unknown step"));
    }

    private static String formatHookStepName(Hook hook) {
        String hookType = getHookType(hook);
        String name = hook.getName()
                .map(hookName -> "(" + hookName + ")")
                .orElseGet(() -> formatMethodName(hook.getSourceReference())
                        .map(methodName -> "(" + methodName + ")")
                        .orElse("")
                );

        return hookType + name;
    }

    private static String getHookType(Hook hook) {
        return hook.getType().map(
                hookType -> switch (hookType) {
                    case BEFORE_TEST_RUN -> "BeforeAll";
                    case AFTER_TEST_RUN -> "AfterAll";
                    case BEFORE_TEST_CASE -> "Before";
                    case AFTER_TEST_CASE -> "After";
                    case BEFORE_TEST_STEP -> "BeforeStep";
                    case AFTER_TEST_STEP -> "AfterStep";
                }).orElse("Unknown");
    }

    private Optional<String> findSnippets(TestStepFinished event) {
        return query.findPickleBy(event)
                .map(query::findSuggestionsBy)
                .map(SuggestionFormatter::format);
    }

    private void printTestCaseFinished(TestCaseFinished event) {
        String timestamp = formatTimeStamp(event.getTimestamp());
        out.print(TEMPLATE_PROGRESS_TEST_FINISHED, timestamp);
        finishNode(timestamp, currentLineage.remove(currentLineage.size() - 1));
    }

    private void printTestRunFinished(TestRunFinished event) {
        String timestamp = formatTimeStamp(event.getTimestamp());
        out.print(TEMPLATE_PROGRESS_COUNTING_FINISHED, timestamp);

        List<LineageNode> emptyPath = new ArrayList<>();
        poppedNodes(emptyPath).forEach(node -> finishNode(timestamp, node));
        currentLineage = emptyPath;

        printBeforeAfterAllResult(event, timestamp);
        out.print(TEMPLATE_TEST_RUN_FINISHED, timestamp);
    }

    private void printBeforeAfterAllResult(TestRunFinished event, String timestamp) {
        Optional<Exception> error = event.getException();
        if (!error.isPresent()) {
            return;
        }
        // Use dummy test to display before all after all failures
        String name = "Before All/After All";
        out.print(TEMPLATE_BEFORE_ALL_AFTER_ALL_STARTED, timestamp, name);
        String details = error.flatMap(Exception::getStackTrace).orElse("");
        out.print(TEMPLATE_BEFORE_ALL_AFTER_ALL_FAILED, timestamp, "Before All/After All failed", details, name);
        out.print(TEMPLATE_BEFORE_ALL_AFTER_ALL_FINISHED, timestamp, name);
    }

    private void handleAttachment(Attachment event) {
        handleAttachment(extractAttachmentMessage(event));
    }

    private void handleAttachment(String message) {
        out.print(TEMPLATE_ATTACH_WRITE_EVENT, message);
    }

    private static String extractAttachmentMessage(Attachment event) {
        return switch (event.getContentEncoding()) {
            case IDENTITY -> "Write event:\n" + event.getBody() + "\n";
            case BASE64 -> {
                String name = event.getFileName().map(s -> s + " ").orElse("");
                yield "Embed event: " + name + "[" + event.getMediaType() + " " + (event.getBody().length() / 4) * 3
                        + " bytes]\n";
            }
        };
    }

    private static String formatTimeStamp(Timestamp timestamp) {
        ZonedDateTime date = Convertor.toInstant(timestamp).atZone(ZoneOffset.UTC);
        return DATE_FORMAT.format(date);
    }

}

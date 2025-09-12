package io.cucumber.teamcityformatter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

final class ComparisonFailure {

    private static final Pattern[] COMPARE_PATTERNS = new Pattern[]{
            // Hamcrest 2 MatcherAssert.assertThat
            Pattern.compile("expected: (.*)(?:\r\n|\r|\n) {5}but: was (.*)$",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            // AssertJ 3 ShouldBeEqual.smartErrorMessage
            Pattern.compile("expected: (.*)(?:\r\n|\r|\n) but was: (.*)$",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            // JUnit 5 AssertionFailureBuilder
            Pattern.compile("expected: <(.*)> but was: <(.*)>$",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            // JUnit 4 Assert.assertEquals
            Pattern.compile("expected:\\s?<(.*)> but was:\\s?<(.*)>$",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            // TestNG 7 Assert.assertEquals
            Pattern.compile("expected \\[(.*)] but found \\[(.*)]\n$",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
    };

    static ComparisonFailure parse(String message) {
        for (Pattern pattern : COMPARE_PATTERNS) {
            ComparisonFailure result = parse(message, pattern);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    static ComparisonFailure parse(String message, Pattern pattern) {
        final Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String expected = matcher.group(1);
        String actual = matcher.group(2);
        return new ComparisonFailure(expected, actual);
    }

    private final String expected;

    private final String actual;

    ComparisonFailure(String expected, String actual) {
        this.expected = requireNonNull(expected);
        this.actual = requireNonNull(actual);
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }
}

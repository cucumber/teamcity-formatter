package io.cucumber.teamcityformatter;

import io.cucumber.messages.types.JavaMethod;
import io.cucumber.messages.types.JavaStackTraceElement;
import io.cucumber.messages.types.Location;
import io.cucumber.messages.types.SourceReference;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Could be replaced by <a href=https://github.com/cucumber/compatibility-kit/issues/131>compatibility-kit#131</a>. 
  */
class SourceReferenceFormatterTest {

    @Test
    void none() {
        SourceReference sourceReference = new SourceReference(
                null,
                null,
                null,
                null
        );
        assertThat(SourceReferenceFormatter.formatLocation(sourceReference))
                .isEmpty();
    }

    @Test
    void method() {
        SourceReference sourceReference = new SourceReference(
                null,
                new JavaMethod(
                        "org.example.Example",
                        "example",
                        asList("java.lang.String", "java.lang.String")
                ),
                null,
                null
        );
        assertThat(SourceReferenceFormatter.formatLocation(sourceReference))
                .contains("java:test://org.example.Example/example");
    }

    @Test
    void method_no_arguments() {
        SourceReference sourceReference = new SourceReference(
                null,
                new JavaMethod(
                        "org.example.Example",
                        "example",
                        emptyList()
                ),
                null,
                null
        );
        assertThat(SourceReferenceFormatter.formatLocation(sourceReference))
                .contains("java:test://org.example.Example/example");
    }

    @Test
    void stacktrace() {
        SourceReference sourceReference = new SourceReference(
                null,
                null,
                new JavaStackTraceElement(
                        "org.example.Example",
                        "path/to/org/example/Example.java",
                        "example"
                ),
                new Location(31415L, 42L)
        );
        assertThat(SourceReferenceFormatter.formatLocation(sourceReference))
                .contains("java:test://org.example.Example/example");
    }


    @Test
    void stacktrace_unnamed_packaged() {
        SourceReference sourceReference = new SourceReference(
                null,
                null,
                new JavaStackTraceElement(
                        "Example",
                        "path/to/Example.java",
                        "<init>"
                ),
                new Location(31415L, 42L)
        );
        assertThat(SourceReferenceFormatter.formatLocation(sourceReference))
                .contains("java:test://Example/Example");
    }

    @Test
    void stacktrace__constructor() {
        SourceReference sourceReference = new SourceReference(
                null,
                null,
                new JavaStackTraceElement(
                        "org.example.Example",
                        "path/to/org/example/Example.java",
                        "<init>"
                ),
                new Location(31415L, 42L)
        );
        assertThat(SourceReferenceFormatter.formatLocation(sourceReference))
                .contains("java:test://org.example.Example/Example");
    }

}

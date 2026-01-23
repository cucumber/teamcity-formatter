package io.cucumber.teamcityformatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.shadow.de.siegmar.fastcsv.util.Nullable;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComparisonFailureTest {

    @Test
    void hamcrest3() {
        ComparisonFailure comparisonFailure = create(() -> org.hamcrest.MatcherAssert.assertThat(42, equalTo(1)));
        assertThat(comparisonFailure).isNotNull()
                .extracting(ComparisonFailure::getExpected, ComparisonFailure::getActual)
                .containsExactly("<1>", "<42>");
    }

    @Test
    void assertJ3() {
        ComparisonFailure comparisonFailure = create(() -> org.assertj.core.api.Assertions.assertThat(42).isEqualTo(1));
        assertThat(comparisonFailure).isNotNull()
                .extracting(ComparisonFailure::getExpected, ComparisonFailure::getActual)
                .containsExactly("1", "42");
    }

    @Test
    void junit5() {
        ComparisonFailure comparisonFailure = create(() -> org.junit.jupiter.api.Assertions.assertEquals(1, 42));
        assertThat(comparisonFailure).isNotNull()
                .extracting(ComparisonFailure::getExpected, ComparisonFailure::getActual)
                .containsExactly("1", "42");
    }

    @Test
    void junit4() {
        ComparisonFailure comparisonFailure = create(() -> org.junit.Assert.assertEquals(1, 42));
        assertThat(comparisonFailure).isNotNull()
                .extracting(ComparisonFailure::getExpected, ComparisonFailure::getActual)
                .containsExactly("1", "42");
    }

    @Test
    void testng7() {
        ComparisonFailure comparisonFailure = create(() -> org.testng.Assert.assertEquals(42, 1));
        assertThat(comparisonFailure).isNotNull()
                .extracting(ComparisonFailure::getExpected, ComparisonFailure::getActual)
                .containsExactly("1", "42");
    }

    private static @Nullable ComparisonFailure create(Executable executable) {
        AssertionError exception = assertThrows(AssertionError.class, executable);
        return ComparisonFailure.parse(requireNonNull(exception.getMessage()));
    }

}

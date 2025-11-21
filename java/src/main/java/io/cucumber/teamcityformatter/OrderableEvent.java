package io.cucumber.teamcityformatter;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static java.util.Objects.requireNonNull;

final class OrderableEvent<T> implements Comparable<OrderableEvent<T>> {
    private final T event;
    private final @Nullable String uri;
    private final @Nullable Integer line;

    OrderableEvent(T event, @Nullable String uri, @Nullable Integer line) {
        this.event = requireNonNull(event);
        this.uri = uri;
        this.line = line;
    }

    private final Comparator<OrderableEvent<T>> comparing = Comparator
            .comparing((OrderableEvent<T> ord) -> ord.uri, nullsFirst(naturalOrder()))
            .thenComparing(ord -> ord.line, nullsFirst(naturalOrder()));

    @Override
    public int compareTo(OrderableEvent<T> o) {
        return comparing.compare(this, o);
    }

    T getEvent() {
        return event;
    }
}

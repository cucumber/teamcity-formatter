package io.cucumber.teamcityformatter;

import io.cucumber.messages.types.Snippet;
import io.cucumber.messages.types.Suggestion;

import java.util.Collection;

import static java.util.stream.Collectors.joining;

final class SuggestionFormatter {
    
    private SuggestionFormatter(){
        // utility class
    }
    
    static String format(Collection<Suggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("You can implement this step");
        if (suggestions.size() > 1) {
            sb.append(" and ").append(suggestions.size() - 1).append(" other step(s)");
        }
        sb.append(" using the snippet(s) below:\n\n");
        String snippets = suggestions
                .stream()
                .map(Suggestion::getSnippets)
                .flatMap(Collection::stream)
                .map(Snippet::getCode)
                .distinct()
                .collect(joining("\n", "", "\n"));
        sb.append(snippets);
        return sb.toString();
    }
}

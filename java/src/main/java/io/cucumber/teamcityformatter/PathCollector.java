package io.cucumber.teamcityformatter;

import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableRow;
import io.cucumber.query.LineageReducer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

final class PathCollector implements LineageReducer.Collector<List<LineageNode>> {
    // There are at most 5 levels to a feature file.
    private final List<LineageNode> lineage = new ArrayList<>(5);
    private @Nullable String uri;
    private @Nullable String scenarioName;
    private int examplesIndex;
    private boolean isExample;

    private String getRequiredUri() {
        return requireNonNull(uri);
    }

    @Override
    public void add(GherkinDocument document) {
        uri = document.getUri().orElse("");
    }

    @Override
    public void add(Feature feature) {
        String name = getNameOrKeyword(feature.getName(), feature.getKeyword());
        lineage.add(new LineageNode(name, getRequiredUri(), feature.getLocation()));
    }

    @Override
    public void add(Rule rule) {
        String name = getNameOrKeyword(rule.getName(), rule.getKeyword());
        lineage.add(new LineageNode(name, getRequiredUri(), rule.getLocation()));
    }

    @Override
    public void add(Scenario scenario) {
        String name = getNameOrKeyword(scenario.getName(), scenario.getKeyword());
        lineage.add(new LineageNode(name, getRequiredUri(), scenario.getLocation()));
        scenarioName = name;
    }

    @Override
    public void add(Examples examples, int index) {
        String name = getNameOrKeyword(examples.getName(), examples.getKeyword());
        lineage.add(new LineageNode(name, getRequiredUri(), examples.getLocation()));
        examplesIndex = index;
    }

    @Override
    public void add(TableRow example, int index) {
        isExample = true;
        String name = "#" + (examplesIndex + 1) + "." + (index + 1);
        lineage.add(new LineageNode(name, getRequiredUri(), example.getLocation()));
    }

    @Override
    public void add(Pickle pickle) {
        // Case 1: Pickles from a scenario outline
        if (isExample) {
            String pickleName = pickle.getName();
            boolean parameterized = !pickleName.equals(scenarioName);
            if (parameterized) {
                LineageNode example = lineage.remove(lineage.size() - 1);
                String parameterizedExampleName = example.getName() + ": " + pickleName;
                lineage.add(new LineageNode(parameterizedExampleName, example.getUri(), example.getLocation()));
            }
        }
        // Case 2: Pickles from a scenario
        // Nothing to do, scenario name and pickle name are the same.
    }

    @Override
    public List<LineageNode> finish() {
        return lineage;
    }

    private static String getNameOrKeyword(String name, String keyword) {
        if (!name.isEmpty()) {
            return name;
        }
        if (!keyword.isEmpty()) {
            return keyword;
        }
        // Always return a non-empty string otherwise the tree diagram is
        // hard to click.
        return "Unknown";
    }
}

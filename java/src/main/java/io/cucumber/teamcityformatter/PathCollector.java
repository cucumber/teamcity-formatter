package io.cucumber.teamcityformatter;

import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableRow;
import io.cucumber.query.LineageReducer;

import java.util.ArrayList;
import java.util.List;

final class PathCollector implements LineageReducer.Collector<List<LineageNode>> {
    // There are at most 5 levels to a feature file.
    private final List<LineageNode> path = new ArrayList<>(5);
    private String uri;
    private String scenarioName;
    private int examplesIndex;
    private boolean isExample;

    @Override
    public void add(GherkinDocument document) {
        uri = document.getUri().orElse("");
    }

    @Override
    public void add(Feature feature) {
        String name = getNameOrKeyword(feature.getName(), feature.getKeyword());
        path.add(new LineageNode(name, uri, feature.getLocation()));
    }

    @Override
    public void add(Rule rule) {
        String name = getNameOrKeyword(rule.getName(), rule.getKeyword());
        path.add(new LineageNode(name, uri, rule.getLocation()));
    }

    @Override
    public void add(Scenario scenario) {
        String name = getNameOrKeyword(scenario.getName(), scenario.getKeyword());
        path.add(new LineageNode(name, uri, scenario.getLocation()));
        scenarioName = name;
    }

    @Override
    public void add(Examples examples, int index) {
        String name = getNameOrKeyword(examples.getName(), examples.getKeyword());
        path.add(new LineageNode(name, uri, examples.getLocation()));
        examplesIndex = index;
    }

    @Override
    public void add(TableRow example, int index) {
        isExample = true;
        String name = "#" + (examplesIndex + 1) + "." + (index + 1);
        path.add(new LineageNode(name, uri, example.getLocation()));
    }

    @Override
    public void add(Pickle pickle) {
        // Case 1: Pickles from a scenario outline
        if (isExample) {
            String pickleName = pickle.getName();
            boolean parameterized = !scenarioName.equals(pickleName);
            if (parameterized) {
                LineageNode example = path.remove(path.size() - 1);
                String parameterizedExampleName = example.getName() + ": " + pickleName;
                path.add(new LineageNode(parameterizedExampleName, example.getUri(), example.getLocation()));
            }
        }
        // Case 2: Pickles from a scenario
        // Nothing to do, scenario name and pickle name are the same.
    }

    @Override
    public List<LineageNode> finish() {
        return path;
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

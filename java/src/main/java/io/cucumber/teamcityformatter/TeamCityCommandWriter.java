package io.cucumber.teamcityformatter;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

final class TeamCityCommandWriter implements AutoCloseable {
    private final PrintWriter out;

    TeamCityCommandWriter(OutputStream out) {
        this.out = createPrintWriter(out);
    }

    private static PrintWriter createPrintWriter(OutputStream out) {
        return new PrintWriter(
                new OutputStreamWriter(
                        requireNonNull(out),
                        StandardCharsets.UTF_8
                ),
                true
        );
    }

    void print(String command, Object... args) {
        out.println(formatCommand(command, args));
    }

    private String formatCommand(String command, Object... parameters) {
        String[] escapedParameters = new String[parameters.length];
        for (int i = 0; i < escapedParameters.length; i++) {
            escapedParameters[i] = escape(parameters[i].toString());
        }

        return String.format(command, (Object[]) escapedParameters);
    }

    private String escape(String source) {
        if (source == null) {
            return "";
        }
        // https://www.jetbrains.com/help/teamcity/service-messages.html#Escaped+Values
        // TODO: Missing \\uXXXX
        return source
                .replace("|", "||")
                .replace("'", "|'")
                .replace("\n", "|n")
                .replace("\r", "|r")
                .replace("[", "|[")
                .replace("]", "|]");
    }

    @Override
    public void close() {
        out.close();
    }
}
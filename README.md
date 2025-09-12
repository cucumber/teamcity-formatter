[![Maven Central](https://img.shields.io/maven-central/v/io.cucumber/teamcity-formatter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:io.cucumber%20AND%20a:teamcity-formatter)

⚠️ This is an internal package; you don't need to install it in order to use the
TeamCity Formatter.

TeamCity Formatter
==================

Interspaces Cucumbers output
with [TeamCity Service Messages](https://www.jetbrains.com/help/teamcity/service-messages.html).
This enables IntelliJ IDEA to render all Cucumber scenarios in a tree-diagram.

<img width="2060" height="552" alt="Screenshot From 2025-09-12 15-30-43" src="https://github.com/user-attachments/assets/175e8f00-a5a9-4664-a616-f93ce1db6848" />


## Features and Limitations

### Print expected and actual values

For supported framework the output include the expected and actual value of an assertion.

| Framework  | AssertionError produced by                |
|------------|-------------------------------------------| 
| Hamcrest 3 | `MatcherAssert.assertThat(*, equalTo(*))` |
| AssertJ 3  | `Assertions.assertThat(*).isEqualTo(*)`   |
| JUnit 5    | `Assertions.assertEquals`                 |
| JUnit 4    | `Assert.assertEquals`                     |
| TestNG 7   | `Assert.assertEquals`                     |


### Parallel execution

The Service Message format does not support parallel execution. As a workaround,
the formatter supports writing the messages for the entire test execution after
the test execution has completed.

Messages will be written in canonical order i.e. features will be listed in
lexical uri order, scenarios from top to bottom. 

package io.cucumber.teamcityformatter;

import io.cucumber.messages.types.JavaMethod;
import io.cucumber.messages.types.JavaStackTraceElement;
import io.cucumber.messages.types.SourceReference;

import java.util.Optional;

final class SourceReferenceFormatter {

    private SourceReferenceFormatter(){
        // utility class
    }
    
    static Optional<String> formatLocation(SourceReference sourceReference) {
        if (sourceReference.getJavaMethod().isPresent()) {
            return sourceReference.getJavaMethod()
                    .map(SourceReferenceFormatter::formatJavaMethodLocation);
        }
        if (sourceReference.getJavaStackTraceElement().isPresent()) {
            return sourceReference.getJavaStackTraceElement()
                    .map(SourceReferenceFormatter::formatJavaStackTraceLocation);
        }
        return Optional.empty();
    }

    private static String formatJavaStackTraceLocation(JavaStackTraceElement javaStackTraceElement) {
        String fqClassName = javaStackTraceElement.getClassName();
        String methodName = javaStackTraceElement.getMethodName();
        return createJavaTestUri(fqClassName, sanitizeMethodName(fqClassName, methodName));
    }

    private static String formatJavaMethodLocation(JavaMethod javaMethod) {
        String fqClassName = javaMethod.getClassName();
        String methodName = javaMethod.getMethodName();
        return createJavaTestUri(fqClassName, methodName);
    }

    private static String createJavaTestUri(String fqClassName, String methodName) {
        // See:
        // https://github.com/JetBrains/intellij-community/blob/master/java/execution/impl/src/com/intellij/execution/testframework/JavaTestLocator.java
        return String.format("java:test://%s/%s", fqClassName, methodName);
    }

    private static String sanitizeMethodName(String fqClassName, String methodName) {
        if (!methodName.equals("<init>")) {
            return methodName;
        }
        // Replace constructor name, not recognized by IDEA.
        int classNameIndex = fqClassName.lastIndexOf('.');
        if (classNameIndex > 0) {
            return fqClassName.substring(classNameIndex + 1);
        }
        return fqClassName;
    }

    static Optional<String> formatMethodName(SourceReference sourceReference) {
        if (sourceReference.getJavaMethod().isPresent()) {
            return sourceReference.getJavaMethod()
                    .map(SourceReferenceFormatter::formatJavaMethodName);
        }
        if (sourceReference.getJavaStackTraceElement().isPresent()) {
            return sourceReference.getJavaStackTraceElement()
                    .map(SourceReferenceFormatter::formatJavaStackTraceName);
        }
        return Optional.empty();
    }

    private static String formatJavaStackTraceName(JavaStackTraceElement javaStackTraceElement) {
        String methodName = javaStackTraceElement.getMethodName();
        String fqClassName = javaStackTraceElement.getClassName();
        return sanitizeMethodName(fqClassName, methodName);
    }

    private static String formatJavaMethodName(JavaMethod javaMethod) {
        return javaMethod.getMethodName();
    }
}

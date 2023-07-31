package org.openrewrite.java.apache.httpclient5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.List;

public class UseTimeValue extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `TimeValue` class to define time values (duration)";
    }

    @Override
    public String getDescription() {
        return "Use `TimeValue` class to define time values (duration).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {


            List<MethodMatcher> methodMatchers  = Arrays.asList(
                    new MethodMatcher("org.apache.hc.core5.http.io.SocketConfig.Builder setSoLinger(int)")
            );

            JavaTemplate template = JavaTemplate.builder("TimeValue.ofMilliseconds(#{})")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpath(
                            "httpclient5", "httpcore5"
                    ))
                    .imports("org.apache.hc.core5.util.TimeValue")
                    .build();
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                if (methodMatches(m)) {
                    m = template.apply(updateCursor(m), m.getCoordinates().replaceArguments(), m.getArguments().get(0));
                    maybeAddImport("org.apache.hc.core5.util.TimeValue");
                }
                return m;
            }

            private boolean methodMatches(J.MethodInvocation method) {
                return methodMatchers.stream()
                        .anyMatch(matcher -> matcher.matches(method));
            }
        };
    }
}

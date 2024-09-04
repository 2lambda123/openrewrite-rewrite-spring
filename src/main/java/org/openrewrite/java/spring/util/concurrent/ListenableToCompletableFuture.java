/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.util.concurrent;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.RemoveRedundantTypeCast;
import org.openrewrite.staticanalysis.UseLambdaForFunctionalInterface;

public class ListenableToCompletableFuture extends JavaVisitor<ExecutionContext> {

    // These matcher patterns assume that ChangeType has already ran first; hence the use of CompletableFuture
    private static final MethodMatcher COMPLETABLE_MATCHER =
            new MethodMatcher("java.util.concurrent.CompletableFuture completable()");
    private static final MethodMatcher ADD_CALLBACK_SUCCESS_FAILURE_MATCHER =
            new MethodMatcher("java.util.concurrent.CompletableFuture addCallback(" +
                              "org.springframework.util.concurrent.SuccessCallback, " +
                              "org.springframework.util.concurrent.FailureCallback)");
    private static final MethodMatcher ADD_CALLBACK_LISTENABLE_FUTURE_CALLBACK_MATCHER =
            new MethodMatcher("java.util.concurrent.CompletableFuture addCallback(" +
                              "org.springframework.util.concurrent.ListenableFutureCallback)");


    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
        J.CompilationUnit cu = compilationUnit;
        cu = (J.CompilationUnit) new MemberReferenceToMethodInvocation().visit(cu, ctx);
        cu = (J.CompilationUnit) new ChangeType(
                "org.springframework.util.concurrent.ListenableFuture",
                "java.util.concurrent.CompletableFuture",
                null).getVisitor()
                .visit(cu, ctx, getCursor().getParent());
        cu = (J.CompilationUnit) super.visitCompilationUnit(cu, ctx);
        cu = (J.CompilationUnit) new UseLambdaForFunctionalInterface().getVisitor().visit(cu, ctx);
        cu = (J.CompilationUnit) new RemoveRedundantTypeCast().getVisitor().visit(cu, ctx); // XXX Should not necessary & fails
        return cu;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

        if (COMPLETABLE_MATCHER.matches(mi)) {
            return mi.getSelect().withPrefix(mi.getPrefix());
        }
        if (ADD_CALLBACK_LISTENABLE_FUTURE_CALLBACK_MATCHER.matches(mi)) {
            return new ListenableFutureCallbackToBiConsumerVisitor().visitNonNull(mi, new InMemoryExecutionContext());
        }
        if (ADD_CALLBACK_SUCCESS_FAILURE_MATCHER.matches(mi)) {
            return replaceSuccessFailureCallback(mi);
        }
        return mi;
    }

    private J.MethodInvocation replaceSuccessFailureCallback(J.MethodInvocation mi) {
        Expression successCallback = mi.getArguments().get(0);
        Expression failureCallback = mi.getArguments().get(1);

        // TODO build up template from success/failureCallback arguments
        return JavaTemplate.builder("whenComplete(new BiConsumer<String, Throwable>() {\n" +
                                    "            @Override\n" +
                                    "            public void accept(String string, Throwable ex) {\n" +
                                    "                if (ex == null) {\n" +
                                    "                    System.out.println(string);\n" +
                                    "                } else {\n" +
                                    "                    System.err.println(ex.getMessage());\n" +
                                    "                }\n" +
                                    "            }\n" +
                                    "        })")
                .doBeforeParseTemplate(System.out::println) // XXX remove
                .contextSensitive()
                .imports("java.util.function.BiConsumer")
                .build()
                .apply(getCursor(), mi.getCoordinates().replaceMethod());
    }
}

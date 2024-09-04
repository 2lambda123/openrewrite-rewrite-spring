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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class ListenableToCompletableFutureTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(ListenableToCompletableFuture::new))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-core-6"));
    }

    @Test
    @DocumentExample
    void addListenableFutureCallback() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.concurrent.ListenableFuture;
              import org.springframework.util.concurrent.ListenableFutureCallback;
              class A {
                  void test(ListenableFuture<String> future) {
                      future.addCallback(new ListenableFutureCallback<String>() {
                          @Override
                          public void onSuccess(String result) {
                              System.out.println(result);
                          }
              
                          @Override
                          public void onFailure(Throwable ex) {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """,
            """
              import java.util.concurrent.CompletableFuture;

              class A {
                  void test(CompletableFuture<String> future) {
                      future.whenComplete((result, ex) -> {
                          if (ex == null) {
                              System.out.println(result);
                          } else {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void addListenableFutureCallbackWithLocalField() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.concurrent.ListenableFuture;
              import org.springframework.util.concurrent.ListenableFutureCallback;
              class A {
                  void test(ListenableFuture<String> future) {
                      future.addCallback(new ListenableFutureCallback<String>() {

                          private final String field = "value";

                          @Override
                          public void onSuccess(String result) {
                              System.out.println(result);
                          }
              
                          @Override
                          public void onFailure(Throwable ex) {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """,
            """
              import java.util.concurrent.CompletableFuture;
              import java.util.function.BiConsumer;
              
              class A {
                  void test(CompletableFuture<String> future) {
                      future.whenComplete(new BiConsumer<String, Throwable>() {
              
                          private final String field = "value";
              
                          @Override
                          public void accept(String result, Throwable ex) {
                              if (ex == null) {
                                  System.out.println(result);
                              } else {
                                  System.err.println(ex.getMessage());
                              }
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void addSuccessFailureCallback() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.util.concurrent.ListenableFuture;
              class A {
                  void test(ListenableFuture<String> future) {
                      future.addCallback(
                          System.out::println,
                          ex -> System.err.println(ex.getMessage()));
                  }
              }
              """,
            """
              import java.util.concurrent.CompletableFuture;

              class A {
                  void test(CompletableFuture<String> future) {
                      future.whenComplete((string, ex) -> {
                          if (ex == null) {
                              System.out.println(string);
                          } else {
                              System.err.println(ex.getMessage());
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void completable() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.concurrent.CompletableFuture;
              import org.springframework.util.concurrent.ListenableFuture;

              class A {
                  CompletableFuture<String> test(ListenableFuture<String> future) {
                      return future.completable();
                  }
              }
              """,
            """
              import java.util.concurrent.CompletableFuture;

              class A {
                  CompletableFuture<String> test(CompletableFuture<String> future) {
                      return future;
                  }
              }
              """
          )
        );
    }
}

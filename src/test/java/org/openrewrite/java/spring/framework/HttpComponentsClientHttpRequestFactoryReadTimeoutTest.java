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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HttpComponentsClientHttpRequestFactoryReadTimeoutTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "spring-beans-5",
            "spring-boot-3.1",
            "spring-web-5",
            "httpclient-4",
            "httpcore-4"));
    }

    @Test
    @DocumentExample
    void migrateHttpComponentsClientHttpRequestFactoryReadTimeout() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.http.config.Registry;
              import org.apache.http.config.RegistryBuilder;
              import org.apache.http.conn.socket.ConnectionSocketFactory;
              import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  clientHttpRequestFactory.setReadTimeout(30000);
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """,
            """
              import org.apache.hc.core5.http.config.Registry;
              import org.apache.hc.core5.http.config.RegistryBuilder;
              import org.apache.hc.core5.http.io.SocketConfig;
              import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
              import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
              import org.springframework.boot.web.client.RestTemplateBuilder;
              import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
              import org.springframework.web.client.RestTemplate;

              import java.util.concurrent.TimeUnit;

              class RestContextInitializer {
                  RestTemplate getRestTemplate() throws Exception {
                      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create().build();
                      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(30000, TimeUnit.MILLISECONDS).build());

                      return new RestTemplateBuilder()
                              .requestFactory(() -> {
                                  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
                                  // ... set poolingConnectionManager on HttpClient
                                  return clientHttpRequestFactory;
                              })
                              .build();
                  }
              }
              """
          )
        );
    }

    // TODO Additional scenarios not yet covered
    // - Using BasicHttpClientConnectionManager
    // - Using PoolingHttpClientConnectionManagerBuilder
    // - No HttpClientConnectionManager at all
    // - No intermediate variable for connectionManager
    // - setReadTimeout called with local variable not accessible near connection manager
    // - there already is a setDefaultSocketConfig call

}

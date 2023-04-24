/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.batch;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("RedundantThrows")
class MigrateItemWriterWriteTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateItemWriterWrite())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-batch-core-4.3.+", "spring-batch-infrastructure-4.3.+", "spring-beans-4.3.30.RELEASE"));
    }

    @Test
    void replaceItemWriterWriteMethod() {
        // language=java
        rewriteRun(
          java("""
            import java.util.List;
            import org.springframework.batch.item.ItemWriter;
            
            public class ConsoleItemWriter<T> implements ItemWriter<T> {
            
                @Override
                public void write(final List<? extends T> items) throws Exception {
                    for (final T item : items) {
                        System.out.println(item.toString());
                    }
                }
            }
            """, """
            import org.springframework.batch.item.Chunk;
            import org.springframework.batch.item.ItemWriter;
            
            public class ConsoleItemWriter<T> implements ItemWriter<T> {
            
                @Override
                public void write(final Chunk<? extends T> items) throws Exception {
                    for (final T item : items) {
                        System.out.println(item.toString());
                    }
                }
            }
            """)
        );
    }

    @Test
    void replaceExtendedItemWriterWriteMethod() {
        // language=java
        rewriteRun(
          java("""
            import java.util.List;
            
            import org.springframework.batch.item.database.JdbcBatchItemWriter;
            
            public class ExtendedJdbcBatchItemWriter extends JdbcBatchItemWriter<String> {
            
                public void write(List<? extends String> a) throws Exception {
                    super.write(a);
                }
            }
            """, """
            import org.springframework.batch.item.Chunk;
            import org.springframework.batch.item.database.JdbcBatchItemWriter;
            
            public class ExtendedJdbcBatchItemWriter extends JdbcBatchItemWriter<String> {
            
                @Override
                public void write(Chunk<? extends String> a) throws Exception {
                    super.write(a);
                }
            }
            """)
        );
    }

    @Test
    void abstractClass() {
        // language=java
        rewriteRun(
          java("""
            import java.util.List;
            import org.springframework.batch.item.ItemWriter;
            
            public abstract class AbstractItemWriter<T> implements ItemWriter<T> {
            
                @Override
                public abstract void write(final List<? extends T> items) throws Exception;
            }
            """, """
            import org.springframework.batch.item.Chunk;
            import org.springframework.batch.item.ItemWriter;
            
            public abstract class AbstractItemWriter<T> implements ItemWriter<T> {
            
                @Override
                public abstract void write(final Chunk<? extends T> items) throws Exception;
            }
            """)
        );
    }
}

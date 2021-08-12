/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.spring

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class NoAutowiredTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-beans")
            .build()

    override val recipe: Recipe
        get() = NoAutowiredOnConstructor()

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun removeLeadingAutowiredAnnotation() = assertChanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            
            @Autowired
            public class TestConfiguration {
                private final TestSourceA testSourceA;
                private TestSourceB testSourceB;
            
                @Autowired
                private TestSourceC testSourceC;
            
                @Autowired
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            
                @Autowired
                public void setTestSourceB(TestSourceB testSourceB) {
                    this.testSourceB = testSourceB;
                }
            }
            
            @Component
            public class TestSourceA {
            }
            
            @Component
            public class TestSourceB {
            }
            
            @Component
            public class TestSourceC {
            }
        """,
        after = """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            
            @Autowired
            public class TestConfiguration {
                private final TestSourceA testSourceA;
                private TestSourceB testSourceB;
            
                @Autowired
                private TestSourceC testSourceC;
            
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            
                @Autowired
                public void setTestSourceB(TestSourceB testSourceB) {
                    this.testSourceB = testSourceB;
                }
            }
            
            @Component
            public class TestSourceA {
            }
            
            @Component
            public class TestSourceB {
            }
            
            @Component
            public class TestSourceC {
            }
        """,
        typeValidation = { identifiers = false }
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun removeLeadingAutowiredAnnotationNoModifiers() = assertChanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            
            public class TestConfiguration {
                private final TestSourceA testSourceA;
                private TestSourceB testSourceB;
            
                @Autowired
                private TestSourceC testSourceC;
            
                @Autowired
                TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            
                @Autowired
                public void setTestSourceB(TestSourceB testSourceB) {
                    this.testSourceB = testSourceB;
                }
            }
            
            @Component
            public class TestSourceA {
            }
            
            @Component
            public class TestSourceB {
            }
            
            @Component
            public class TestSourceC {
            }
        """,
        after = """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            
            public class TestConfiguration {
                private final TestSourceA testSourceA;
                private TestSourceB testSourceB;
            
                @Autowired
                private TestSourceC testSourceC;
            
                TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            
                @Autowired
                public void setTestSourceB(TestSourceB testSourceB) {
                    this.testSourceB = testSourceB;
                }
            }
            
            @Component
            public class TestSourceA {
            }
            
            @Component
            public class TestSourceB {
            }
            
            @Component
            public class TestSourceC {
            }
        """,
        typeValidation = { identifiers = false }
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun removeAutowiredWithMultipleAnnotation() = assertChanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Required;
            import org.springframework.stereotype.Component;
            
            public class AnnotationPos1 {
                private final TestSourceA testSourceA;
            
                @Autowired
                @Required
                @Qualifier
                public AnnotationPos1(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos2 {
                private final TestSourceA testSourceA;
            
                @Required
                @Autowired
                @Qualifier
                public AnnotationPos2(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos3 {
                private final TestSourceA testSourceA;
            
                @Required
                @Qualifier
                @Autowired
                public AnnotationPos3(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            @Component
            public class TestSourceA {
            }
        """,
        after = """
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Required;
            import org.springframework.stereotype.Component;
            
            public class AnnotationPos1 {
                private final TestSourceA testSourceA;
            
                @Required
                @Qualifier
                public AnnotationPos1(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos2 {
                private final TestSourceA testSourceA;
            
                @Required
                @Qualifier
                public AnnotationPos2(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos3 {
                private final TestSourceA testSourceA;
            
                @Required
                @Qualifier
                public AnnotationPos3(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            @Component
            public class TestSourceA {
            }
        """,
        typeValidation = { identifiers = false }
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun removeAutowiredWithMultipleInLineAnnotation() = assertChanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Required;
            import org.springframework.stereotype.Component;
            
            public class AnnotationPos1 {
                private final TestSourceA testSourceA;
            
                @Autowired @Required @Qualifier
                public AnnotationPos1(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos2 {
                private final TestSourceA testSourceA;
            
                @Required @Autowired @Qualifier
                public AnnotationPos2(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos3 {
                private final TestSourceA testSourceA;
            
                @Required @Qualifier @Autowired
                public AnnotationPos3(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            @Component
            public class TestSourceA {
            }
        """,
        after = """
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Required;
            import org.springframework.stereotype.Component;
            
            public class AnnotationPos1 {
                private final TestSourceA testSourceA;
            
                @Required @Qualifier
                public AnnotationPos1(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos2 {
                private final TestSourceA testSourceA;
            
                @Required @Qualifier
                public AnnotationPos2(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos3 {
                private final TestSourceA testSourceA;
            
                @Required @Qualifier
                public AnnotationPos3(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            @Component
            public class TestSourceA {
            }
        """,
        typeValidation = { identifiers = false }
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun oneNamePrefixAnnotation() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public @Autowired DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun multipleNamePrefixAnnotationsPos1() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public @Autowired @Deprecated DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public @Deprecated DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun multipleNamePrefixAnnotationsPos2() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public @SuppressWarnings("") @Autowired @Deprecated DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public @SuppressWarnings("") @Deprecated DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun multipleNamePrefixAnnotationsPos3() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public @SuppressWarnings("") @Deprecated @Autowired DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public @SuppressWarnings("") @Deprecated DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun keepAutowiredAnnotationsWhenMultipleConstructorsExist() = assertUnchanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.core.io.Resource;
            import java.io.PrintStream;
            
            public class MyAppResourceService {
                private final Resource someResource;
                private final PrintStream printStream;
            
                public MyAppResourceService(Resource someResource) {
                    this.someResource = someResource;
                    this.printStream = System.out;
                }
            
                @Autowired
                public MyAppResourceService(Resource someResource, PrintStream printStream) {
                    this.someResource = someResource;
                    this.printStream = printStream;
                }
            }
        """
    )

    @Test
    fun optionalAutowiredAnnotations() = assertUnchanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;

                public DatabaseConfiguration(@Autowired(required = false) DataSource dataSource) {
                }
            }
        """
    )

    @Test
    fun noAutowiredAnnotations() = assertUnchanged(
        before = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;

                @Primary
                public DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )
}

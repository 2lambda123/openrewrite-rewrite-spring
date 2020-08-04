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
package org.openrewrite.spring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactoringVisitorTests
import org.openrewrite.java.JavaParser

class NoRequestMappingAnnotationTest: RefactoringVisitorTests<JavaParser> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath(JavaParser.dependenciesFromClasspath("mockito-all", "junit"))
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(NoRequestMappingAnnotation())

    @Test
    fun requestMapping() = assertRefactored(
            before = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import static org.springframework.web.bind.annotation.RequestMethod.GET;
                import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = HEAD)
                    public ResponseEntity<List<String>> getUsersHead() {
                        return null;
                    }
                
                    @RequestMapping(method = GET)
                    public ResponseEntity<List<String>> getUsers() {
                        return null;
                    }
    
                    @RequestMapping(path = "/{id}", method = RequestMethod.GET)
                    public ResponseEntity<String> getUser(@PathVariable("id") Long id) {
                        return null;
                    }
                    
                    @RequestMapping
                    public ResponseEntity<List<String>> getUsersNoRequestMethod() {
                        return null;
                    }
                }
            """,
            after = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = HEAD)
                    public ResponseEntity<List<String>> getUsersHead() {
                        return null;
                    }
                
                    @GetMapping
                    public ResponseEntity<List<String>> getUsers() {
                        return null;
                    }
    
                    @GetMapping("/{id}")
                    public ResponseEntity<String> getUser(@PathVariable("id") Long id) {
                        return null;
                    }
                    
                    @GetMapping
                    public ResponseEntity<List<String>> getUsersNoRequestMethod() {
                        return null;
                    }
                }
            """
    )

    @Issue("#3")
    @Test
    fun requestMappingWithMultipleMethods() = assertRefactored(
            before = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import static org.springframework.web.bind.annotation.RequestMethod.GET;
                import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = { HEAD, GET })
                    public ResponseEntity<List<String>> getUsersHead() {
                        return null;
                    }
                }
            """,
            after = """
                import java.util.*;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import static org.springframework.web.bind.annotation.RequestMethod.GET;
                import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
                
                @RestController
                @RequestMapping("/users")
                public class UsersController {
                    @RequestMapping(method = { HEAD, GET })
                    public ResponseEntity<List<String>> getUsersHead() {
                        return null;
                    }
                }
            """
    )
}

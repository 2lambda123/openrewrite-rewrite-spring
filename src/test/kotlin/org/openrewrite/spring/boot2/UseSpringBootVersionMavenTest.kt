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
package org.openrewrite.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.xml.XmlParser
import org.openrewrite.xml.tree.Xml

class UseSpringBootVersionMavenTest : RefactorVisitorTestForParser<Xml.Document> {

    override val parser: XmlParser = XmlParser()

    private val useLatestSpringBoot = UseSpringBootVersionMaven()
            .apply {
                setVersion("2.+")
            }

    override val visitors: Iterable<RefactorVisitor<*>> = listOf(useLatestSpringBoot)

    @Test
    fun upgradeSpringBootVersion() = assertRefactored(
            before = """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.0.0.RELEASE</version>
                  </parent>
                </project>
            """,
            after = {
                """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>${useLatestSpringBoot.latestMatchingVersion}</version>
                  </parent>
                </project>
            """
            }
    )
}

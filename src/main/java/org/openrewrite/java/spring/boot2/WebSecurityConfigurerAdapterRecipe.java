/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author Alex Boyko
 */
public class WebSecurityConfigurerAdapterRecipe extends Recipe {

    private static final Collection<J.Modifier.Type> EXPLICIT_ACCESS_LEVELS = Arrays.asList(J.Modifier.Type.Public,
            J.Modifier.Type.Private, J.Modifier.Type.Protected);

    private static final String FQN_CONFIGURATION = "org.springframework.context.annotation.Configuration";
    private static final String FQN_WEB_SECURITY_CONFIGURER_ADAPTER = "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter";
    private static final String FQN_SECURITY_FILTER_CHAIN = "org.springframework.security.web.SecurityFilterChain";
    private static final String FQN_OVERRIDE = "java.lang.Override";
    private static final String FQN_WEB_SECURITY_CUSTOMIZER = "org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer";
    private static final String BEAN_PKG = "org.springframework.context.annotation";
    private static final String BEAN_SIMPLE_NAME = "Bean";
    private static final String FQN_BEAN = BEAN_PKG + "." + BEAN_SIMPLE_NAME;
    private static final String BEAN_ANNOTATION = "@" + BEAN_SIMPLE_NAME;

    private static final MethodMatcher CONFIGURE_HTTP_SECURITY_METHOD_MATCHER =
            new MethodMatcher("* configure(org.springframework.security.config.annotation.web.builders.HttpSecurity)");
    private static final MethodMatcher CONFIGURE_WEB_SECURITY_METHOD_MATCHER =
            new MethodMatcher("* configure(org.springframework.security.config.annotation.web.builders.WebSecurity)");
    private static final MethodMatcher CONFIGURE_AUTH_MANAGER_SECURITY_METHOD_MATCHER =
            new MethodMatcher("* configure(org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder)");

    private static final List<MethodMatcher> APPLICABLE_METHODS_MATCHERS = Arrays.asList(
            CONFIGURE_HTTP_SECURITY_METHOD_MATCHER,
            CONFIGURE_WEB_SECURITY_METHOD_MATCHER
//            CONFIGURE_AUTH_MANAGER_SECURITY_METHOD_MATCHER
    );

    private static final String MSG_QUALIFIES = "qualifies";

    @Override
    public String getDisplayName() {
        return "Spring Security 5.4 introduces the ability to configure HttpSecurity by creating a SecurityFilterChain bean";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>(FQN_WEB_SECURITY_CONFIGURER_ADAPTER);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
                if (isApplicableClass(classDecl)) {
                    getCursor().putMessage(MSG_QUALIFIES, true);
                    maybeRemoveImport(FQN_WEB_SECURITY_CONFIGURER_ADAPTER);
                    return super.visitClassDeclaration(classDecl, context).withExtends(null);
                }
                return super.visitClassDeclaration(classDecl, context);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext context) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, context);
                Cursor classCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                if (classCursor.getMessage(MSG_QUALIFIES, false)) {
                    if (CONFIGURE_HTTP_SECURITY_METHOD_MATCHER.matches(m, classCursor.getValue())) {
                        JavaType securityChainType = JavaType.buildType(FQN_SECURITY_FILTER_CHAIN);
                        JavaType.Method type = m.getMethodType();
                        if (type != null) {
                            type = type.withName("filterChain").withReturnType(securityChainType);
                        }

                        Space returnPrefix = m.getReturnTypeExpression() == null ? Space.EMPTY : m.getReturnTypeExpression().getPrefix();
                        m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), anno -> {
                                    if (TypeUtils.isOfClassType(anno.getType(), FQN_OVERRIDE)) {
                                        maybeRemoveImport(FQN_OVERRIDE);
                                        return null;
                                    }
                                    return anno;
                                }))
                                .withReturnTypeExpression(new J.Identifier(Tree.randomId(), returnPrefix, Markers.EMPTY,"SecurityFilterChain", securityChainType, null))
                                .withName(m.getName().withSimpleName("filterChain"))
                                .withMethodType(type)
                                .withModifiers(ListUtils.map(m.getModifiers(), (modifier) -> EXPLICIT_ACCESS_LEVELS.contains(modifier.getType()) ? null : modifier));

                        m = addBeanAnnotation(m, getCursor());
                        maybeAddImport(FQN_SECURITY_FILTER_CHAIN);
                    } else if (CONFIGURE_WEB_SECURITY_METHOD_MATCHER.matches(m, classCursor.getValue())) {
                        JavaType securityCustomizerType = JavaType.buildType(FQN_WEB_SECURITY_CUSTOMIZER);
                        JavaType.Method type = m.getMethodType();
                        if (type != null) {
                            type = type.withName("webSecurityCustomizer").withReturnType(securityCustomizerType);
                        }
                        Space returnPrefix = m.getReturnTypeExpression() == null ? Space.EMPTY : m.getReturnTypeExpression().getPrefix();
                        m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), anno -> {
                                    if (TypeUtils.isOfClassType(anno.getType(), FQN_OVERRIDE)) {
                                        maybeRemoveImport(FQN_OVERRIDE);
                                        return null;
                                    }
                                    return anno;
                                }))
                                .withMethodType(type)
                                .withParameters(Collections.emptyList())
                                .withReturnTypeExpression(new J.Identifier(Tree.randomId(), returnPrefix, Markers.EMPTY,"WebSecurityCustomizer", securityCustomizerType, null))
                                .withName(m.getName().withSimpleName("webSecurityCustomizer"))
                                .withModifiers(ListUtils.map(m.getModifiers(), (modifier) -> EXPLICIT_ACCESS_LEVELS.contains(modifier.getType()) ? null : modifier));

                        m = addBeanAnnotation(m, getCursor());
                        maybeAddImport(FQN_WEB_SECURITY_CUSTOMIZER);
                    } else if (CONFIGURE_AUTH_MANAGER_SECURITY_METHOD_MATCHER.matches(m, classCursor.getValue())) {
                        // TODO: implement this case
                        m = m.withMarkers(m.getMarkers().searchResult("Migrate manually based on https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter"));
                    }
                }
                return m;
            }

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext context) {
                J.Block b = super.visitBlock(block, context);
                if (getCursor().getParent() != null && getCursor().getParent().getValue() instanceof J.MethodDeclaration) {
                    J.MethodDeclaration parentMethod = getCursor().getParent().getValue();
                    Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                    J.ClassDeclaration classDecl = classDeclCursor.getValue();
                    if (classDeclCursor.getMessage(MSG_QUALIFIES, false)) {
                        if (CONFIGURE_HTTP_SECURITY_METHOD_MATCHER.matches(parentMethod, classDecl)) {
                            JavaTemplate template = JavaTemplate.builder(this::getCursor,  "return #{any(org.springframework.security.config.annotation.SecurityBuilder)}.build();")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn("package org.springframework.security.config.annotation;" +
                                                    "public interface SecurityBuilder<O> {\n" +
                                                    "    O build() throws Exception;" +
                                                    "}")
                                            .build()).imports("org.springframework.security.config.annotation.SecurityBuilder").build();
                            b = b.withTemplate(template, b.getCoordinates().lastStatement(),
                                    ((J.VariableDeclarations)parentMethod.getParameters().get(0)).getVariables().get(0).getName());
                        } else if (CONFIGURE_WEB_SECURITY_METHOD_MATCHER.matches(parentMethod, classDecl)) {
                            String t = "return (" + ((J.VariableDeclarations)parentMethod.getParameters().get(0)).getVariables().get(0).getName().getSimpleName() + ") -> #{any()};";
                            JavaTemplate template = JavaTemplate.builder(this::getCursor, t).javaParser(() -> JavaParser.fromJavaVersion().build()).build();
                            b = b.withTemplate(template, b.getCoordinates().firstStatement(), b);
                            b = b.withStatements(ListUtils.map(b.getStatements(), (index, stmt) -> {
                                if (index == 0){
                                    return stmt;
                                }
                                return null;
                            }));
                        }
                    }
                }
                return b;
            }

            private J.MethodDeclaration addBeanAnnotation(J.MethodDeclaration m, Cursor c) {
                maybeAddImport(FQN_BEAN);
                JavaTemplate template = JavaTemplate.builder(() -> c, BEAN_ANNOTATION).imports(FQN_BEAN).javaParser(() -> JavaParser.fromJavaVersion()
                        .dependsOn("package " + BEAN_PKG + "; public @interface " + BEAN_SIMPLE_NAME + " {}").build()).build();
                return m.withTemplate(template, m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

            private boolean isApplicableClass(J.ClassDeclaration classDecl) {

                if (TypeUtils.isAssignableTo(FQN_WEB_SECURITY_CONFIGURER_ADAPTER, classDecl.getType())
                        && classDecl.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), FQN_CONFIGURATION))) {

                    AtomicBoolean foundNonApplicableMethodUsage = new AtomicBoolean();
                    new JavaIsoVisitor<AtomicBoolean>() {

                        private void checkNonApplicableMethod(@Nullable JavaType.Method m, AtomicBoolean found) {
                            if (m != null && !found.get()) {
                                J.ClassDeclaration enclosingClassDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
                                if (enclosingClassDecl == classDecl || !enclosingClassDecl.hasModifier(J.Modifier.Type.Static)) {
                                    if (TypeUtils.isAssignableTo(FQN_WEB_SECURITY_CONFIGURER_ADAPTER, m.getDeclaringType())
                                            && !APPLICABLE_METHODS_MATCHERS.stream().anyMatch(matcher -> matcher.matches(m))) {
                                        found.set(true);
                                    }
                                }
                            }
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                           checkNonApplicableMethod(method.getMethodType(), found);
                           return found.get() ? method : super.visitMethodInvocation(method, found);
                        }

                        @Override
                        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, AtomicBoolean found) {
                            checkNonApplicableMethod(method.getMethodType(), found);
                            return found.get() ? method : super.visitMethodDeclaration(method, found);
                        }

                        @Override
                        public J.MemberReference visitMemberReference(J.MemberReference memberRef, AtomicBoolean found) {
                            checkNonApplicableMethod(memberRef.getMethodType(), found);
                            return found.get() ? memberRef : super.visitMemberReference(memberRef, found);
                        }

                    }.visit(classDecl, foundNonApplicableMethodUsage);

                    return !foundNonApplicableMethodUsage.get();
                }
                return false;

            }

        };
    }

}

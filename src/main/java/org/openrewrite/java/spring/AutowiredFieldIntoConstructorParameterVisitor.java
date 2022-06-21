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
package org.openrewrite.java.spring;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.Empty;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;


public class AutowiredFieldIntoConstructorParameterVisitor extends JavaVisitor<ExecutionContext> {

    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    final private String classFqName;

    final private String fieldName;

    public AutowiredFieldIntoConstructorParameterVisitor(String classFqName, String fieldName) {
        this.classFqName = classFqName;
        this.fieldName = fieldName;
    }

    @Override
    public J visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext p) {
        if (TypeUtils.isOfClassType(classDecl.getType(), classFqName)) {
            List<MethodDeclaration> constructors = classDecl.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .filter(m -> m.isConstructor())
                    .collect(Collectors.toList());
            boolean applicable = false;
            if (constructors.isEmpty()) {
                applicable = true;
            } else if (constructors.size() == 1) {
                getCursor().putMessage("applicableConstructor", constructors.get(0));
                applicable = true;
            } else {
                List<MethodDeclaration> autowiredConstructors = constructors.stream().filter(constr -> constr.getLeadingAnnotations().stream()
                                .map(a -> TypeUtils.asFullyQualified(a.getType()))
                                .filter(Objects::nonNull)
                                .map(fq -> fq.getFullyQualifiedName())
                                .filter(fqn -> AUTOWIRED.equals(fqn))
                                .findFirst()
                                .isPresent()
                        )
                        .limit(2)
                        .collect(Collectors.toList());
                if (autowiredConstructors.size() == 1) {
                    getCursor().putMessage("applicableConstructor", autowiredConstructors.get(0));
                    applicable = true;
                }
            }
            if (applicable) {
                // visit contents if there is applicable constructor
                return super.visitClassDeclaration(classDecl, p);
            }
        }
        return classDecl;
    }

    @Override
    public J visitVariableDeclarations(VariableDeclarations multiVariable, ExecutionContext p) {
        Cursor blockCursor = getCursor().dropParentUntil(Block.class::isInstance);
        VariableDeclarations mv = multiVariable;
        if (blockCursor != null && blockCursor.getParent().getValue() instanceof ClassDeclaration
                && multiVariable.getVariables().size() == 1
                && fieldName.equals(multiVariable.getVariables().get(0).getName().printTrimmed())) {

            mv = (VariableDeclarations) new RemoveAnnotationVisitor(new AnnotationMatcher("@" + AUTOWIRED)).visit(multiVariable, p);
            if (mv != multiVariable) {
                maybeRemoveImport(AUTOWIRED);
                MethodDeclaration constructor = blockCursor.getParent().getMessage("applicableConstructor", null);
                ClassDeclaration c = blockCursor.getParent().getValue();
                if (constructor == null) {
                    doAfterVisit(new AddConstructorVisitor(c.getSimpleName(), fieldName, multiVariable.getTypeExpression()));
                } else {
                    doAfterVisit(new AddConstructorParameterAndAssignment(constructor, fieldName, multiVariable.getTypeExpression()));
                }
            }
        }
        return mv;
    }


    private static class AddConstructorVisitor extends JavaVisitor<ExecutionContext> {
        private String className;
        private String fieldName;
        private TypeTree type;

        public AddConstructorVisitor(String className, String fieldName, TypeTree type) {
            this.className = className;
            this.fieldName = fieldName;
            this.type = type;
        }

        @Override
        public J visitBlock(Block block, ExecutionContext p) {
            if (getCursor().getParent() != null) {
                Object n = getCursor().getParent().getValue();
                if (n instanceof ClassDeclaration) {
                    ClassDeclaration classDecl = (ClassDeclaration) n;
                    if (classDecl.getKind() == ClassDeclaration.Kind.Type.Class && className.equals(classDecl.getSimpleName())) {
                        JavaTemplate.Builder template = JavaTemplate.builder(() -> getCursor(), ""
                                + classDecl.getSimpleName() + "(" + type.printTrimmed() + " " + fieldName + ") {\n"
                                + "this." + fieldName + " = " + fieldName + ";\n"
                                + "}\n"
                        );
                        FullyQualified fq = TypeUtils.asFullyQualified(type.getType());
                        if (fq != null) {
                            template.imports(fq.getFullyQualifiedName());
                            maybeAddImport(fq);
                        }
                        Optional<Statement> firstMethod = block.getStatements().stream().filter(MethodDeclaration.class::isInstance).findFirst();
                        if (firstMethod.isPresent()) {
                            return block.withTemplate(template.build(), firstMethod.get().getCoordinates().before());
                        } else {
                            return block.withTemplate(template.build(), block.getCoordinates().lastStatement());
                        }
                    }
                }
            }
            return block;
        }
    }

    private static class AddConstructorParameterAndAssignment extends JavaIsoVisitor<ExecutionContext> {

        private MethodDeclaration constructor;
        private String fieldName;
        private TypeTree type;

        public AddConstructorParameterAndAssignment(MethodDeclaration constructor, String fieldName, TypeTree type) {
            this.constructor = constructor;
            this.fieldName = fieldName;
            this.type = type;
        }

        @Override
        public MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext p) {
            if (method == this.constructor) {
                String paramsStr = Stream.concat(method.getParameters().stream().filter(s -> !Empty.class.isInstance(s)).map(s -> s.printTrimmed()), Stream.of(type.printTrimmed() + " " + fieldName)).collect(Collectors.joining(", "));
                JavaTemplate.Builder paramsTemplate = JavaTemplate.builder(() -> getCursor(), paramsStr);
                JavaTemplate.Builder statementTemplate = JavaTemplate.builder(() -> getCursor(), "this." + fieldName + " = " + fieldName + ";");
                method = method
                        .withTemplate(paramsTemplate.build(), method.getCoordinates().replaceParameters())
                        .withTemplate(statementTemplate.build(), method.getBody().getCoordinates().lastStatement());
            }
            return method;
        }
    }

}

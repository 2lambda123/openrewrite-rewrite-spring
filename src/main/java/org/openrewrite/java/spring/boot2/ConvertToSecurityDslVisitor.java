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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.Markup;

import java.util.*;

public class ConvertToSecurityDslVisitor<P> extends JavaIsoVisitor<P> {

    private static final String MSG_FLATTEN_CHAIN = "http-security-dsl-flatten-invocation-chain";

    private static final String MSG_TOP_INVOCATION = "top-method-invocation";

    private final String securityFqn;

    private final Collection<String> convertableMethods;

    public ConvertToSecurityDslVisitor(String securityFqn, Collection<String> convertableMethods) {
        this.securityFqn = securityFqn;
        this.convertableMethods = convertableMethods;
    }

    @Override
    public boolean isAcceptable(SourceFile sourceFile, P p) {
        return new UsesType<>(securityFqn, true).isAcceptable(sourceFile, p);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation initialMethod, P executionContext) {
        J.MethodInvocation method = super.visitMethodInvocation(initialMethod, executionContext);
        if (isApplicableMethod(method)) {
            final List<J.MethodInvocation> chain = computeAndMarkChain(method);
            JavaType.FullyQualified httpSecurityType = method.getMethodType().getDeclaringType();
            final String methodName = method.getSimpleName();
            final J.MethodInvocation m = method;
            method = httpSecurityType.getMethods().stream().filter(aMethod -> methodName.equals(aMethod.getName()) && aMethod.getParameterTypes().size() == 1).findFirst().map(newMethodType -> {
                String paramName = generateParamNameFromMethodName(m.getSimpleName());
                return m
                        .withMethodType(newMethodType)
                        .withArguments(Collections.singletonList(chain.isEmpty() ? createDefaultsCall(newMethodType.getParameterTypes().get(0)) : createLambdaParam(paramName, newMethodType.getParameterTypes().get(0), chain)));
            }).orElse(method);
        }
        Boolean msg = getCursor().pollMessage(MSG_FLATTEN_CHAIN);
        if (Boolean.TRUE.equals(msg)) {
            method = (J.MethodInvocation) method.getSelect();
        }
        // Auto-format the top invocation call if anything has changed down the tree
        if (initialMethod != method && (getCursor().getParent(2) == null || !(getCursor().getParent(2).getValue() instanceof J.MethodInvocation))) {
            method = autoFormat(method, executionContext);
        }
        return method;
    }

    private static String generateParamNameFromMethodName(String n) {
        int i = n.length() - 1;
        for (; i >= 0 && Character.isLowerCase(n.charAt(i)); i--) {}
        if (i >= 0) {
            return StringUtils.uncapitalize(i == 0 ? n : n.substring(i));
        }
        return n;
    }

    private J.Lambda createLambdaParam(String paramName, JavaType paramType, List<J.MethodInvocation> chain) {
        J.Identifier param = createIdentifier(paramName, paramType);
        J.MethodInvocation body = unfoldMethodInvocationChain(createIdentifier(paramName, paramType), chain);
        return new J.Lambda(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                new J.Lambda.Parameters(Tree.randomId(), Space.EMPTY, Markers.EMPTY, false, Collections.singletonList(new JRightPadded<>(param, Space.EMPTY, Markers.EMPTY))),
                Space.build(" ", Collections.emptyList()),
                body,
                JavaType.Primitive.Void
        );
    }

    private J.Identifier createIdentifier(String name, JavaType type) {
        return new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, name, type, null);
    }

    private J.MethodInvocation unfoldMethodInvocationChain(J.Identifier core, List<J.MethodInvocation> chain) {
        Expression select = core;
        J.MethodInvocation invocation = null;
        for (J.MethodInvocation inv : chain) {
            invocation = inv.withSelect(select);
            select = invocation;
        }
        // Check if top-level invocation to remove the prefix as the prefix is space before the root call, i.e. before httpSecurity identifier. We don't want to have inside the lambda
        if (invocation.getMarkers().getMarkers().stream().filter(Markup.Info.class::isInstance).map(Markup.Info.class::cast).anyMatch(marker -> MSG_TOP_INVOCATION.equals(marker.getMessage()))) {
            invocation = invocation
                    .withMarkers(invocation.getMarkers().removeByType(Markup.Info.class))
                    .withPrefix(Space.EMPTY);
        }
        return invocation;
    }

    private boolean isApplicableMethod(J.MethodInvocation m) {
        JavaType.Method type = m.getMethodType();
        if (type != null) {
            JavaType.FullyQualified declaringType = type.getDeclaringType();
            if (declaringType != null && securityFqn.equals(declaringType.getFullyQualifiedName())
                    && type.getParameterTypes().isEmpty() && convertableMethods.contains(m.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isApplicableTopLevelMethodInvocation(J.MethodInvocation m) {
        if (isApplicableMethod(m)) {
            return true;
        } else if (m.getSelect() instanceof J.MethodInvocation) {
            return isApplicableTopLevelMethodInvocation((J.MethodInvocation) m.getSelect());
        }
        return false;
    }

    private boolean isApplicableCallCursor(Cursor c) {
        if (c != null && c.getValue() instanceof J.MethodInvocation) {
            J.MethodInvocation inv = c.getValue();
            if (!TypeUtils.isOfClassType(inv.getType(), securityFqn)) {
                return true;
            }
        }
        return false;
    }

    private List<J.MethodInvocation> computeAndMarkChain(J.MethodInvocation m) {
        List<J.MethodInvocation> chain = new ArrayList<>();
        Cursor cursor = getCursor().getParent(2);
        if (isApplicableCallCursor(cursor)) {
            for (; cursor != null && isApplicableCallCursor(cursor); cursor = cursor.getParent(2)) {
                cursor.putMessage(MSG_FLATTEN_CHAIN, true);
                chain.add(cursor.getValue());
            }
        }
        if (cursor == null || !(cursor.getValue() instanceof J.MethodInvocation) && !chain.isEmpty()) {
            // top invocation is at the end of the chain - mark it. We'd need to strip off prefix from this invocation later
            J.MethodInvocation topInvocation = chain.remove(chain.size() - 1);
            // removed above, now add it back with the marker
            chain.add(topInvocation.withMarkers(topInvocation.getMarkers().addIfAbsent(new Markup.Info(Tree.randomId(), MSG_TOP_INVOCATION, null))));
        }
        // mark all and() methods for flattening as well but do not include in the chain
        for (; cursor != null && cursor.getValue() instanceof J.MethodInvocation && isAndMethod(cursor.getValue()); cursor = cursor.getParent(2)) {
            cursor.putMessage(MSG_FLATTEN_CHAIN, true);
        }
        return chain;
    }

    private boolean isAndMethod(J.MethodInvocation andMethod) {
        return "and".equals(andMethod.getSimpleName()) && (andMethod.getArguments().isEmpty() || andMethod.getArguments().get(0) instanceof J.Empty);
    }

    private J.MethodInvocation createDefaultsCall(JavaType type) {
        JavaType.Method methodType = TypeUtils.asFullyQualified(type).getMethods().stream().filter(m -> "withDefaults".equals(m.getName()) && m.getParameterTypes().isEmpty() && m.getFlags().contains(Flag.Static)).findFirst().orElse(null);
        if (methodType == null) {
            throw new IllegalStateException();
        }
        maybeAddImport(methodType.getDeclaringType().getFullyQualifiedName(), methodType.getName());
        return new J.MethodInvocation(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, null,
                new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, "withDefaults", null, null),
                JContainer.empty(), methodType)
                .withSelect(null);
    }

}

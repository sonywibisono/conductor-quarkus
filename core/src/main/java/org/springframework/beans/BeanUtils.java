/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.beans;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Lightweight bridge implementation of Spring's BeanUtils for Quarkus runtime. */
public abstract class BeanUtils {

    public static void copyProperties(Object source, Object target, String... ignoreProperties) {
        if (source == null || target == null) {
            return;
        }
        Set<String> ignoreSet =
                (ignoreProperties != null && ignoreProperties.length > 0)
                        ? new HashSet<>(Arrays.asList(ignoreProperties))
                        : Collections.emptySet();

        Class<?> sourceClass = source.getClass();
        Class<?> targetClass = target.getClass();

        Map<String, Method> targetSetters = new HashMap<>();
        for (Method m : targetClass.getMethods()) {
            if (m.getName().startsWith("set")
                    && m.getName().length() > 3
                    && m.getParameterCount() == 1
                    && Modifier.isPublic(m.getModifiers())) {
                String propName =
                        Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                targetSetters.put(propName, m);
            }
        }

        for (Method m : sourceClass.getMethods()) {
            if (m.getParameterCount() == 0 && Modifier.isPublic(m.getModifiers())) {
                String propName = null;
                if (m.getName().startsWith("get") && m.getName().length() > 3) {
                    propName =
                            Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
                } else if (m.getName().startsWith("is") && m.getName().length() > 2) {
                    propName =
                            Character.toLowerCase(m.getName().charAt(2)) + m.getName().substring(3);
                }
                if (propName != null
                        && !ignoreSet.contains(propName)
                        && !"class".equals(propName)) {
                    Method setter = targetSetters.get(propName);
                    if (setter != null) {
                        try {
                            Class<?> paramType = setter.getParameterTypes()[0];
                            Class<?> returnType = m.getReturnType();
                            if (isAssignable(paramType, returnType)) {
                                Object value = m.invoke(source);
                                setter.invoke(target, value);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    public static void copyProperties(Object source, Object target) {
        copyProperties(source, target, (String[]) null);
    }

    private static boolean isAssignable(Class<?> targetType, Class<?> sourceType) {
        if (targetType.isAssignableFrom(sourceType)) {
            return true;
        }
        if (targetType.isPrimitive()) {
            if (targetType == boolean.class && sourceType == Boolean.class) return true;
            if (targetType == byte.class && sourceType == Byte.class) return true;
            if (targetType == char.class && sourceType == Character.class) return true;
            if (targetType == short.class && sourceType == Short.class) return true;
            if (targetType == int.class && sourceType == Integer.class) return true;
            if (targetType == long.class && sourceType == Long.class) return true;
            if (targetType == float.class && sourceType == Float.class) return true;
            if (targetType == double.class && sourceType == Double.class) return true;
        } else if (sourceType.isPrimitive()) {
            if (sourceType == boolean.class && targetType == Boolean.class) return true;
            if (sourceType == byte.class && targetType == Byte.class) return true;
            if (sourceType == char.class && targetType == Character.class) return true;
            if (sourceType == short.class && targetType == Short.class) return true;
            if (sourceType == int.class && targetType == Integer.class) return true;
            if (sourceType == long.class && targetType == Long.class) return true;
            if (sourceType == float.class && targetType == Float.class) return true;
            if (sourceType == double.class && targetType == Double.class) return true;
        }
        return false;
    }

    public static <T> T instantiateClass(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to instantiate [" + clazz.getName() + "]", ex);
        }
    }
}

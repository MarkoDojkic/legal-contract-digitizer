package dev.markodojkic.legalcontractdigitizer.test;

import java.lang.reflect.Method;

public class TestUtils {
    public static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object invokePrivateMethod(Object target, String methodName, Class<?>[] paramTypes, Object[] args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
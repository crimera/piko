/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class Entity {
    protected final Object obj;

    public Entity(Object obj) {
        this.obj = obj;
    }

    public Entity() {
        this.obj = null;
    }

    public Object getObject(){
        return this.obj;
    }

    public Class<?> getObjClass() throws ClassNotFoundException {
        return this.obj.getClass();
    }

    public Entity construct(String className, Class<?>[] paramTypes, Object... params) throws Exception {
        Class<?> clazz = Class.forName(className);
        Constructor<?> constructor = clazz.getDeclaredConstructor(paramTypes);
        constructor.setAccessible(true);
        Object instance = constructor.newInstance(params);
        return new Entity(instance);
    }

    public Object getField(Class cls, Object clsObj, String fieldName) throws Exception {
        Field field = cls.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Object) field.get(clsObj);
    }

    public Object getField(Object clsObj, String fieldName) throws Exception {
        return getField(clsObj.getClass(), clsObj, fieldName);
    }

    public Object getField(String fieldName) throws Exception {
        return getField(this.obj, fieldName);
    }

    public Entity getFieldAsEntity(String fieldName) throws Exception {
        Object object = getField(fieldName);
        return new Entity(object);
    }

    private Method findCompatibleMethod(Class<?> clazz, String methodName, Object... params) throws NoSuchMethodException {
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.getName().equals(methodName) || types.length != params.length) {
                continue;
            }

            boolean compatible = true;
            for (int i = 0; i < types.length; i++) {
                Object param = params[i];
                if (param == null) {
                    continue;
                }
                if (!types[i].isAssignableFrom(param.getClass())) {
                    compatible = false;
                    break;
                }
            }

            if (compatible) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "." + methodName);
    }

    public Object getMethod(Object clsObj, String methodName, Class<?>[] paramTypes, Object... params) throws Exception {
        Class<?> clazz;
        if (clsObj instanceof Class<?>) {
            clazz = (Class<?>) clsObj;
        } else {
            clazz = clsObj.getClass();
        }

        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(clsObj instanceof Class<?> ? null : clsObj, params);
        } catch (NoSuchMethodException e) {
            Method method = findCompatibleMethod(clazz, methodName, params);
            return method.invoke(clsObj instanceof Class<?> ? null : clsObj, params);
        }
    }

    public Object getMethod(Object clsObj, String methodName, Object... params) throws Exception {
        Class<?> clazz;
        if (clsObj instanceof Class<?>) {
            clazz = (Class<?>) clsObj;
        } else {
            clazz = clsObj.getClass();
        }

        if (params == null || params.length == 0) {
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(clsObj instanceof Class<?> ? null : clsObj);
        }

        Class<?>[] paramTypes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            paramTypes[i] = params[i] == null ? Object.class : params[i].getClass();
        }
        return this.getMethod(clsObj, methodName, paramTypes, params);
    }

    public Object getMethod(String methodName, Class<?>[] paramTypes, Object... params) throws Exception {
        return this.getMethod(this.obj, methodName, paramTypes, params);
    }

    public Object getMethod(String methodName, Object... params) throws Exception {
        return this.getMethod(this.obj, methodName, params);
    }

}

/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

public class Entity {
    protected final Object obj;

    // Entity getters run on every RecyclerView bind and list scroll, so the Field/Method a name
    // resolves to is cached instead of re-resolved (and re-setAccessible'd) on every call.
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Class<?>[] NO_PARAM_TYPES = new Class<?>[0];

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
        return resolveField(cls, fieldName).get(clsObj);
    }

    private static Field resolveField(Class<?> cls, String fieldName) throws NoSuchFieldException {
        ConcurrentHashMap<String, Field> classCache = FIELD_CACHE.computeIfAbsent(cls, c -> new ConcurrentHashMap<>());
        Field cached = classCache.get(fieldName);
        if (cached != null) return cached;

        Field field = cls.getDeclaredField(fieldName);
        field.setAccessible(true);
        classCache.putIfAbsent(fieldName, field);
        return classCache.get(fieldName);
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

    public Object getMethod(Object clsObj, String methodName, Class<?>[] paramTypes, Object... params) throws Exception {
        Class<?> clazz;
        Object receiver;
        if (clsObj instanceof Class<?>) {
            // clsObj is already the class to call a static method on - there's no instance to invoke on.
            clazz = (Class<?>) clsObj;
            receiver = null;
        } else {
            clazz = clsObj.getClass();
            receiver = clsObj;
        }

        Method method = resolveMethod(clazz, methodName, paramTypes);
        return method.invoke(receiver, params);
    }

    private static Method resolveMethod(Class<?> cls, String methodName, Class<?>[] paramTypes) throws NoSuchMethodException {
        String key = methodCacheKey(methodName, paramTypes);
        ConcurrentHashMap<String, Method> classCache = METHOD_CACHE.computeIfAbsent(cls, c -> new ConcurrentHashMap<>());
        Method cached = classCache.get(key);
        if (cached != null) return cached;

        Method method = cls.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        classCache.putIfAbsent(key, method);
        return classCache.get(key);
    }

    private static String methodCacheKey(String methodName, Class<?>[] paramTypes) {
        StringBuilder key = new StringBuilder(methodName);
        for (Class<?> type : paramTypes) {
            key.append('|').append(type.getName());
        }
        return key.toString();
    }

    public Object getMethod(Object clsObj, String methodName, Object... params) throws Exception {
        Class<?> clazz;
        if (clsObj instanceof Class<?>) {
            clazz = (Class<?>) clsObj;
        } else {
            clazz = clsObj.getClass();
        }

        if (params == null || params.length == 0) {
            return resolveMethod(clazz, methodName, NO_PARAM_TYPES).invoke(clsObj);
        } else {
            Class<?>[] paramTypes = new Class<?>[params.length];
            for (int i = 0; i < params.length; i++) {
                paramTypes[i] = params[i].getClass();
            }
            return this.getMethod(clsObj, methodName, paramTypes, params);
        }

    }

    public Object getMethod(String methodName, Class<?>[] paramTypes, Object... params) throws Exception {
        return this.getMethod(this.obj, methodName, paramTypes, params);
    }

    public Object getMethod(String methodName, Object... params) throws Exception {
        return this.getMethod(this.obj, methodName, params);
    }

}

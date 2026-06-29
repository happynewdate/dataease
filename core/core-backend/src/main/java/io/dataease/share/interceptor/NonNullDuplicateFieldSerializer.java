package io.dataease.share.interceptor;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @Author:
 * @createTime: 2026年03月10日 18:10:31
 * @Description:
 */
// 自定义序列化器：处理多重复字段，只取有值的一个
public class NonNullDuplicateFieldSerializer<T> implements JsonSerializer<T> {
    // 定义需要处理的重复字段名（支持多个，如 chartId、source 等）
    private final Set<String> duplicateFieldNames;

    public NonNullDuplicateFieldSerializer(String... fieldNames) {
        this.duplicateFieldNames = new HashSet<>(Arrays.asList(fieldNames));
    }

    @Override
    public JsonElement serialize(T obj, Type type, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        Class<?> clazz = obj.getClass();
        // 存储已处理的重复字段（避免重复添加）
        Set<String> processedFields = new HashSet<>();

        // 1. 遍历当前类及其所有父类的字段
        while (clazz != null && !clazz.equals(Object.class)) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName();
                // 跳过已处理的字段
                if (processedFields.contains(fieldName)) {
                    continue;
                }

                // 2. 处理重复字段（只取非空值）
                if (duplicateFieldNames.contains(fieldName)) {
                    Object fieldValue = getFieldValue(obj, field);
                    // 找到第一个非空值，添加到JSON并标记为已处理
                    if (fieldValue != null) {
                        jsonObject.add(fieldName, context.serialize(fieldValue));
                        processedFields.add(fieldName);
                    }
                }
                // 3. 处理非重复字段（正常序列化）
                else {
                    // 跳过静态/transient字段
                    if ((field.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT)) != 0) {
                        continue;
                    }
                    Object fieldValue = getFieldValue(obj, field);
                    jsonObject.add(fieldName, context.serialize(fieldValue));
                    processedFields.add(fieldName);
                }
            }
            // 向上遍历父类
            clazz = clazz.getSuperclass();
        }
        return jsonObject;
    }

    // 反射获取字段值（忽略访问权限）
    private Object getFieldValue(Object obj, Field field) {
        try {
            field.setAccessible(true);
            return field.get(obj);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
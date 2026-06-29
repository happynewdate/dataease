package io.dataease.chart.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlPlaceholderUtil {

    // 匹配：'#(xxx.yyy)'
    //private static final Pattern PATTERN = Pattern.compile("'#\\((\\w+\\.\\w+)\\)'");
    private static final Pattern PATTERN = Pattern.compile("'#\\((\\w+(\\.\\w+)?)\\)'");

    public static String replaceSqlPlaceholders(String sql, JSONObject paramJson) {
        if (StringUtils.isBlank(sql)) {
            return sql;
        }

        StringBuffer sb = new StringBuffer();
        Matcher matcher = PATTERN.matcher(sql);

        while (matcher.find()) {
            String path = matcher.group(1);
            String value = getValueByPath(paramJson, path);

            String replacement;
            if (StringUtils.isBlank(value)) {
                replacement = "null";
            } else {
                replacement = parseValue(value); // 这里自动拆数组
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // 关键：自动解析 ["1","2","3"] → 变成 '1','2','3'
    private static String parseValue(String value) {
        try {
            // 尝试解析 JSON 数组
            JSONArray array = new JSONArray(value);
            StringBuilder sb = new StringBuilder();
            for (Object obj : array) {
                if (sb.length() > 0) sb.append(",");
                sb.append("'").append(obj.toString().replace("'", "''")).append("'");
            }
            return sb.toString();
        } catch (Exception e) {
            // 不是数组，返回单个值
            return "'" + value.replace("'", "''") + "'";
        }
    }

    private static String getValueByPath(JSONObject json, String path) {
        if (json == null || StringUtils.isEmpty(path)) return null;

        String[] keys = path.split("\\.");
        Object current = json;
        for (String key : keys) {
            if (!(current instanceof JSONObject)) return null;
            current = ((JSONObject) current).get(key);
            if (current == null) return null;
        }
        return current == null ? null : current.toString();
    }

    public static String base64Decode(String base64Str) {
        if (base64Str == null || base64Str.isEmpty()) {
            return base64Str;
        }
        try {
            return Base64.decodeStr(base64Str);
        } catch (Exception e) {
            return base64Str;
        }
    }
}
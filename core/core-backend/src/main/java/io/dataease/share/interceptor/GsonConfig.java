package io.dataease.share.interceptor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dataease.extensions.view.dto.ChartViewFieldDTO;

/**
 * @Author:
 * @createTime: 2026年03月10日 18:11:12
 * @Description:
 */
public class GsonConfig {
    // 生成处理重复字段的Gson实例（支持多个重复字段，如 chartId、source 等）
    public static Gson createNonNullDuplicateFieldGson() {
        return new GsonBuilder()
                // 注册序列化器：指定处理的类 + 自定义序列化器（传入需要处理的重复字段名）
                .registerTypeAdapter(ChartViewFieldDTO.class,
                        new NonNullDuplicateFieldSerializer<>("chartId", "source")) // 可添加多个重复字段
                .serializeNulls() // 可选：保留其他非重复字段的空值
                .create();
    }
}

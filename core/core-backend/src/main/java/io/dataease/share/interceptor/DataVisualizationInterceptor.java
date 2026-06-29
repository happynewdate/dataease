//package io.dataease.share.interceptor;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.junit.jupiter.api.Order;
//import org.springframework.core.Ordered;
//import org.springframework.stereotype.Component;
//import org.springframework.util.StreamUtils;
//import org.springframework.web.filter.OncePerRequestFilter;
//import org.springframework.web.util.ContentCachingRequestWrapper;
//
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * @Author:
// * @createTime: 2026年03月10日 10:58:50
// * @Description:
// */
//@Component
//@Order(Ordered.HIGHEST_PRECEDENCE)
//public class DataVisualizationInterceptor extends OncePerRequestFilter {
//
//    // 静态 Map 用于缓存结果 (注意：生产环境建议用 ConcurrentHashMap 或 Redis)
//    private static final Map<String, String> globalMap = new HashMap<>();
//
//    // JSON 解析器
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
//            throws ServletException, IOException {
//
//        String requestURI = request.getRequestURI();
//        String contextPath = request.getContextPath();
//        String targetPath = contextPath.isEmpty() ? requestURI : requestURI.substring(contextPath.length());
//
//        // 只拦截特定路径
//        if ("/de2api/dataVisualization/findById".equals(targetPath)) {
//
//            // 1. 包装请求 (为了后续能重复读流)
//            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
//
//            // 2. 【关键步骤】先读取 Body 内容以获取 Key (例如 id)
//            // 注意：这里读取是为了查 Map，读完后面还会再用，所以必须用 wrappedRequest
//            byte[] content = StreamUtils.copyToByteArray(wrappedRequest.getInputStream());
//            String requestBody = new String(content, StandardCharsets.UTF_8);
//
//            // 3. 尝试从 JSON 中提取 Key (假设 JSON 格式为 {"id": "123"} 或其他)
//            // 如果解析失败或没有 id，视为未命中，继续走正常流程
//            String cacheKey = null;
//            try {
//                if (!requestBody.trim().isEmpty()) {
//                    JsonNode jsonNode = objectMapper.readTree(requestBody);
//                    // 假设你的 Key 字段名叫 "id"，请根据实际情况修改
//                    if (jsonNode.has("id")) {
//                        cacheKey = jsonNode.get("id").asText();
//                    }
//                }
//            } catch (Exception e) {
//                // JSON 解析失败，忽略，继续走正常流程（让 Controller 去报 400）
//                System.out.println(">>> JSON 解析失败，跳过缓存检查: " + e.getMessage());
//            }
//
//            // 4. 【核心逻辑】检查 Map
//            if (cacheKey != null && globalMap.containsKey(cacheKey)) {
//                // === 命中缓存 ===
//                String cachedValue = globalMap.get(cacheKey);
//                System.out.println(">>> [缓存命中] ID: " + cacheKey + ", 直接返回结果");
//
//                // 设置响应头
//                response.setContentType("application/json;charset=UTF-8");
//                response.setStatus(HttpServletResponse.SC_OK);
//
//                // 直接写入缓存的结果并返回
//                response.getWriter().write(cachedValue);
//                response.getWriter().flush();
//
//                // 【重要】直接 return，不再调用 filterChain，请求结束
//                return;
//            } else {
//                // === 未命中缓存 ===
//                System.out.println(">>> [缓存未命中] ID: " + (cacheKey == null ? "未知" : cacheKey) + ", 放行请求");
//
//                try {
//                    // 5. 放行请求 (让 Controller 处理)
//                    // 注意：wrappedRequest 里已经缓存了 content，Controller 读取时不会报错
//                    filterChain.doFilter(wrappedRequest, response);
//
//                    // 6. 【可选】如果需要将本次请求的结果存入 Map
//                    // 注意：此时 response 已经被 Controller 写入了。
//                    // 如果要缓存结果，通常需要在 Controller 返回后拦截 Response，或者在这里通过其他方式获取。
//                    // 简单示例：假设你知道结果是什么，或者你想缓存原始请求体作为某种标记
//                    // 如果你需要缓存 Controller 的返回值，需要使用 ContentCachingResponseWrapper 包装 response
//                    // 这里仅演示：如果业务逻辑需要在 Filter 层做额外处理
//
//                } catch (Exception e) {
//                    System.err.println(">>> 请求处理异常: " + e.getMessage());
//                    throw e;
//                }
//            }
//
//            return;
//        }
//
//        // 非目标路径，直接放行
//        filterChain.doFilter(request, response);
//    }
//
//    // 提供一个静态方法供外部（如 Controller）存入数据到 globalMap
//    public static void putToCache(String key, String value) {
//        globalMap.put(key, value);
//        System.out.println(">>> [缓存更新] 已存入 Key: " + key);
//    }
//}

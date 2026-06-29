package io.dataease.filter;

import io.dataease.result.ResultMessage;
import io.dataease.utils.JsonUtil;
import jakarta.servlet.*;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class HtmlResourceFilter implements Filter, Ordered {

    @Value("${dataease.http.cache:false}")
    private Boolean httpCache;

    /** Vite 构建产物的路径前缀，这些文件带有版本号 hash，可以永久缓存 */
    private static final String[] IMMUTABLE_PATH_PREFIXES = {
        "/assets/",
        "/js/"
    };

    @Override
    public int getOrder() {
        return 99;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        String requestUri = httpRequest.getRequestURI();

        // 对 Vite 构建产物（带版本号的 js/css/图片等）设置强缓存
        if (isImmutableResource(requestUri)) {
            httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        } else if (httpCache == null || !httpCache) {
            // 对其他请求禁用缓存
            httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            httpResponse.setHeader("Cache", "no-cache");
            httpResponse.setHeader(HttpHeaders.PRAGMA, "no-cache");
            httpResponse.setHeader(HttpHeaders.EXPIRES, "0");
        }
        // 继续执行过滤器链
        try {
            filterChain.doFilter(servletRequest, httpResponse);
        } catch (Exception e) {
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write(JsonUtil.toJSONString(new ResultMessage(HttpServletResponse.SC_BAD_REQUEST, e.getMessage())).toString());
        }
    }

    /**
     * 判断是否为 Vite 构建产物的不可变资源（带版本 hash 的文件名）
     */
    private boolean isImmutableResource(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        for (String prefix : IMMUTABLE_PATH_PREFIXES) {
            if (requestUri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}

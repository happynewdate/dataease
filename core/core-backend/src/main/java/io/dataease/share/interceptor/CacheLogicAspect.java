package io.dataease.share.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import io.dataease.api.visualization.request.DataVisualizationBaseRequest;
import io.dataease.api.xpack.share.vo.XpackShareProxyVO;
import io.dataease.constant.AuthConstant;
import io.dataease.extensions.view.dto.ChartViewDTO;
import io.dataease.utils.ServletUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class CacheLogicAspect {

    public static Logger logger = LoggerFactory.getLogger(CacheLogicAspect.class);

    private final Gson gson = new Gson();

    // 保存过期时间，方便外部获取
    private final long linkExpire;

    // 定义缓存对象，替代 ConcurrentHashMap
    // 这里使用 Caffeine，支持自动过期
    private final Cache<String, Object> linkMap;
    private final Cache<String, Object> infoMap;
    private final Cache<String, Object> dataMap;

    /**
     * 构造函数注入，允许通过配置文件动态设置过期时间
     *
     * @param linkExpire link缓存过期时间(秒)
     * @param infoExpire 可视化信息缓存过期时间(秒)
     * @param dataExpire 图表数据缓存过期时间(秒)
     */
    public CacheLogicAspect(
            @Value("${cache.link.expire:2}") long linkExpire,
            @Value("${cache.info.expire:30}") long infoExpire,
            @Value("${cache.data.expire:30}") long dataExpire) {

        this.linkExpire = linkExpire;

        this.linkMap = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(linkExpire, TimeUnit.MINUTES)
                .build();

        this.infoMap = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(infoExpire, TimeUnit.MINUTES)
                .build();

        this.dataMap = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(dataExpire, TimeUnit.MINUTES)
                .build();
    }

    //查询分享代理信息
    @Around("execution(* io.dataease.share.server.XpackShareServer.proxyInfo(..))")
    public Object handleLink(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = null;
        Object[] args = joinPoint.getArgs();

        XpackShareProxyVO result;
        try {
            result = (XpackShareProxyVO) joinPoint.proceed();
            // 生成 key 用于判断是否需要刷新
            String argsJson = gson.toJson(args);

            logger.info("==========获取大屏的请求入参为"+argsJson+"=======================");

            if (StringUtils.isNotEmpty(argsJson) && argsJson.contains("\"refresh\":true")) {
                Long resourceId = result.getResourceId();
                String linkKey = "link_" + resourceId;

                // 放入缓存，标记需要刷新。
                // 注意：这里放入后，会在配置的 linkExpire 时间后自动过期，
                // 或者你可以手动 invalidate 来立即失效（如果需要立即失效逻辑需调整）
                // 当前逻辑：只要 linkMap 里有这个 key，就认为需要刷新。
                // 所以 refresh=true 时，我们 put 一个标记。
                linkMap.put(linkKey, true);
                logger.info("==========获取大屏的请求入参为"+argsJson+"==============的refresh为true=========");
                logger.info("===========入参:======"+argsJson+"=================="+linkExpire+"分钟内调用接口返回");

            }

        } catch (Throwable e) {
            logger.error(">>> [执行中] 方法执行异常: " + e.getMessage());
            throw e;
        }
        return result;
    }


    //查询可视化资源
    @Around("execution(* io.dataease.visualization.server.DataVisualizationServer.findById(..))")
    public Object handleFindById(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = null;
        Object result;
        try {
            Object[] args = joinPoint.getArgs();
            if (ObjectUtils.isNotEmpty(args) && args.length > 0) {
                DataVisualizationBaseRequest request = (DataVisualizationBaseRequest) args[0];
                Long id = request.getId();
                key = "link_" + id + "_info";
            }

            if (StringUtils.isEmpty(key)) {
                result = joinPoint.proceed();
            } else {
                String linkCheckKey = key.replace("_info", "");
                // 检查是否存在刷新标记
                // getIfPresent 不会触发加载，仅检查是否存在
                if (linkMap.getIfPresent(linkCheckKey) != null) {
                    // 链接的 refresh 为 true (或者标记未过期)，表示重新加载
                    // 加载完成后，可以选择是否移除标记，取决于业务需求
                    // 如果希望只刷新一次，可以在这里 linkMap.invalidate(linkCheckKey);
                    // 如果希望在过期时间内一直刷新，则保留
                    result = joinPoint.proceed();
                    logger.info("=============查询可视化资源================"+linkCheckKey+"=======调用接口返回结果========");
                } else {
                    // 没有刷新标记，尝试从缓存获取
                    Object cached = infoMap.getIfPresent(key);
                    if (cached != null) {
                        result = cached;
                        logger.info("=============查询可视化资源================"+linkCheckKey+"=======从内存中获取结果========");
                    } else {
                        result = joinPoint.proceed();
                    }
                }
            }

        } catch (Throwable e) {
            logger.error(">>>查询可视化资源================= [执行中] 方法执行异常: " + e.getMessage());
            throw e;
        }

        // 只有成功执行且 key 有效时才放入缓存
        if (key != null && result != null) {
            infoMap.put(key, result);
        }
        return result;
    }


    //拦截图表查询方法进行缓存结果
//    @Around("execution(* io.dataease.chart.server.ChartDataServer.getData(..))")
    public Object handleData(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = null;
        String yeeAdminInfo = ServletUtils.getHead(AuthConstant.YEE_ADMIN_INFO);
        Object[] args = joinPoint.getArgs();
        Object result;
        try {
            if (ObjectUtils.isNotEmpty(args) && args.length > 0) {
                ChartViewDTO chartViewDTO = (ChartViewDTO) args[0];
                Long id = chartViewDTO.getId();
                Long sceneId = chartViewDTO.getSceneId();

                String filter = null;
                if (ObjectUtils.isNotEmpty(chartViewDTO.getChartExtRequest()) &&
                        ObjectUtils.isNotEmpty(chartViewDTO.getChartExtRequest().getFilter())) {
                    filter = gson.toJson(chartViewDTO.getChartExtRequest().getFilter());
                }

                key = "link_" + sceneId + "_getData" + id + "_" + filter;
            }
            if(StringUtils.isNotEmpty(yeeAdminInfo)){
                key += "_" + yeeAdminInfo;
            }

            if (StringUtils.isEmpty(key)) {
                result = joinPoint.proceed();
            } else {
                // 提取 linkKey 部分: "link_" + sceneId
                int getDataIndex = key.indexOf("_getData");
                String linkCheckKey = (getDataIndex > 0) ? key.substring(0, getDataIndex) : key;

                if (linkMap.getIfPresent(linkCheckKey) != null) {
                    // 存在刷新标记，跳过缓存，直接查询
                    result = joinPoint.proceed();
                } else {
                    Object cached = dataMap.getIfPresent(key);
                    if (cached != null) {
                        result = cached;
                        logger.info("=============拦截图表查询方法================"+key+"=======从内存中获取结果为========");
                    } else {
                        result = joinPoint.proceed();
                    }
                }
            }

        } catch (Throwable e) {
            logger.error(">>> [执行中] 方法执行异常: " + e.getMessage());
            throw e;
        }
        if (key != null && result != null) {
            dataMap.put(key, result);
        }
        return result;
    }

    @Around("execution(* io.dataease.visualization.server.DataVisualizationServer.updatePublishStatus(..)) ||"+
            "execution(* io.dataease.visualization.server.DataVisualizationServer.checkCanvasChange(..))")
    public Object handleStatus(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result;
        Object[] args = joinPoint.getArgs();
        String key = null;
        try {
            if (ObjectUtils.isNotEmpty(args) && args.length > 0) {
                DataVisualizationBaseRequest request = (DataVisualizationBaseRequest) args[0];
                Long id = request.getId();
                key = "link_" + id;
            }

            result = joinPoint.proceed();
        } catch (Throwable e) {
            logger.error(">>> [执行中] 方法执行异常: " + e.getMessage());
            throw e;
        }
        if (key != null) {
            linkMap.put(key, true);
        }
        return result;
    }


}

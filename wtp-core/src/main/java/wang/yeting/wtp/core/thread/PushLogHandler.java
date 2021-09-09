package wang.yeting.wtp.core.thread;

import lombok.extern.slf4j.Slf4j;
import wang.yeting.wtp.core.annotation.Wtp;
import wang.yeting.wtp.core.biz.client.AdminBiz;
import wang.yeting.wtp.core.biz.model.WtpBo;
import wang.yeting.wtp.core.biz.model.WtpConfigBean;
import wang.yeting.wtp.core.biz.model.WtpLogAdminBiz;
import wang.yeting.wtp.core.biz.model.WtpLogBo;
import wang.yeting.wtp.core.concurrent.WtpThreadPoolExecutor;
import wang.yeting.wtp.core.context.WtpAnnotationContext;
import wang.yeting.wtp.core.factory.WtpThreadPoolFactory;
import wang.yeting.wtp.core.handler.WtpHandler;
import wang.yeting.wtp.core.util.HttpResponse;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author : weipeng
 * @date : 2020-07-24 18:31
 */
@Slf4j
public class PushLogHandler {

    private int current = 0;

    private int size = 0;

    private List<WtpLogAdminBiz> wtpLogAdminBizList;

    private Set<Map.Entry<String, WtpThreadPoolExecutor>> entrySet;

    private static ScheduledFuture<?> scheduledFuture;
    private static HashMap<Wtp, Boolean> wtprRegistStatMap = new HashMap<>();

    private WtpConfigBean wtpConfigBean;

    private String appId;

    private String clusterId;
    private List<AdminBiz> adminBizList;

    private WtpAnnotationContext wtpAnnotationContext;

    public PushLogHandler(List<AdminBiz> adminBizList, WtpConfigBean wtpConfigBean, WtpAnnotationContext wtpAnnotationContext) {
        this.adminBizList = adminBizList;
        this.wtpConfigBean = wtpConfigBean;
        this.wtpAnnotationContext = wtpAnnotationContext;
    }

    public void pushLog() {
        initAdminList(adminBizList);

        this.appId = wtpConfigBean.getAppId();
        this.clusterId = wtpConfigBean.getClusterId();

        WtpThreadPoolFactory wtpThreadPoolFactory = WtpThreadPoolFactory.getInstance();
        ConcurrentMap<String, WtpThreadPoolExecutor> threadPoolConcurrentMap = wtpThreadPoolFactory.getThreadPoolConcurrentMap();
        this.entrySet = threadPoolConcurrentMap.entrySet();

        scheduledFuture = ThreadPool.getScheduledThreadPoolExecutor().scheduleWithFixedDelay(() -> {
            try {
                doInitWtp();
                doPushLog();
            } catch (Exception e) {
                log.error("wtp ------> doPushLog Exception .", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    private void doInitWtp() {
        Wtp wtp = null;
        WtpThreadPoolFactory wtpThreadPoolFactory = WtpThreadPoolFactory.getInstance();
        Set<Map.Entry<String, List<WtpHandler>>> entrySet = wtpAnnotationContext.getWtpHandler().entrySet();
        for (Map.Entry<String, List<WtpHandler>> wtpHandlerEntry : entrySet) {
            String name = wtpHandlerEntry.getKey();
            WtpThreadPoolExecutor threadPool = wtpThreadPoolFactory.getThreadPool(name);
            List<WtpHandler> writeArrayList = wtpHandlerEntry.getValue();
            if (writeArrayList.isEmpty()) {
                continue;
            }
            WtpHandler wtpHandler = writeArrayList.get(0);
            wtp = wtpHandler.getWtp();

            // 每次都尝试注册
            if (Boolean.FALSE.equals(wtprRegistStatMap.getOrDefault(wtp, Boolean.FALSE)))
                registerNoConfigurationWtp(wtp, wtprRegistStatMap);

            if (threadPool == null) {
                threadPool = wtpThreadPoolFactory.loadDefault(wtp);
                log.warn("wtp ------> {} No configuration Wtp.", wtp.value());
            }
            // 每次都尝试去注册wtp, 针对客户端启动时服务器端没有启动的场景，进行定时循环重复注册

            for (WtpHandler handler : writeArrayList) {
                handler.assignment(threadPool);
            }
        }
    }

    private void registerNoConfigurationWtp(Wtp wtp, HashMap<Wtp, Boolean> wtprRegistStatMap) {
        log.info("start to registry wtp: {}", wtp);
        for (AdminBiz adminBiz : adminBizList) {
            WtpBo wtpBo = new WtpBo()
                    .setAppId(wtpConfigBean.getAppId())
                    .setClusterId(wtpConfigBean.getClusterId())
                    .setCorePoolSize(wtp.defaultCorePoolSize())
                    .setMaximumPoolSize(wtp.defaultMaximumPoolSize())
                    .setKeepAliveSeconds(wtp.defaultKeepAliveSeconds())
                    .setName(wtp.value())
                    .setQueueCapacity(wtp.defaultQueueCapacity())
                    .setQueueName(wtp.defaultQueueName().getQueueName())
                    .setRejectedExecutionHandlerName(wtp.rejectedExecutionHandlerName().getRejectedExecutionHandlerName());
            try {
                HttpResponse<Boolean> response = adminBiz.registerNoConfigurationWtp(wtpBo);
                if (response.getStatusCode() == HttpResponse.SUCCESS_CODE) {
                    wtprRegistStatMap.put(wtp, Boolean.TRUE);
                    return;
                }
            } catch (Exception e) {
                log.error("wtp ------> register NoConfiguration Wtp Exception. ", e);
            }
        }
        log.error("wtp ------> {} failed to register. ", wtp.value());
    }

    private void doPushLog() {
        for (Map.Entry<String, WtpThreadPoolExecutor> nameThreadPoolExecutorEntry : entrySet) {
            WtpThreadPoolExecutor threadPoolExecutor = nameThreadPoolExecutorEntry.getValue();
            String name = nameThreadPoolExecutorEntry.getKey();
            BlockingQueue<Runnable> queue = threadPoolExecutor.getQueue();
            WtpLogBo wtpLog = new WtpLogBo()
                    .setAppId(appId).setClusterId(clusterId).setName(name)

                    .setCorePoolSize(threadPoolExecutor.getCorePoolSize())
                    .setMaximumPoolSize(threadPoolExecutor.getMaximumPoolSize())
                    .setKeepAliveSeconds(threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS))
                    .setActiveCount(threadPoolExecutor.getActiveCount())
                    .setCompletedTaskCount(threadPoolExecutor.getCompletedTaskCount())
                    .setLargestPoolSize(threadPoolExecutor.getLargestPoolSize())
                    .setRejectedExecutionCount(threadPoolExecutor.getRejectedExecutionCount())
                    .setTaskCount(threadPoolExecutor.getTaskCount())
                    .setPoolSize(threadPoolExecutor.getPoolSize())
                    .setTotalTime(threadPoolExecutor.getTotalTime())
                    .setMaximumTime(threadPoolExecutor.getMaximumTime())
                    .setQueueSize(queue.size())
                    .setQueueRemainingCapacity(queue.remainingCapacity())

                    .setLogTime(System.currentTimeMillis());
            WtpLogAdminBiz wtpLogAdminBiz = getAdminBiz();
            AdminBiz adminBiz = wtpLogAdminBiz.getAdminBiz();
            String ip = adminBiz.getWtpConfigBean().getIp();
            wtpLog.setIp(ip);
            try {
                HttpResponse<Boolean> httpResponse = adminBiz.pushLog(wtpLog);
                if (httpResponse.getStatusCode() != HttpResponse.SUCCESS_CODE || httpResponse.getBody() == null || !httpResponse.getBody()) {
                    wtpLogAdminBiz.setWaitCount(3);
                }
            } catch (Exception e) {
                wtpLogAdminBiz.setWaitCount(3);
            }
        }
    }

    private WtpLogAdminBiz getAdminBiz() {
        WtpLogAdminBiz wtpLogAdminBiz = null;
        do {
            if (current == size) {
                current = 0;
            }
            WtpLogAdminBiz wtpLogAdminBizTemp = wtpLogAdminBizList.get(current++);
            Integer waitCount = wtpLogAdminBizTemp.getWaitCount();
            if (waitCount == 0) {
                wtpLogAdminBiz = wtpLogAdminBizTemp;
            } else {
                wtpLogAdminBizTemp.setWaitCount(--waitCount);
            }
        } while (wtpLogAdminBiz == null);
        return wtpLogAdminBiz;
    }

    private void initAdminList(List<AdminBiz> adminBizList) {
        wtpLogAdminBizList = new ArrayList<>(adminBizList.size());
        size = adminBizList.size();
        for (AdminBiz adminBiz : adminBizList) {
            WtpLogAdminBiz wtpLogAdminBiz = new WtpLogAdminBiz().setWaitCount(0).setAdminBiz(adminBiz);
            wtpLogAdminBizList.add(wtpLogAdminBiz);
        }
    }

    public static void destroy() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }
}

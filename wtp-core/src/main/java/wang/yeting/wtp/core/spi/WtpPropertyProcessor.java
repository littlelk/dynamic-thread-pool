package wang.yeting.wtp.core.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import wang.yeting.wtp.core.annotation.Wtp;
import wang.yeting.wtp.core.biz.client.AdminBiz;
import wang.yeting.wtp.core.biz.client.AdminBizClient;
import wang.yeting.wtp.core.biz.model.ConfigEvent;
import wang.yeting.wtp.core.biz.model.WtpBo;
import wang.yeting.wtp.core.biz.model.WtpConfigBean;
import wang.yeting.wtp.core.concurrent.ResizableCapacityLinkedBlockingQueue;
import wang.yeting.wtp.core.concurrent.WtpThreadPoolExecutor;
import wang.yeting.wtp.core.context.WtpAnnotationContext;
import wang.yeting.wtp.core.factory.WtpQueueFactory;
import wang.yeting.wtp.core.factory.WtpThreadPoolFactory;
import wang.yeting.wtp.core.handler.WtpHandler;
import wang.yeting.wtp.core.thread.*;
import wang.yeting.wtp.core.util.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author : weipeng
 * @date : 2020-07-22 15:44
 */

@Slf4j
public class WtpPropertyProcessor implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

    private ApplicationContext applicationContext;

    private WtpAnnotationContext wtpAnnotationContext;

    private final WtpConfigBean wtpConfigBean;

    private static List<AdminBiz> adminBizList;

    public WtpPropertyProcessor(WtpConfigBean wtpConfigBean) {
        this.wtpConfigBean = wtpConfigBean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.wtpAnnotationContext = applicationContext.getBean(WtpAnnotationContext.class);
    }

    @Override
    public void destroy() {
        for (AdminBiz adminBiz : adminBizList) {
            adminBiz.destroy();
        }
        ThreadPool.destroy();
        WtpThreadPoolFactory.getInstance().destroy();
        log.warn("wtp ------> destroy.");
    }

    @Override
    public void afterSingletonsInstantiated() {
        WtpQueueFactory.refreshInstance();

        WtpThreadPoolFactory.refreshInstance();

        start();
    }

    private void start() {
        log.warn("wtp ------> start.");

        //生成注册对象
        /*
                初始化adminBizList = new ArrayList<>();，list中存放了多个AdminBizClient
         */
        initAdminBizList();

        //注册到admin 并首次初始化线程池
        /*
                注册到admin的wtp_registry表中
                    如果表中已存在数据则更新时间
                    如果表中不存在数据则插入
                从admin的redis中查找WtpConfigFactory中该 Appid和ClusterId对应的配置，根据此配置(可能第一次为null，还没有注册)进行
                    初始化队列，加入到队列map中
                    初始化线程池(从队列map中找对应的队列)
         */
        registryInAdmin();

        //加载线程池
        /*
         *      新建两个线程池放在ThreadPool成员变量中
         *          1. new ThreadPoolExecutor(0, maxPoolSize, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> new Thread(r, "wtp main-" + r.hashCode()))
         *          2. new ScheduledThreadPoolExecutor(4, r -> new Thread(r, "wtp Scheduled-" + r.hashCode()))
         */
        initWtpThreadPool();

        //放值WtpHandler
        /*
                拿到WtpAnnotationContext中的配置
                    如果threadPoolExecutorConcurrentMap没有此线程池，则初始化线程池放入threadPoolExecutorConcurrentMap，利用反射注入给客户端 写入wtp表中
                    如果已存在此线程池，则将此线程池利用反射注入给客户端
         */
//        initWtp();

        //长轮询拉去新配置
        /*
                主线程池起一个线程
                根据客户端配置中的ConnectRetryInterval时间间隔去拉取配置
                    先尝试注册该AppId和cluster到wtp_registry表，如果存在则更新时间
                    然后将该请求参数(appid clusterid deferredResult)放入队列中[admin端会有定时线程去拉取队列中的任务进行处理，并返回]
                        在admin端有长轮询线程处理请求，从redis的key config:change:appId:%s:clusterId:%s 中拉取新配置 返回
         */
        startPullConfig();

        //推送线程池日志
        /*
                定时线程池ThreadPool.getScheduledThreadPoolExecutor().scheduleWithFixedDelay(()->{}, 60, 60, TimeUnit.SECONDS)
                    每隔1min 推送一下日志
                        (如果推送失败3s后在次尝试推送)
                        计算该线程池的占用情况，如果满足告警情况进行告警
                        写入wtp_log表中
         */
        pushLog();

        //定时去拉取全部配置
        /*
                定时线程池ThreadPool.getScheduledThreadPoolExecutor().scheduleWithFixedDelay(()->{}, 300, 300, TimeUnit.SECONDS)
                    每隔5min拉取全部配置
                    从redis的key config:all:appId:%s:clusterId:%s 中拉取全部配置，更改线程池参数
         */
        taskPullConfig();

        log.warn("wtp ------> started.");
    }

    private void taskPullConfig() {
        TaskPullConfigHandler taskPullConfigHandler = new TaskPullConfigHandler();
        taskPullConfigHandler.taskPullConfig(adminBizList);
    }

    private void initWtpThreadPool() {
        int maxPoolSize = 3 + adminBizList.size();
        ThreadPool.loadMainThreadPool(
                new ThreadPoolExecutor(0, maxPoolSize, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> new Thread(r, "wtp main-" + r.hashCode()))
                , new ScheduledThreadPoolExecutor(4, r -> new Thread(r, "wtp Scheduled-" + r.hashCode()))
        );
    }

//    public void initWtp() {
//        InitWtpHandler initWtpHandler = new InitWtpHandler(applicationContext, wtpAnnotationContext);
//        initWtpHandler.initWtp(adminBizList, wtpConfigBean);
//    }

    private void pushLog() {
        PushLogHandler pushLogHandler = new PushLogHandler(adminBizList, wtpConfigBean, wtpAnnotationContext);
        pushLogHandler.pushLog();
    }

    private void startPullConfig() {
        PullConfigHandler pullConfigHandler = new PullConfigHandler();
        pullConfigHandler.pullConfig(adminBizList);
    }

    private void registryInAdmin() {
        ConfigEvent configEvent = null;
        for (AdminBiz adminBiz : adminBizList) {
            HttpResponse<ConfigEvent> response = adminBiz.registry();
            if (response.getStatusCode() == HttpResponse.SUCCESS_CODE) {
                configEvent = response.getBody();
            } else {
                log.error("wtp ------> register {} failed. ", adminBiz);
            }
        }
        if (configEvent == null) {
//            throw new RuntimeException("wtp ------> All registries failed , adminBizList [" + adminBizList + "].");
            log.error("wtp ------> All registries failed , adminBizList [\" + adminBizList + \"].");
        }
        initWtpQueueFactory(configEvent);
        initWtpThreadPoolFactory(configEvent);
    }

    private void initWtpQueueFactory(ConfigEvent configEvent) {
        WtpQueueFactory wtpQueueFactory = WtpQueueFactory.getInstance();
        wtpQueueFactory.initQueue(configEvent);
    }

    private void initWtpThreadPoolFactory(ConfigEvent configEvent) {
        WtpThreadPoolFactory wtpThreadPoolFactory = WtpThreadPoolFactory.getInstance();
        wtpThreadPoolFactory.injectService(configEvent);
    }



    private void initAdminBizList() {
        if (adminBizList == null) {
            adminBizList = new ArrayList<>();
        }
        String adminAddresses = wtpConfigBean.getAdminUrls();
        if (adminAddresses != null && adminAddresses.trim().length() > 0) {
            for (String address : adminAddresses.trim().split(",")) {
                if (address != null && address.trim().length() > 0) {
                    AdminBiz adminBiz = new AdminBizClient(address.trim(), wtpConfigBean);
                    adminBizList.add(adminBiz);
                }
            }
        }
    }

}

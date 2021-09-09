package wang.yeting.wtp.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import wang.yeting.wtp.admin.bean.Wtp;
import wang.yeting.wtp.admin.factory.WtpConfigFactory;
import wang.yeting.wtp.admin.factory.WtpFactory;
import wang.yeting.wtp.admin.service.WtpRegistryService;
import wang.yeting.wtp.admin.service.WtpService;
import wang.yeting.wtp.admin.thread.MainThreadPool;
import wang.yeting.wtp.admin.thread.PullConfigMonitorHandler;
import wang.yeting.wtp.admin.thread.WtpMonitorHandler;
import wang.yeting.wtp.admin.thread.WtpRegistryMonitorHandler;
import wang.yeting.wtp.admin.util.RedisUtils;

import java.util.List;
import java.util.concurrent.*;

/**
 * @author : weipeng
 * @date : 2020-07-30 19:59
 */
@Slf4j
@SpringBootConfiguration
public class WtpHelperConfig implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {

    private ApplicationContext applicationContext;

    @Value("${config.refresh.second}")
    private Long configRefreshSecond;

    @Value("${registry.monitor.second}")
    private Long registryMonitorSecond;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void destroy() throws Exception {
        MainThreadPool.destroy();
        log.error("wtp ------> destroy.");
    }

    @Override
    public void afterSingletonsInstantiated() {
        /*
         * 1. 初始化WtpFactory和 WtpConfigFactory
         * 2. 查表wtp,根据表中内容更新
         *      WtpFactory：ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, Wtp>>> wtpConcurrentMap = new ConcurrentHashMap<>();
         *      WtpConfigFactory：
         *              Map<String, List> appMap = new HashMap();
         *              Map<String, List> configMap = new HashMap();
         */
        initFactory();

        /*
         * 新建三个线程池放在MainThreadPool成员变量中
         *      1. new ThreadPoolExecutor(5, 10, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> new Thread(r, "wtp main-" + r.hashCode()))
         *      2. Executors.newCachedThreadPool()
         *      3. new ScheduledThreadPoolExecutor(5, r -> new Thread(r, "wtp Scheduled-" + r.hashCode()))
         */
        initMainThreadPool();

        /*
         *  (scheduleWithFixedDelay)
         *  使用定时线程池 每隔五分钟
         *  查找并移除wtp_registry表中最后更新事件在5分钟之前的记录
         */
        registryMonitor();

        /*
         * (scheduleAtFixedRate)
         *  使用定时线程池 每隔五分钟
         * 查找表wtp中的配置，更新到WtpFactory和 WtpConfigFactory中
         */
        wtpMonitor();

        /*
         * pullConfigMonitorHandler = new PullConfigMonitorHandler();
         * 使用主线程池   每隔1秒处理一下来自客户端的拉取配置的请求
         *      查看redis中是否有该key"config:change:appId:%s:clusterId:%s"
         *          当redis中有该key时，说明配置改变  返回redis的配置并删除请求，超时时间30s
         *          当redis中没有该key时，当达到指定次数30次时删除
         */
        pullConfigMonitor();
    }

    private void initFactory() {
        RedisUtils redisUtils = applicationContext.getBean(RedisUtils.class);
        WtpFactory.refreshInstance();
        WtpFactory wtpFactory = WtpFactory.getInstance();
        WtpConfigFactory.refreshInstance(redisUtils, configRefreshSecond);
        WtpConfigFactory wtpConfigFactory = WtpConfigFactory.getInstance();

        WtpService wtpService = applicationContext.getBean(WtpService.class);
        // 查库表wtp所有记录
        List<Wtp> wtpList = wtpService.initConfigFactory();

        // 把所有的WTP加载在内存Map中
        wtpFactory.loadWtp(wtpList);

        // 表wtp所有记录 放在appMap 和 configMap中 且存在redis中 config:all:appId:%s:clusterId:%s，超时时间5min
        wtpConfigFactory.loadConfig(wtpList);
    }

    private void pullConfigMonitor() {
        try {
            MainThreadPool.execute(() -> {
                        PullConfigMonitorHandler pullConfigMonitorHandler = new PullConfigMonitorHandler();
                        pullConfigMonitorHandler.pullConfigMonitor();
                    }
            );
        } catch (Exception e) {
            throw new RuntimeException("pullConfigMonitor", e);
        }
    }

    private void initMainThreadPool() {
        MainThreadPool.loadMainThreadPool(new ThreadPoolExecutor(5, 10, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> new Thread(r, "wtp main-" + r.hashCode()))
                , Executors.newCachedThreadPool()
                , new ScheduledThreadPoolExecutor(5, r -> new Thread(r, "wtp Scheduled-" + r.hashCode()))
        );
    }

    private void registryMonitor() {
        try {
            WtpRegistryService wtpRegistryService = (WtpRegistryService) applicationContext.getBean("wtpRegistryServiceImpl");
            WtpRegistryMonitorHandler wtpRegistryMonitorHandler = new WtpRegistryMonitorHandler(wtpRegistryService);
            wtpRegistryMonitorHandler.registryMonitor(registryMonitorSecond);
        } catch (Exception e) {
            throw new RuntimeException("registryMonitor");
        }
    }

    private void wtpMonitor() {
        try {
            WtpService wtpService = (WtpService) applicationContext.getBean("wtpServiceImpl");
            WtpMonitorHandler wtpMonitorHandler = new WtpMonitorHandler(wtpService);
            wtpMonitorHandler.wtpMonitor(configRefreshSecond);
        } catch (Exception e) {
            throw new RuntimeException("pushHealthLog");
        }
    }

}

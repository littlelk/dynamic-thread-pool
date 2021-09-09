//package wang.yeting.wtp.core.thread;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.BeansException;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ApplicationContextAware;
//import org.springframework.context.annotation.ComponentScan;
//import wang.yeting.wtp.core.annotation.Wtp;
//import wang.yeting.wtp.core.biz.client.AdminBiz;
//import wang.yeting.wtp.core.biz.model.WtpBo;
//import wang.yeting.wtp.core.biz.model.WtpConfigBean;
//import wang.yeting.wtp.core.concurrent.WtpThreadPoolExecutor;
//import wang.yeting.wtp.core.context.WtpAnnotationContext;
//import wang.yeting.wtp.core.factory.WtpThreadPoolFactory;
//import wang.yeting.wtp.core.handler.WtpHandler;
//import wang.yeting.wtp.core.util.HttpResponse;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.Set;
//import java.util.concurrent.ScheduledFuture;
//import java.util.concurrent.TimeUnit;
//
//@Slf4j
//public class InitWtpHandler {
//
//    private ApplicationContext applicationContext;
//    private WtpAnnotationContext wtpAnnotationContext;
//    private static ScheduledFuture<?> scheduledFuture;
//    private WtpConfigBean wtpConfigBean;
//    private List<AdminBiz> adminBizList;
//
//    public InitWtpHandler(ApplicationContext applicationContext, WtpAnnotationContext wtpAnnotationContext) {
//        this.applicationContext = applicationContext;
//        this.wtpAnnotationContext = wtpAnnotationContext;
//    }
//
//    public void initWtp(List<AdminBiz> adminBizList, WtpConfigBean wtpConfigBean) {
//
//        this.adminBizList = adminBizList;
//        this.wtpConfigBean = wtpConfigBean;
//
//        scheduledFuture = ThreadPool.getScheduledThreadPoolExecutor().scheduleWithFixedDelay(() -> {
//            try {
//                doInitWtp();
//            } catch (Exception e) {
//                log.error("wtp ------> doPushLog Exception .", e);
//            }
//        }, 0, 60, TimeUnit.SECONDS);
//    }
//
//    private void doInitWtp() {
//        log.info("start to init wtp...");
//        Wtp wtp = null;
//        WtpThreadPoolFactory wtpThreadPoolFactory = WtpThreadPoolFactory.getInstance();
//        Set<Map.Entry<String, List<WtpHandler>>> entrySet = wtpAnnotationContext.getWtpHandler().entrySet();
//        for (Map.Entry<String, List<WtpHandler>> wtpHandlerEntry : entrySet) {
//            String name = wtpHandlerEntry.getKey();
//            WtpThreadPoolExecutor threadPool = wtpThreadPoolFactory.getThreadPool(name);
//            List<WtpHandler> writeArrayList = wtpHandlerEntry.getValue();
//            if (writeArrayList.isEmpty()) {
//                continue;
//            }
//            WtpHandler wtpHandler = writeArrayList.get(0);
//            wtp = wtpHandler.getWtp();
//
//            // 每次都尝试注册
//            registerNoConfigurationWtp(wtp);
//
//            if (threadPool == null) {
//                threadPool = wtpThreadPoolFactory.loadDefault(wtp);
//                log.warn("wtp ------> {} No configuration Wtp.", wtp.value());
//            }
//            // 每次都尝试去注册wtp, 针对客户端启动时服务器端没有启动的场景，进行定时循环重复注册
//
//            for (WtpHandler handler : writeArrayList) {
//                handler.assignment(threadPool);
//            }
//        }
//    }
//
//    private void registerNoConfigurationWtp(Wtp wtp) {
//        for (AdminBiz adminBiz : adminBizList) {
//            WtpBo wtpBo = new WtpBo()
//                    .setAppId(wtpConfigBean.getAppId())
//                    .setClusterId(wtpConfigBean.getClusterId())
//                    .setCorePoolSize(wtp.defaultCorePoolSize())
//                    .setMaximumPoolSize(wtp.defaultMaximumPoolSize())
//                    .setKeepAliveSeconds(wtp.defaultKeepAliveSeconds())
//                    .setName(wtp.value())
//                    .setQueueCapacity(wtp.defaultQueueCapacity())
//                    .setQueueName(wtp.defaultQueueName().getQueueName())
//                    .setRejectedExecutionHandlerName(wtp.rejectedExecutionHandlerName().getRejectedExecutionHandlerName());
//            try {
//                HttpResponse<Boolean> response = adminBiz.registerNoConfigurationWtp(wtpBo);
//                if (response.getStatusCode() == HttpResponse.SUCCESS_CODE) {
//                    return;
//                }
//            } catch (Exception e) {
//                log.error("wtp ------> register NoConfiguration Wtp Exception. ", e);
//            }
//        }
//        log.error("wtp ------> {} failed to register. ", wtp.value());
//    }
//}

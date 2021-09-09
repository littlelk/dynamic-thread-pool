package wang.yeting.wtp.core.annotation;

import wang.yeting.wtp.core.enums.QueueEnums;
import wang.yeting.wtp.core.enums.RejectedExecutionHandlerEnums;

import java.lang.annotation.*;

/**
 * @author : weipeng
 * @date : 2020-07-23 13:49
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Wtp {

    /**
     * pool name
     *
     * @return //pool name
     */
    String value();

    /**
     * default core pool size
     *
     * @return default core pool size
     */
    int defaultCorePoolSize() default 5;

    int defaultMaximumPoolSize() default 5;

    int defaultQueueCapacity() default 50;

    long defaultKeepAliveSeconds() default 60L;

    QueueEnums defaultQueueName() default QueueEnums.resizableCapacityLinkedBlockIngQueue;

    RejectedExecutionHandlerEnums rejectedExecutionHandlerName() default  RejectedExecutionHandlerEnums.abortPolicy;

}

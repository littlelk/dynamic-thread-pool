package wang.yeting.wtp.core.enums;

import lombok.Getter;
import wang.yeting.wtp.core.concurrent.ResizableCapacityLinkedBlockingDeque;
import wang.yeting.wtp.core.concurrent.ResizableCapacityLinkedBlockingQueue;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author : weipeng
 * @date : 2020-07-25 10:35
 */

@Getter
public enum QueueEnums {

    /**
     * Dynamically modifiable {@link LinkedBlockingQueue}
     */
    resizableCapacityLinkedBlockIngQueue("ResizableCapacityLinkedBlockingQueue", ResizableCapacityLinkedBlockingQueue.class),

    /**
     * Dynamically modifiable {@link LinkedBlockingDeque}
     */
    resizableCapacityLinkedBlockingDeque("ResizableCapacityLinkedBlockingDeque", ResizableCapacityLinkedBlockingDeque.class),

    /**
     * details {@link LinkedBlockingQueue}
     */
    linkedBlockingQueue("LinkedBlockingQueue", LinkedBlockingQueue.class),

    /**
     * details {@link LinkedBlockingDeque}
     */
    linkedBlockingDeque("LinkedBlockingDeque", LinkedBlockingDeque.class),

    /**
     * details {@link PriorityBlockingQueue}
     */
    priorityBlockingQueue("PriorityBlockingQueue", PriorityBlockingQueue.class),

    /**
     * details {@link ArrayBlockingQueue}
     */
    arrayBlockingQueue("ArrayBlockingQueue", ArrayBlockingQueue.class),

    /**
     * details {@link SynchronousQueue}
     */
    synchronousQueue("SynchronousQueue", SynchronousQueue.class),

    /**
     * details {@link LinkedTransferQueue}
     */
    linkedTransferQueue("LinkedTransferQueue", LinkedTransferQueue.class);

    private final String queueName;
    private final Class<?> queueClass;

    QueueEnums(String queueName, Class<?> queueClass) {
        this.queueName = queueName;
        this.queueClass = queueClass;
    }

    private static final Map<String, QueueEnums> ENUM_MAP = new HashMap<>(QueueEnums.values().length);

    static {
        for (QueueEnums e : QueueEnums.values()) {
            ENUM_MAP.put(e.getQueueName(), e);
        }
    }

    /**
     * 根据 {@queueName queueName} 获取枚举类
     *
     * @param queueName 枚举类的值
     * @return 枚举类
     */
    public static QueueEnums getByQueueName(String queueName) {
        return ENUM_MAP.get(queueName);
    }

    public static List<String> getAllQueueName() {
        Set<String> keySet = ENUM_MAP.keySet();
        return new ArrayList<>(keySet);
    }

}

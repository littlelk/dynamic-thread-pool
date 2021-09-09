package wang.yeting.wtp.admin.service.alarm;

import wang.yeting.wtp.admin.bean.Wtp;
import wang.yeting.wtp.core.biz.model.WtpLogBo;

/**
 * @author : weipeng
 * @date : 2020-10-28 5:15 下午
 * 可实现本接口，自定义告警，示例：EmailWtpAlarm
 */

public interface WtpAlarm {

    Boolean poolAlarm(Wtp wtp, WtpLogBo wtpLogBo);

    Boolean queueAlarm(Wtp wtp, WtpLogBo wtpLogBo);

}

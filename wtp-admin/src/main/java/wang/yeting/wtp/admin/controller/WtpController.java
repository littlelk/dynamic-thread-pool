package wang.yeting.wtp.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import wang.yeting.wtp.admin.annotation.CurrentUser;
import wang.yeting.wtp.admin.annotation.Permission;
import wang.yeting.wtp.admin.bean.Wtp;
import wang.yeting.wtp.admin.model.PageResponse;
import wang.yeting.wtp.admin.model.Result;
import wang.yeting.wtp.admin.model.bo.UserBo;
import wang.yeting.wtp.admin.model.dto.WtpDto;
import wang.yeting.wtp.admin.model.vo.WtpVo;
import wang.yeting.wtp.admin.service.WtpService;
import wang.yeting.wtp.core.enums.QueueEnums;
import wang.yeting.wtp.core.enums.RejectedExecutionHandlerEnums;

/**
 * @author : weipeng
 * @date : 2020-07-27 15:51
 */
@Permission
@CrossOrigin
@RestController
@RequestMapping("/wtp")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class WtpController {

    private final WtpService wtpService;

    @GetMapping("/page")
    public Result<PageResponse<WtpDto>> page(WtpVo wtpVo, @CurrentUser UserBo userBo) {
        PageResponse<WtpDto> page = wtpService.page(wtpVo, userBo);
        return Result.success(page);
    }

    @PostMapping("/create")
    public Result<Boolean> create(Wtp wtp, @CurrentUser UserBo userBo) {
        Boolean create = wtpService.create(wtp, userBo);
        return Result.success(create);
    }

    @PostMapping("/update")
    public Result<Boolean> update(Wtp wtp, @CurrentUser UserBo userBo) {
        Boolean create = wtpService.update(wtp, userBo);
        return Result.success(create);
    }

    @PostMapping("/syncConfig")
    public Result<Boolean> syncConfig(Wtp wtp, String clusterIds, @CurrentUser UserBo userBo) {
        Boolean syncConfig = wtpService.syncConfig(wtp, clusterIds, userBo);
        return Result.success(syncConfig);
    }

    @PostMapping("/del")
    public Result<Boolean> del(Wtp wtp, @CurrentUser UserBo userBo) {
        Boolean del = wtpService.del(wtp, userBo);
        return Result.success(del);
    }

    @GetMapping("/get")
    public Result<Wtp> get(WtpVo wtpVo, @CurrentUser UserBo userBo) {
        Wtp wtp = wtpService.get(wtpVo, userBo);
        return Result.success(wtp);
    }

    @GetMapping("/queueOptions")
    public Result<PageResponse<String>> queueOptions() {
        return Result.success(new PageResponse<String>().setList(QueueEnums.getAllQueueName()));
    }

    @GetMapping("/rejectedOptions")
    public Result<PageResponse<String>> rejectedOptions() {
        return Result.success(new PageResponse<String>().setList(RejectedExecutionHandlerEnums.getAllRejectedExecutionHandlerName()));
    }
}

package com.ruoyi.bussiness.domain.vo;

import com.ruoyi.common.annotation.Excel;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Admin 端 XGT 锁仓计划列表 VO（PRD §15.5）。
 *
 * 比 TXgtLockPlan 多带：userLoginName（join t_app_user）。
 * @Excel 注解供 ExcelUtil 导出使用。
 *
 * @date 2026-05-12
 */
@Data
public class XgtLockPlanAdminVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Excel(name = "ID")
    private Long id;

    @Excel(name = "用户ID")
    private Long userId;

    @Excel(name = "用户名")
    private String userLoginName;

    @Excel(name = "来源类型")
    private String sourceType;

    @Excel(name = "来源RefID")
    private String sourceRefId;

    @Excel(name = "XGT数量")
    private BigDecimal amountXgt;

    @Excel(name = "USD名义价值")
    private BigDecimal amountUsdNominal;

    @Excel(name = "锁仓开始", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date lockedAt;

    @Excel(name = "预计释放", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date releaseAt;

    @Excel(name = "实际释放", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date releasedAt;

    @Excel(name = "状态")
    private String status;

    @Excel(name = "冻结原因")
    private String frozenReason;

    @Excel(name = "上链地址")
    private String chainAddress;

    @Excel(name = "TxHash")
    private String txHash;

    @Excel(name = "备注")
    private String remark;

    @Excel(name = "创建时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Excel(name = "更新时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}

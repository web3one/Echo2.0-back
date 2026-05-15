package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("t_copy_profit_share_log")
public class TCopyProfitShareLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long copyOrderId;
    private Long traderUserId;
    private Long followerUserId;
    private Long followerPositionId;
    private BigDecimal grossProfit;
    private BigDecimal shareRate;
    private BigDecimal shareAmount;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}

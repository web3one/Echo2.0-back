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
@TableName("t_copy_order")
public class TCopyOrder {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long relationId;
    private Long traderUserId;
    private Long followerUserId;
    private Long traderPositionId;
    private Long followerPositionId;
    private String symbol;
    private Integer type;
    private BigDecimal leverage;
    private BigDecimal copyRatio;
    private BigDecimal traderMargin;
    private BigDecimal followerMargin;
    private BigDecimal profitShareRateSnap;
    private String openStatus;
    private String closeStatus;
    private String failReason;
    private String closeFailReason;
    private BigDecimal followerEarn;
    private BigDecimal profitShareAmount;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}

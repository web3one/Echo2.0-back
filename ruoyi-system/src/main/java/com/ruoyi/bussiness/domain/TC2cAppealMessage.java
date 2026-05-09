package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * C2C申诉消息对象 t_c2c_appeal_message
 */
@Data
@TableName("t_c2c_appeal_message")
public class TC2cAppealMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联t_c2c_order_appeal.id */
    private Long appealId;

    /** 发送者ID */
    private Long senderId;

    /** 1=用户 2=管理员 */
    private Integer senderType;

    /** 1=文本 2=图片 */
    private Integer contentType;

    /** 消息内容 */
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}

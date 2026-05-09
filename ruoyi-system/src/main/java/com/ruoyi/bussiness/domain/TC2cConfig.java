package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * C2C全局配置对象 t_c2c_config
 */
@Data
@TableName("t_c2c_config")
public class TC2cConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 描述 */
    private String configDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}

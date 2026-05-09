package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户推荐关系闭包表（金矿邀请体系 Phase 0.3）
 * 替代 t_app_user.app_parent_ids 的 3 级链路限制，支持无限级祖先 / 后代查询。
 * 每个用户在表中至少有 depth=0 自身行；每多一级祖先多一行。
 *
 * @date 2026-05-08
 */
@Data
@TableName("t_user_relation")
public class TUserRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 后代用户ID（也存自身，depth=0） */
    private Long userId;

    /** 祖先用户ID（自身或任意级祖先） */
    private Long parentId;

    /** 深度：0=自身，1=直接父级，2=祖父级，... 无限级 */
    private Integer depth;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Date createTime;
}

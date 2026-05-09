package com.ruoyi.bussiness.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 双轨树（金矿邀请体系 Phase 0.4）
 * 与 t_user_relation 是两棵不同形态的树：
 *   t_user_relation = 邀请关系树（直推奖依据）
 *   t_binary_tree   = 双轨放置树（团队代理奖 / V1-V5 累计业绩 / 业绩弱区计算依据）
 *
 * @date 2026-05-09
 */
@Data
@TableName("t_binary_tree")
public class TBinaryTree implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DIRECTION_LEFT = "left";
    public static final String DIRECTION_RIGHT = "right";
    public static final String PLACEMENT_AUTO = "auto";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 邀请人用户ID（活跃码持有者，可能不是双轨直接父级） */
    private Long sponsorId;

    /** 双轨直接父级用户ID（NULL = 子树根） */
    private Long parentId;

    /** 本用户在 parent_id 下的方向：left / right */
    private String direction;

    /** 注册时偏好：auto / left / right */
    private String placementSide;

    /** 左区下线总数（不含自身） */
    private Integer leftCount;

    /** 右区下线总数（不含自身） */
    private Integer rightCount;

    @TableField(fill = FieldFill.INSERT)
    private Date placedAt;
}

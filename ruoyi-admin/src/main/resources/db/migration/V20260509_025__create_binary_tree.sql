-- ============================================================================
-- V20260509_025 双轨树 t_binary_tree（金矿邀请体系 Phase 0.4）
-- ============================================================================
-- 业务变更：
--   双轨制 MLM 的核心结构：每个用户在树里有"左区"和"右区"两个子位置。
--   用于后续团队代理奖（按当日新增弱区业绩）/ 累计业绩（V1-V5 升级条件）/
--   团队树可视化等业务。
--
-- 与 t_user_relation 的区别：
--   - t_user_relation：邀请关系树（A 邀请 B），用来发"直推奖"
--   - t_binary_tree：双轨放置树（B 实际挂在哪个用户的左/右下），用来发"团队代理奖"
--   两棵树形态可能不同：B 的邀请人是 A，但 A.left 已满时 B 滑落到 A.left.left，
--   B 的双轨直接父级就是 A.left（不是 A）。
--
-- placement 算法（混合，PM 已选 C 方案）：
--   1. 用户注册时传 placement_side ∈ {auto, left, right}（默认 auto）
--   2. auto = 看 sponsor 当前 left_count vs right_count，塞数量少的那一区
--      （Phase 1 矿机上线后改为按业绩弱区，本表已含 left_count/right_count 计数依据）
--   3. left/right = 沿邀请人的 left/right 方向静态滑落，找到第一个空槽
--   4. 滑落终点的用户成为 newUser.parent_id，方向是 newUser.direction
--
-- 老用户回填策略：
--   本迁移只建表，不回填老数据。原因：老用户没有注册时的 placement 选择记录，
--   按时间序列自动 auto 放置不可逆且容易出错；改为按需回填——
--   当老用户首次邀请新用户时，registerService 会发现 sponsor 没有 t_binary_tree
--   行，自动给 sponsor 创建一行（sponsor_id=NULL, parent_id=NULL，作为子树根），
--   再把 newUser 挂到 sponsor 下。这样老用户在第一次发展下线时才进入双轨树，
--   未发展下线的老用户不进树（不影响他们任何现有功能）。
--
-- 配套 Java：
--   - domain/TBinaryTree.java
--   - mapper/TBinaryTreeMapper.java
--   - service/IBinaryTreeService + impl/BinaryTreeServiceImpl（placement 算法）
--   - TAppUserServiceImpl.insertTAppUser 调 binaryTreeService.placeNewUser
-- ============================================================================

CREATE TABLE IF NOT EXISTS `t_binary_tree` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `sponsor_id` BIGINT NULL COMMENT '邀请人用户ID（活跃码持有者，可能不是双轨直接父级）',
    `parent_id` BIGINT NULL COMMENT '双轨直接父级用户ID（NULL=子树根，比如最顶层老用户）',
    `direction` VARCHAR(8) NULL COMMENT '本用户在 parent_id 下的方向：left / right',
    `placement_side` VARCHAR(8) NOT NULL DEFAULT 'auto' COMMENT '注册时偏好：auto / left / right',
    `left_count` INT NOT NULL DEFAULT 0 COMMENT '本用户左区下线总数（不含自身，用于 auto 弱区计算）',
    `right_count` INT NOT NULL DEFAULT 0 COMMENT '本用户右区下线总数（不含自身，用于 auto 弱区计算）',
    `placed_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入树时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`),
    KEY `idx_parent_dir` (`parent_id`, `direction`) COMMENT '查 parent 下指定方向的子节点',
    KEY `idx_sponsor` (`sponsor_id`) COMMENT '查邀请人维度数据'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='双轨树（金矿邀请体系，团队代理奖 / 双轨业绩计算依据）';

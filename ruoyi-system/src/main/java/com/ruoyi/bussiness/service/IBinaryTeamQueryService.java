package com.ruoyi.bussiness.service;

import com.ruoyi.bussiness.domain.vo.BinaryTeamVO;

/**
 * 双轨团队查询（GET /api/team/binary 后端服务）
 */
public interface IBinaryTeamQueryService {

    BinaryTeamVO queryMyTeam(Long userId);
}

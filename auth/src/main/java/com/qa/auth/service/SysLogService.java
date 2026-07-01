package com.qa.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qa.auth.dto.response.SysLogVO;
import com.qa.auth.entity.SysLogEntity;
import com.qa.auth.mapper.SysLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysLogMapper sysLogMapper;

    public void save(SysLogEntity log) {
        sysLogMapper.insert(log);
    }

    public Page<SysLogVO> page(int pageNum, int pageSize, String username, String module) {
        Page<SysLogEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysLogEntity> wrapper = new LambdaQueryWrapper<SysLogEntity>()
                .orderByDesc(SysLogEntity::getCreatedAt);
        if (username != null && !username.isBlank()) {
            wrapper.like(SysLogEntity::getUsername, username);
        }
        if (module != null && !module.isBlank()) {
            wrapper.eq(SysLogEntity::getModule, module);
        }
        Page<SysLogEntity> result = sysLogMapper.selectPage(page, wrapper);
        Page<SysLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVo).collect(Collectors.toList()));
        return voPage;
    }

    private SysLogVO toVo(SysLogEntity entity) {
        SysLogVO vo = new SysLogVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setUsername(entity.getUsername());
        vo.setModule(entity.getModule());
        vo.setOperation(entity.getOperation());
        vo.setRequestUri(entity.getRequestUri());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setResponseCode(entity.getResponseCode());
        vo.setStatus(entity.getStatus());
        vo.setRequestIp(entity.getRequestIp());
        vo.setCostMs(entity.getCostMs());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}

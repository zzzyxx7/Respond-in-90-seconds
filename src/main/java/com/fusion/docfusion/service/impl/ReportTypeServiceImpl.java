package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.ReportTypeVO;
import com.fusion.docfusion.entity.ReportType;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.ReportTypeMapper;
import com.fusion.docfusion.service.ReportTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportTypeServiceImpl implements ReportTypeService {

    private final ReportTypeMapper reportTypeMapper;

    @Override
    public Result<ReportTypeVO> create(ReportTypeVO vo) {
        if (vo == null || vo.getName() == null || vo.getName().isBlank()) {
            throw new BusinessException("报表类型名称不能为空");
        }
        ReportType entity = new ReportType();
        entity.setName(vo.getName().trim());
        entity.setDescription(vo.getDescription());
        entity.setCreatedAt(LocalDateTime.now());
        reportTypeMapper.insert(entity);

        ReportTypeVO result = toVO(entity);
        return Result.success(result);
    }

    @Override
    public Result<List<ReportTypeVO>> listAll() {
        List<ReportType> list = reportTypeMapper.selectAll();
        List<ReportTypeVO> vos = list.stream().map(this::toVO).toList();
        return Result.success(vos);
    }

    @Override
    public Result<ReportTypeVO> getById(Long id) {
        ReportType entity = reportTypeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("报表类型不存在");
        }
        return Result.success(toVO(entity));
    }

    @Override
    public Result<ReportTypeVO> update(Long id, ReportTypeVO vo) {
        ReportType existing = reportTypeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("报表类型不存在");
        }
        if (vo.getName() != null && !vo.getName().isBlank()) {
            existing.setName(vo.getName().trim());
        }
        existing.setDescription(vo.getDescription());
        reportTypeMapper.update(existing);
        return Result.success(toVO(existing));
    }

    @Override
    public Result<Boolean> delete(Long id) {
        ReportType existing = reportTypeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("报表类型不存在");
        }
        int rows = reportTypeMapper.deleteById(id);
        return Result.success(rows > 0);
    }

    private ReportTypeVO toVO(ReportType entity) {
        ReportTypeVO vo = new ReportTypeVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}


package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.ReportTypeVO;
import com.fusion.docfusion.entity.ReportType;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.ReportTypeMapper;
import com.fusion.docfusion.service.ReportTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportTypeServiceImpl implements ReportTypeService {

    private final ReportTypeMapper reportTypeMapper;

    @Override
    public Result<ReportTypeVO> create(ReportTypeVO vo) {
        if (vo == null || vo.getName() == null || vo.getName().isBlank()) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_NAME_EMPTY);
        }
        ReportType entity = new ReportType();
        entity.setPublicId(generatePublicId());
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
    public Result<ReportTypeVO> getByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_PUBLIC_ID_INVALID);
        }
        ReportType entity = reportTypeMapper.selectByPublicId(publicId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_NOT_FOUND);
        }
        return Result.success(toVO(entity));
    }

    @Override
    public Result<ReportTypeVO> update(Long id, ReportTypeVO vo) {
        ReportType existing = reportTypeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_NOT_FOUND);
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
            throw new BusinessException(ErrorCode.REPORT_TYPE_NOT_FOUND);
        }
        int rows = reportTypeMapper.deleteById(id);
        return Result.success(rows > 0);
    }

    private ReportTypeVO toVO(ReportType entity) {
        ReportTypeVO vo = new ReportTypeVO();
        vo.setId(entity.getId());
        vo.setPublicId(entity.getPublicId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private static String generatePublicId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}


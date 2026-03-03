package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.TemplateProfileVO;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.entity.TemplateProfile;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.mapper.TemplateProfileMapper;
import com.fusion.docfusion.service.TemplateProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateProfileServiceImpl implements TemplateProfileService {

    private final TemplateMapper templateMapper;
    private final TemplateProfileMapper templateProfileMapper;

    @Override
    public Result<TemplateProfileVO> saveOrUpdate(TemplateProfileVO vo) {
        if (vo == null || vo.getTemplateId() == null) {
            throw new BusinessException("模板ID不能为空");
        }
        if (vo.getContent() == null || vo.getContent().isBlank()) {
            throw new BusinessException("模板档案内容不能为空");
        }

        Template template = templateMapper.selectById(vo.getTemplateId());
        if (template == null) {
            throw new BusinessException("模板不存在");
        }

        TemplateProfile existing = templateProfileMapper.selectByTemplateId(vo.getTemplateId());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            TemplateProfile entity = new TemplateProfile();
            entity.setTemplateId(vo.getTemplateId());
            entity.setContent(vo.getContent());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            templateProfileMapper.insert(entity);
            return Result.success(toVO(entity));
        } else {
            existing.setContent(vo.getContent());
            existing.setUpdatedAt(now);
            templateProfileMapper.update(existing);
            return Result.success(toVO(existing));
        }
    }

    @Override
    public Result<TemplateProfileVO> getByTemplateId(Long templateId) {
        if (templateId == null) {
            throw new BusinessException("模板ID不能为空");
        }
        TemplateProfile profile = templateProfileMapper.selectByTemplateId(templateId);
        if (profile == null) {
            return Result.success(null);
        }
        return Result.success(toVO(profile));
    }

    private TemplateProfileVO toVO(TemplateProfile entity) {
        TemplateProfileVO vo = new TemplateProfileVO();
        vo.setTemplateId(entity.getTemplateId());
        vo.setContent(entity.getContent());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}


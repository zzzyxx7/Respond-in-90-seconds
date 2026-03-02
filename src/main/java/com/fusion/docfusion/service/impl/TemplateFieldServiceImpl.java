package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.entity.TemplateField;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.TemplateFieldMapper;
import com.fusion.docfusion.service.TemplateFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateFieldServiceImpl implements TemplateFieldService {

    private final TemplateFieldMapper templateFieldMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveForTemplate(Long templateId, List<TemplateField> fields) {
        if (templateId == null) {
            throw new BusinessException("模板ID不能为空");
        }
        // 先清空旧配置
        templateFieldMapper.deleteByTemplateId(templateId);

        if (fields == null || fields.isEmpty()) {
            return;
        }

        List<TemplateField> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (TemplateField field : fields) {
            if (field.getLocation() == null || field.getLocation().isBlank()) {
                continue;
            }
            field.setId(null);
            field.setTemplateId(templateId);
            field.setCreatedAt(now);
            toSave.add(field);
        }
        if (!toSave.isEmpty()) {
            templateFieldMapper.insertBatch(toSave);
        }
    }

    @Override
    public List<TemplateField> listByTemplateId(Long templateId) {
        if (templateId == null) {
            throw new BusinessException("模板ID不能为空");
        }
        return templateFieldMapper.selectByTemplateId(templateId);
    }
}
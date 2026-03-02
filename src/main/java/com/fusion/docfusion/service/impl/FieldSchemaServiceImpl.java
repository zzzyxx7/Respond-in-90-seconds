package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.entity.FieldSchema;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.FieldSchemaMapper;
import com.fusion.docfusion.service.FieldSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FieldSchemaServiceImpl implements FieldSchemaService {

    private final FieldSchemaMapper fieldSchemaMapper;

    @Override
    public FieldSchema create(FieldSchema schema) {
        if (schema == null || schema.getCode() == null || schema.getCode().isBlank()) {
            throw new BusinessException("字段编码不能为空");
        }
        if (schema.getDisplayName() == null || schema.getDisplayName().isBlank()) {
            throw new BusinessException("字段名称不能为空");
        }
        if (schema.getDataType() == null || schema.getDataType().isBlank()) {
            schema.setDataType("string");
        }
        if (schema.getEnabled() == null) {
            schema.setEnabled(true);
        }
        schema.setId(null);
        schema.setCreatedAt(LocalDateTime.now());
        fieldSchemaMapper.insert(schema);
        return schema;
    }

    @Override
    public List<FieldSchema> listAll() {
        return fieldSchemaMapper.selectAll();
    }
}
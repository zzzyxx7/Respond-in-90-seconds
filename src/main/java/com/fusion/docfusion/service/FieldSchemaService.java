package com.fusion.docfusion.service;

import com.fusion.docfusion.entity.FieldSchema;

import java.util.List;

/**
 * 字段定义的简单服务：开发期用于配置和查看字段字典。
 */
public interface FieldSchemaService {

    FieldSchema create(FieldSchema schema);

    List<FieldSchema> listAll();
}
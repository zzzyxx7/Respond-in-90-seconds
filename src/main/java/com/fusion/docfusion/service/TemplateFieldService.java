package com.fusion.docfusion.service;

import com.fusion.docfusion.entity.TemplateField;

import java.util.List;

/**
 * 模板字段（模板中单元格与字段的映射）服务。
 */
public interface TemplateFieldService {

    /**
     * 覆盖保存某个模板的字段映射：
     * 先删掉该模板已有配置，再插入新的列表。
     */
    void saveForTemplate(Long templateId, List<TemplateField> fields);

    List<TemplateField> listByTemplateId(Long templateId);
}
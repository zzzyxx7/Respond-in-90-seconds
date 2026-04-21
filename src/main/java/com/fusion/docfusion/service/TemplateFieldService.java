package com.fusion.docfusion.service;

import com.fusion.docfusion.entity.TemplateField;

import java.util.List;

public interface TemplateFieldService {

    void saveForTemplate(Long templateId, List<TemplateField> fields);

    List<TemplateField> listByTemplateId(Long templateId);
}

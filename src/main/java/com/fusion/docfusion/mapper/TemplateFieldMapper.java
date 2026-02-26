package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.TemplateField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TemplateFieldMapper {

    int insert(TemplateField entity);

    /**
     * 批量插入，一般在解析模板时使用
     */
    int insertBatch(@Param("list") List<TemplateField> list);

    List<TemplateField> selectByTemplateId(@Param("templateId") Long templateId);

    int deleteByTemplateId(@Param("templateId") Long templateId);
}


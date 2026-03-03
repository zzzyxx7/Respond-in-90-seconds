package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.TemplateProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TemplateProfileMapper {

    int insert(TemplateProfile entity);

    int update(TemplateProfile entity);

    TemplateProfile selectByTemplateId(@Param("templateId") Long templateId);
}


package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.Template;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TemplateMapper {
    int insert(Template entity);
    Template selectById(@Param("id") Long id);
    List<Template> selectAll();

    List<Template> selectAllByOwner(@Param("ownerId") Long ownerId);

    List<Template> selectByReportTypeId(@Param("reportTypeId") Long reportTypeId);

    List<Template> selectByReportTypeIdAndOwner(@Param("reportTypeId") Long reportTypeId, @Param("ownerId") Long ownerId);

    int update(Template entity);

    int deleteById(@Param("id") Long id);
}

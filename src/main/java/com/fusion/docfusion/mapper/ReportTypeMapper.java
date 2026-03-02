package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.ReportType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportTypeMapper {

    int insert(ReportType entity);

    int update(ReportType entity);

    int deleteById(@Param("id") Long id);

    ReportType selectById(@Param("id") Long id);

    List<ReportType> selectAll();
}


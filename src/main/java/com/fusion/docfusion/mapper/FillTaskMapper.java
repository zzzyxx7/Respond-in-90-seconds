package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.FillTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FillTaskMapper {
    int insert(FillTask entity);
    int updateById(FillTask entity);
    FillTask selectById(@Param("id") Long id);
}

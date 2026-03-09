package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.FillTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FillTaskMapper {
    int insert(FillTask entity);
    int updateById(FillTask entity);
    FillTask selectById(@Param("id") Long id);

    List<FillTask> selectByConditions(@Param("userId") Long userId,
                                      @Param("mode") String mode,
                                      @Param("status") String status,
                                      @Param("limit") Integer limit,
                                      @Param("offset") Integer offset);
}

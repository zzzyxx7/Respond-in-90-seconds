package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.ReportTypeVO;

import java.util.List;

public interface ReportTypeService {

    Result<ReportTypeVO> create(ReportTypeVO vo);

    Result<List<ReportTypeVO>> listAll();

    Result<ReportTypeVO> getById(Long id);

    Result<ReportTypeVO> update(Long id, ReportTypeVO vo);

    Result<Boolean> delete(Long id);
}


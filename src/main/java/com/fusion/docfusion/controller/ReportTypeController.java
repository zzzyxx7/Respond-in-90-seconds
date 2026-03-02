package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.ReportTypeVO;
import com.fusion.docfusion.service.ReportTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报表类型管理：增删改查
 */
@RestController
@RequestMapping("/api/report-types")
@RequiredArgsConstructor
@Slf4j
public class ReportTypeController {

    private final ReportTypeService reportTypeService;

    /**
     * 新增报表类型
     * POST /api/report-types
     */
    @PostMapping
    public Result<ReportTypeVO> create(@RequestBody ReportTypeVO vo) {
        log.info("创建报表类型, name={}", vo == null ? null : vo.getName());
        return reportTypeService.create(vo);
    }

    /**
     * 报表类型列表
     * GET /api/report-types
     */
    @GetMapping
    public Result<List<ReportTypeVO>> listAll() {
        log.info("查询报表类型列表");
        return reportTypeService.listAll();
    }

    /**
     * 获取单个报表类型
     * GET /api/report-types/{id}
     */
    @GetMapping("/{id}")
    public Result<ReportTypeVO> getById(@PathVariable Long id) {
        log.info("查询报表类型详情, id={}", id);
        return reportTypeService.getById(id);
    }

    /**
     * 更新报表类型
     * PUT /api/report-types/{id}
     */
    @PutMapping("/{id}")
    public Result<ReportTypeVO> update(@PathVariable Long id, @RequestBody ReportTypeVO vo) {
        log.info("更新报表类型, id={}", id);
        return reportTypeService.update(id, vo);
    }

    /**
     * 删除报表类型
     * DELETE /api/report-types/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        log.info("删除报表类型, id={}", id);
        return reportTypeService.delete(id);
    }
}


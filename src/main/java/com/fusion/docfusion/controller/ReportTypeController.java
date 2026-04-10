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
     * 按 publicId 获取报表类型（用于防枚举）。
     * GET /api/report-types/public/{publicId}
     */
    @GetMapping("/public/{publicId}")
    public Result<ReportTypeVO> getByPublicId(@PathVariable String publicId) {
        log.info("查询报表类型详情(公共ID), publicId={}", publicId);
        return reportTypeService.getByPublicId(publicId);
    }

    /**
     * 管理员：按 publicId 更新报表类型
     * PUT /api/report-types/admin/public/{publicId}
     */
    @PutMapping("/admin/public/{publicId}")
    public Result<ReportTypeVO> updateByPublicId(@PathVariable String publicId, @RequestBody ReportTypeVO vo) {
        log.info("更新报表类型(公共ID), publicId={}", publicId);
        ReportTypeVO existing = reportTypeService.getByPublicId(publicId).getData();
        if (existing == null || existing.getId() == null) {
            return reportTypeService.getByPublicId(publicId);
        }
        return reportTypeService.update(existing.getId(), vo);
    }

    /**
     * 管理员：按 publicId 删除报表类型
     * DELETE /api/report-types/admin/public/{publicId}
     */
    @DeleteMapping("/admin/public/{publicId}")
    public Result<Boolean> deleteByPublicId(@PathVariable String publicId) {
        log.info("删除报表类型(公共ID), publicId={}", publicId);
        ReportTypeVO existing = reportTypeService.getByPublicId(publicId).getData();
        if (existing == null || existing.getId() == null) {
            return Result.success(false);
        }
        return reportTypeService.delete(existing.getId());
    }
}


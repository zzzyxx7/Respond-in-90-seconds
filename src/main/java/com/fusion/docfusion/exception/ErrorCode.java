package com.fusion.docfusion.exception;

import lombok.Getter;

/**
 * 统一业务错误码：与 HTTP 语义对齐的 status + 机器可读枚举名。
 * 接口返回 {@link com.fusion.docfusion.common.Result#errorCode} 供前端/Apifox 断言。
 */
@Getter
public enum ErrorCode {
    // 通用
    BUSINESS_ERROR(500, "业务处理失败"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "无权限访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    RATE_LIMITED(429, "请求过于频繁"),
    PARAM_VALIDATION_ERROR(400, "参数校验失败"),
    REQUEST_BODY_INVALID(400, "请求体格式错误"),

    // 认证
    AUTH_LOGIN_REQUIRED(401, "请先登录"),
    AUTH_INVALID_CREDENTIALS(401, "用户名或密码错误"),
    AUTH_USERNAME_EMPTY(400, "用户名不能为空"),
    AUTH_PASSWORD_EMPTY(400, "密码不能为空"),
    AUTH_USERNAME_LENGTH_INVALID(400, "用户名长度应在 3~50 之间"),
    AUTH_PASSWORD_LENGTH_INVALID(400, "密码长度应在 6~100 之间"),
    AUTH_USERNAME_EXISTS(400, "用户名已存在"),

    // 文档集 / 文档
    DOC_UPLOAD_EMPTY(400, "请至少上传一个文档"),
    DOC_UPLOAD_TOO_LARGE(400, "单次上传文件总大小过大，请控制在 100MB 以内"),
    DOC_UPLOAD_DIR_FAIL(500, "创建上传目录失败"),
    DOC_SET_DIR_FAIL(500, "创建文档集目录失败"),
    DOC_INVALID_PATH(400, "非法文件名或路径"),
    DOC_SAVE_FAILED(500, "保存文件失败"),
    DOC_NO_VALID_FILES(400, "没有可保存的文档"),
    DOCUMENT_NOT_FOUND(404, "文档不存在"),
    DOCUMENT_FILE_MISSING(404, "文档文件不存在"),
    DOCUMENT_SET_NOT_FOUND(404, "文档集不存在"),
    DOCUMENT_SET_FORBIDDEN(403, "无权使用该文档集"),
    DOCUMENT_SET_DELETE_FORBIDDEN(403, "无权删除该文档集"),
    DOCUMENT_SET_VIEW_FORBIDDEN(403, "无权查看该文档集"),
    DOCUMENT_SET_EMPTY_DOCS(400, "文档集中没有文档"),

    // 模板
    TEMPLATE_UPLOAD_EMPTY(400, "请选择模板文件"),
    TEMPLATE_TOO_LARGE(400, "模板文件过大，请控制在 50MB 以内"),
    TEMPLATE_FILENAME_INVALID(400, "文件名无效"),
    TEMPLATE_TYPE_UNSUPPORTED(400, "仅支持 word(docx/doc) 或 excel(xlsx/xls) 模板"),
    TEMPLATE_DIR_FAIL(500, "创建模板目录失败"),
    TEMPLATE_PATH_INVALID(400, "非法模板文件名或路径"),
    TEMPLATE_SAVE_FAILED(500, "保存模板失败"),
    TEMPLATE_NOT_FOUND(404, "模板不存在"),
    TEMPLATE_FORBIDDEN_VIEW(403, "无权访问该模板"),
    TEMPLATE_FORBIDDEN_UPDATE(403, "无权修改该模板"),
    TEMPLATE_FORBIDDEN_DELETE(403, "无权删除该模板"),

    // 填表任务
    TASK_NOT_FOUND(404, "任务不存在"),
    TASK_FORBIDDEN(403, "无权访问该任务"),
    TASK_OPERATION_FORBIDDEN(403, "无权操作该任务"),
    TASK_RERUN_NOT_ALLOWED(400, "仅 FAILED/TIMEOUT 状态任务允许人工重跑"),
    TASK_CANCEL_NOT_ALLOWED(400, "仅 PENDING/RUNNING 状态任务允许取消"),
    TASK_CANCELLED(400, "任务已取消"),
    FILL_RATE_LIMITED(429, "提交操作过于频繁，请在 1 分钟后再试"),
    TASK_MODE_UNKNOWN(400, "未知任务模式"),

    // 报表 / 字段 / 模板字段 / 档案
    REPORT_TYPE_NAME_EMPTY(400, "报表类型名称不能为空"),
    REPORT_TYPE_NOT_FOUND(404, "报表类型不存在"),
    FIELD_SCHEMA_CODE_EMPTY(400, "字段编码不能为空"),
    FIELD_SCHEMA_NAME_EMPTY(400, "字段名称不能为空"),
    TEMPLATE_ID_REQUIRED(400, "模板ID不能为空"),
    TEMPLATE_PROFILE_ID_REQUIRED(400, "模板ID不能为空"),
    TEMPLATE_PROFILE_CONTENT_EMPTY(400, "模板档案内容不能为空"),

    // AI 填表
    FILL_TASK_INVALID_TEMPLATE(400, "填表任务无效：缺少 templateId"),
    FILL_TASK_DOCS_EMPTY(400, "填表任务无效：文档列表为空"),
    TEMPLATE_FILE_MISSING(404, "模板文件不存在"),
    FREE_MODE_TASK_INVALID(400, "自由模式任务无效"),
    FREE_MODE_DOCS_EMPTY(400, "自由模式任务无效：文档列表为空"),
    AI_INPUT_DOC_MISSING(404, "输入文档不存在"),
    AI_CREATE_REMOTE_FAILED(502, "AI 创建任务失败"),
    AI_TASK_ID_MISSING(502, "AI 创建任务返回缺少 task_id"),
    AI_TASK_FAILED(502, "AI 填表任务失败"),
    AI_TASK_TIMEOUT(504, "AI 填表任务超时"),
    AI_DOWNLOAD_PATH_INVALID(500, "下载结果文件路径非法"),
    AI_DOWNLOAD_FAILED(502, "AI 结果下载失败"),
    FREE_MODE_TEMPLATE_NOT_CONFIGURED(500, "自由模式未配置 ai.fill.free-template-path"),
    FREE_MODE_TEMPLATE_MISSING(404, "自由模式默认模板不存在"),
    AI_POLL_INTERRUPTED(500, "AI 任务轮询被中断"),

    // AI 抽取
    AI_ANALYZE_FILE_MISSING(400, "待分析的文件不存在"),
    AI_MOCK_PARSE_FAILED(500, "Mock AI 返回 JSON 解析失败"),
    AI_EXTRACT_HTTP_FAILED(502, "AI 抽取服务调用失败"),
    AI_EXTRACT_RETRY_INTERRUPTED(500, "AI 调用重试被中断"),
    AI_EXTRACT_FAILED(502, "AI 抽取服务失败");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}

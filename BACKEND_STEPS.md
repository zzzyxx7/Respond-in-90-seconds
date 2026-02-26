# DocFusion 后端步骤说明（A23 比赛）

## 一、你的水平与项目风格

已参考你的 **DaMaiPlatform** 和 **ticket-system** 项目，本后端采用相同风格：

- Spring Boot 3.2 + MyBatis + MySQL
- 统一返回 `Result<T>`、全局异常 `BusinessException` + `GlobalExceptionHandler`
- 分层：Controller → Service(impl) → Mapper + entity/dto

## 二、已完成的骨架

### 1. 依赖与配置

- **pom.xml**：Spring Boot 3.2.5、MyBatis、Validation、**Apache POI**（docx/xlsx 解析，后续填表用）
- **application.yml**：数据源（mydatabase）、文件上传大小、**上传目录**（`uploads/docs`、`uploads/templates`、`uploads/results`）
- **SecurityConfig**：`/api/**` 全部放行（Demo 阶段便于联调）

### 2. 数据库

- **schema.sql**：`document_set`、`document`、`template`、`fill_task` 四张表
- 首次运行前：启动 MySQL（见下方「本地运行」），在 `mydatabase` 中执行 `schema.sql`

### 3. 接口一览

| 接口 | 说明 |
|------|------|
| `POST /api/documents/upload` | 一次性上传多个文档（form-data，key=`files`），创建文档集，返回 `documentSetId` |
| `GET /api/documents/sets/{documentSetId}` | 查询文档集详情（含文档列表） |
| `POST /api/templates/upload` | 上传模板（form-data，key=`file`），支持 word/excel |
| `GET /api/templates/list` | 模板列表 |
| `GET /api/templates/{templateId}` | 模板详情 |
| `POST /api/fill/submit` | 提交填表任务，body：`{"documentSetId":1,"templateId":1}` |
| `GET /api/fill/tasks/{taskId}` | 查询任务状态与结果文件路径 |
| `GET /api/fill/download/{taskId}` | 下载填表结果文件 |

### 4. 当前填表逻辑（占位）

- `FillServiceImpl.submitFill()` 目前只做：**把模板文件复制到结果目录**并标记任务成功。
- **你与 AI 同学需要补充**：从文档集中抽取信息 → 按模板字段填表（POI 写 Excel/Word）。

## 三、本地运行

1. **启动 MySQL**（二选一）  
   - 使用 compose：在项目根目录执行 `docker compose up -d`，确保 `mydatabase` 已创建。  
   - 或本机已有 MySQL：在 `mydatabase` 中执行 `src/main/resources/schema.sql`。

2. **修改数据源（如需要）**  
   - 编辑 `src/main/resources/application.yml` 中的 `spring.datasource`（url、username、password）与本地一致。

3. **启动应用**  
   - `mvn spring-boot:run` 或运行 `DocFusionApplication`。

4. **验证**  
   - 用 Postman/Apifox 调用：  
     - 上传文档 → 拿到 `documentSetId`  
     - 上传模板 → 拿到 `templateId`  
     - `POST /api/fill/submit` → 拿到 `taskId`  
     - `GET /api/fill/download/{taskId}` 下载文件（当前应为模板副本）。

## 四、你接下来要做的（按优先级）

1. **与前端对齐**  
   - 把上面接口表发给前端/移动端同学，约定请求/响应格式（已统一为 `Result<T>`）。

2. **与 AI 同学约定抽取接口**  
   - 由 AI 提供「从一段文本中按字段抽取」的 API（或本地服务）。  
   - 你在后端调用该 API，把结果落库或直接用于填表（例如先建 `extracted_value` 表存抽取结果，再在填表时按模板字段名查询）。

3. **实现真正的填表逻辑**  
   - 用 **POI** 读取模板（xlsx/docx），识别待填单元格（如占位符 `{{字段名}}` 或固定行列）。  
   - 根据模板字段，从「抽取结果」或 AI 实时返回的数据中取值，写回 Excel/Word。  
   - 保存到 `uploads/results`，把路径写入 `fill_task.result_file_path`。

4. **文档解析与入库**  
   - 在 `DocumentServiceImpl` 或单独服务中，用 POI 解析 docx/xlsx，得到纯文本；txt/md 直接读。  
   - 将解析后的文本交给 AI 做抽取，或先存入 `document_content` 表供后续填表使用。

5. **性能与比赛要求**  
   - 单次填表响应时间 ≤90 秒：若 AI 调用慢，可考虑异步任务 + 轮询 `GET /api/fill/tasks/{taskId}`。  
   - 准确率 ≥80%：主要靠 AI 抽取与模板字段设计，后端保证数据正确写入、不丢字段。

## 五、目录结构（当前）

```
src/main/java/com/fusion/docfusion/
├── DocFusionApplication.java
├── common/Result.java
├── config/SecurityConfig.java, UploadProperties.java
├── controller/DocumentController.java, TemplateController.java, FillController.java
├── dto/DocumentSetVO, DocumentVO, TemplateVO, FillTaskVO, FillRequest
├── entity/DocumentSet, Document, Template, FillTask
├── exception/BusinessException.java, GlobalExceptionHandler.java
├── mapper/*.java
├── service/*.java + service/impl/*.java
src/main/resources/
├── application.yml
├── schema.sql
└── mapper/*.xml
```

如果你告诉我「先做文档解析」还是「先做填表逻辑」，我可以按其中一条线写出具体代码示例（含 POI 读写的类和方法名）。

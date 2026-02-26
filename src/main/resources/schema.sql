/******************************
 * 0. 创建并选择数据库
 ******************************/
CREATE DATABASE IF NOT EXISTS mydatabase
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE mydatabase;


/******************************
 * 1. 文档集表：document_set
 * 一次上传的一批混合文档（官方测试集就是一个文档集）
 ******************************/
CREATE TABLE IF NOT EXISTS document_set (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文档集ID',
                                            name VARCHAR(255) NOT NULL COMMENT '文档集名称/描述，例如：官方测试集1',
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='文档集：一次批量上传的文档集合';


/******************************
 * 2. 文档表：document
 * 存每个具体文件的基本信息（不存内容，只存路径）
 * 关系：document_set(1) ——> (N) document
 ******************************/
CREATE TABLE IF NOT EXISTS document (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文档ID',
                                        document_set_id BIGINT NOT NULL COMMENT '所属文档集ID，外键到 document_set.id',
                                        file_name VARCHAR(255) NOT NULL COMMENT '原始文件名，例如 report1.docx',
                                        file_type VARCHAR(32) NOT NULL COMMENT '文件类型：docx/md/xlsx/txt',
                                        file_path VARCHAR(512) NOT NULL COMMENT '服务器上保存的文件路径（相对或绝对）',
                                        file_size BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',

                                        INDEX idx_document_set_id (document_set_id),
                                        CONSTRAINT fk_document_set
                                            FOREIGN KEY (document_set_id) REFERENCES document_set(id)
                                                ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='文档：单个文件的元数据';


/******************************
 * 3. 模板表：template
 * 存每个 word / excel 模板文件的基本信息
 * 比赛中：评委上传的5个表格模板，就会各生成一条记录
 ******************************/
CREATE TABLE IF NOT EXISTS template (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模板ID',
                                        file_name VARCHAR(255) NOT NULL COMMENT '模板文件名，例如 template1.xlsx',
                                        file_type VARCHAR(32) NOT NULL COMMENT '模板类型：word/excel',
                                        file_path VARCHAR(512) NOT NULL COMMENT '模板文件在服务器上的路径',
                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='模板：word/excel 模板文件';


/******************************
 * 4. 填表任务表：fill_task
 * 一次“根据某个文档集 + 某个模板进行自动填表”的任务
 * 关系：
 *   document_set(1) ——> (N) fill_task
 *   template(1)     ——> (N) fill_task
 ******************************/
CREATE TABLE IF NOT EXISTS fill_task (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '填表任务ID',
                                         document_set_id BIGINT NOT NULL COMMENT '使用的文档集ID',
                                         template_id BIGINT NOT NULL COMMENT '使用的模板ID',
                                         status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED',
                                         result_file_path VARCHAR(512) DEFAULT NULL COMMENT '生成的结果表格文件路径',
                                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
                                         finished_at DATETIME DEFAULT NULL COMMENT '任务完成时间',
                                         error_message VARCHAR(512) DEFAULT NULL COMMENT '如失败，记录错误信息',

                                         INDEX idx_fill_document_set_id (document_set_id),
                                         INDEX idx_fill_template_id (template_id),

                                         CONSTRAINT fk_fill_task_document_set
                                             FOREIGN KEY (document_set_id) REFERENCES document_set(id)
                                                 ON DELETE CASCADE,
                                         CONSTRAINT fk_fill_task_template
                                             FOREIGN KEY (template_id) REFERENCES template(id)
                                                 ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='填表任务：异步调用单元，记录每次自动填表的状态与结果';


/******************************
 * 5. 字段定义表：field_schema
 * 描述“系统里可以抽取哪些字段”
 * 不绑定具体业务场景，只是一个字段字典
 ******************************/
CREATE TABLE IF NOT EXISTS field_schema (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字段定义ID',
                                            code VARCHAR(100) NOT NULL UNIQUE COMMENT '字段编码，如 student_name, amount',
                                            display_name VARCHAR(255) NOT NULL COMMENT '字段中文名，例如 学生姓名、金额',
                                            data_type VARCHAR(50) NOT NULL COMMENT '数据类型：string/number/date 等',
                                            description VARCHAR(512) DEFAULT NULL COMMENT '字段含义说明',
                                            enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='字段定义：描述可抽取的业务字段';


/******************************
 * 6. 模板字段表：template_field
 * 描述“某个模板中，哪个位置要填哪个字段”
 * 关系：
 *   template(1)     ——> (N) template_field
 *   field_schema(1) ——> (N) template_field
 ******************************/
CREATE TABLE IF NOT EXISTS template_field (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模板字段ID',
                                              template_id BIGINT NOT NULL COMMENT '所属模板ID',
                                              field_schema_id BIGINT NOT NULL COMMENT '对应的字段定义ID',
                                              location VARCHAR(100) NOT NULL COMMENT '模板中的位置，如 A3/B5 或 {{student_name}}',
                                              format VARCHAR(100) DEFAULT NULL COMMENT '展示格式（如金额两位小数、日期格式等）',
                                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                                              INDEX idx_template_field_template_id (template_id),
                                              INDEX idx_template_field_field_schema_id (field_schema_id),

                                              CONSTRAINT fk_template_field_template
                                                  FOREIGN KEY (template_id) REFERENCES template(id)
                                                      ON DELETE CASCADE,
                                              CONSTRAINT fk_template_field_schema
                                                  FOREIGN KEY (field_schema_id) REFERENCES field_schema(id)
                                                      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='模板字段：模板中的具体填充位置与字段的映射关系';


/******************************
 * 7. 抽取结果表：extracted_value
 * 存“某个文档里抽取到的某个字段的值”
 * 关系：
 *   document(1)      ——> (N) extracted_value
 *   field_schema(1)  ——> (N) extracted_value
 ******************************/
CREATE TABLE IF NOT EXISTS extracted_value (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '抽取结果ID',
                                               document_id BIGINT NOT NULL COMMENT '来源文档ID',
                                               field_schema_id BIGINT NOT NULL COMMENT '字段定义ID',
                                               field_value TEXT NOT NULL COMMENT '抽取到的字段值（字符串形式）',
                                               confidence DECIMAL(5,4) DEFAULT NULL COMMENT '置信度 0~1，例如 0.9234',
                                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                                               INDEX idx_extracted_document_id (document_id),
                                               INDEX idx_extracted_field_schema_id (field_schema_id),

                                               CONSTRAINT fk_extracted_document
                                                   FOREIGN KEY (document_id) REFERENCES document(id)
                                                       ON DELETE CASCADE,
                                               CONSTRAINT fk_extracted_field_schema
                                                   FOREIGN KEY (field_schema_id) REFERENCES field_schema(id)
                                                       ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    COMMENT='抽取结果：非结构化文档中抽取出的字段值';
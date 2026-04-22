-- MySQL dump 10.13  Distrib 8.0.42, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: mydatabase
-- ------------------------------------------------------
-- Server version	8.0.42

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `mydatabase`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `mydatabase` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `mydatabase`;

--
-- Table structure for table `document`
--

DROP TABLE IF EXISTS `document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `public_id` varchar(64) NOT NULL,
  `document_set_id` bigint NOT NULL COMMENT '所属文档集ID，外键到 document_set.id',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名，例如 report1.docx',
  `file_type` varchar(32) NOT NULL COMMENT '文件类型：docx/md/xlsx/txt',
  `file_path` varchar(512) NOT NULL COMMENT '服务器上保存的文件路径（相对或绝对）',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_public_id` (`public_id`),
  KEY `idx_document_set_id` (`document_set_id`),
  CONSTRAINT `fk_document_set` FOREIGN KEY (`document_set_id`) REFERENCES `document_set` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=153 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档：单个文件的元数据';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `document_set`
--

DROP TABLE IF EXISTS `document_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_set` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档集ID',
  `public_id` varchar(64) NOT NULL,
  `owner_id` bigint DEFAULT NULL COMMENT '创建该文档集的用户ID',
  `name` varchar(255) NOT NULL COMMENT '文档集名称/描述，例如：官方测试集1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_set_public_id` (`public_id`),
  KEY `idx_document_set_owner_id` (`owner_id`)
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档集：一次批量上传的文档集合';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `extracted_value`
--

DROP TABLE IF EXISTS `extracted_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `extracted_value` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '抽取结果ID',
  `public_id` varchar(64) NOT NULL,
  `document_id` bigint NOT NULL COMMENT '来源文档ID',
  `field_schema_id` bigint NOT NULL COMMENT '字段定义ID',
  `field_value` text NOT NULL COMMENT '抽取到的字段值（字符串形式）',
  `confidence` decimal(5,4) DEFAULT NULL COMMENT '置信度 0~1，例如 0.9234',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_extracted_value_public_id` (`public_id`),
  KEY `idx_extracted_document_id` (`document_id`),
  KEY `idx_extracted_field_schema_id` (`field_schema_id`),
  CONSTRAINT `fk_extracted_document` FOREIGN KEY (`document_id`) REFERENCES `document` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_extracted_field_schema` FOREIGN KEY (`field_schema_id`) REFERENCES `field_schema` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽取结果：非结构化文档中抽取出的字段值';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `field_schema`
--

DROP TABLE IF EXISTS `field_schema`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `field_schema` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字段定义ID',
  `public_id` varchar(64) NOT NULL,
  `code` varchar(100) NOT NULL COMMENT '字段编码，如 student_name, amount',
  `display_name` varchar(255) NOT NULL COMMENT '字段中文名，例如 学生姓名、金额',
  `data_type` varchar(50) NOT NULL COMMENT '数据类型：string/number/date 等',
  `description` varchar(512) DEFAULT NULL COMMENT '字段含义说明',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：1-启用，0-停用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  UNIQUE KEY `uk_field_schema_public_id` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字段定义：描述可抽取的业务字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fill_task`
--

DROP TABLE IF EXISTS `fill_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fill_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '填表任务ID',
  `public_id` varchar(64) NOT NULL,
  `user_id` bigint DEFAULT NULL COMMENT '创建任务的用户ID，可为空（未登录）',
  `document_set_id` bigint NOT NULL COMMENT '使用的文档集ID',
  `template_id` bigint DEFAULT NULL COMMENT '使用的模板ID，FREE 模式为空',
  `mode` varchar(32) NOT NULL DEFAULT 'TEMPLATE' COMMENT '任务模式：TEMPLATE（模板模式）/ FREE（自由模式）',
  `user_requirement` varchar(512) DEFAULT NULL COMMENT '自由模式下的用户需求描述',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED',
  `result_file_path` varchar(512) DEFAULT NULL COMMENT '生成的结果表格文件路径',
  `result_files_json` json DEFAULT NULL COMMENT '多结果文件清单（JSON）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
  `finished_at` datetime DEFAULT NULL COMMENT '任务完成时间',
  `error_message` varchar(512) DEFAULT NULL COMMENT '如失败，记录错误信息',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本号，每次更新+1',
  `ai_remote_task_id` varchar(128) DEFAULT NULL COMMENT '远端 AI 任务ID',
  `ai_provider` varchar(64) DEFAULT NULL COMMENT 'AI 供应商标识',
  `ai_model` varchar(128) DEFAULT NULL COMMENT 'AI 模型名',
  `input_tokens` bigint DEFAULT NULL COMMENT '输入 token 数',
  `output_tokens` bigint DEFAULT NULL COMMENT '输出 token 数',
  `total_tokens` bigint DEFAULT NULL COMMENT '总 token 数',
  `ai_cost` decimal(18,8) DEFAULT NULL COMMENT '本次 AI 成本',
  `ai_cost_currency` varchar(16) DEFAULT NULL COMMENT '成本币种',
  `ai_cost_estimated` tinyint(1) DEFAULT NULL COMMENT '是否后端估算成本',
  `ai_usage_raw` json DEFAULT NULL COMMENT '供应商 usage 原始 JSON',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fill_task_public_id` (`public_id`),
  KEY `idx_fill_document_set_id` (`document_set_id`),
  KEY `idx_fill_template_id` (`template_id`),
  KEY `idx_fill_user_id` (`user_id`),
  CONSTRAINT `fk_fill_task_document_set` FOREIGN KEY (`document_set_id`) REFERENCES `document_set` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_fill_task_template` FOREIGN KEY (`template_id`) REFERENCES `template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=116 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='填表任务：异步调用单元，记录每次自动填表的状态与结果';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fill_task_step`
--

DROP TABLE IF EXISTS `fill_task_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fill_task_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务步骤ID',
  `task_id` bigint NOT NULL COMMENT '所属任务ID',
  `step_code` varchar(64) NOT NULL COMMENT '步骤编码：RAG/EXTRACT/FILL/GENERATE 等',
  `step_name` varchar(128) NOT NULL COMMENT '步骤名称（展示用）',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '步骤状态：PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
  `message` varchar(512) DEFAULT NULL COMMENT '补充信息（如命中文档数/字段数等）',
  `error_message` varchar(512) DEFAULT NULL COMMENT '失败原因（如失败）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fill_task_step_task_step` (`task_id`,`step_code`),
  KEY `idx_fill_task_step_task_id` (`task_id`),
  CONSTRAINT `fk_fill_task_step_task` FOREIGN KEY (`task_id`) REFERENCES `fill_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1919 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='填表任务步骤：用于展示任务链路与耗时';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `report_type`
--

DROP TABLE IF EXISTS `report_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '报表类型ID',
  `public_id` varchar(64) NOT NULL,
  `name` varchar(255) NOT NULL COMMENT '报表类型名称，例如 合同收支汇总表',
  `description` varchar(512) DEFAULT NULL COMMENT '报表类型说明，例如 适用于年度合同统计',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_type_public_id` (`public_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表类型：描述业务上的报表类别，例如合同汇总表、员工信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `template`
--

DROP TABLE IF EXISTS `template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `public_id` varchar(64) NOT NULL,
  `owner_id` bigint DEFAULT NULL COMMENT '创建该模板的用户ID',
  `report_type_id` bigint DEFAULT NULL COMMENT '所属报表类型ID，可为空表示未分类',
  `file_name` varchar(255) NOT NULL COMMENT '模板文件名，例如 template1.xlsx',
  `file_type` varchar(32) NOT NULL COMMENT '模板类型：word/excel',
  `file_path` varchar(512) NOT NULL COMMENT '模板文件在服务器上的路径',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_public_id` (`public_id`),
  KEY `idx_template_report_type_id` (`report_type_id`),
  KEY `idx_template_owner_id` (`owner_id`),
  CONSTRAINT `fk_template_report_type` FOREIGN KEY (`report_type_id`) REFERENCES `report_type` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板：word/excel 模板文件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `template_field`
--

DROP TABLE IF EXISTS `template_field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板字段ID',
  `public_id` varchar(64) NOT NULL,
  `template_id` bigint NOT NULL COMMENT '所属模板ID',
  `field_schema_id` bigint NOT NULL COMMENT '对应的字段定义ID',
  `location` varchar(100) NOT NULL COMMENT '模板中的位置，如 A3/B5 或 {{student_name}}',
  `format` varchar(100) DEFAULT NULL COMMENT '展示格式（如金额两位小数、日期格式等）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_field_public_id` (`public_id`),
  KEY `idx_template_field_template_id` (`template_id`),
  KEY `idx_template_field_field_schema_id` (`field_schema_id`),
  CONSTRAINT `fk_template_field_schema` FOREIGN KEY (`field_schema_id`) REFERENCES `field_schema` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_template_field_template` FOREIGN KEY (`template_id`) REFERENCES `template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板字段：模板中的具体填充位置与字段的映射关系';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `template_profile`
--

DROP TABLE IF EXISTS `template_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板档案ID',
  `public_id` varchar(64) NOT NULL,
  `template_id` bigint NOT NULL COMMENT '所属模板ID',
  `content` text NOT NULL COMMENT '模板档案内容，通常为 JSON（report_profile.json）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_profile_public_id` (`public_id`),
  KEY `idx_template_profile_template_id` (`template_id`),
  CONSTRAINT `fk_template_profile_template` FOREIGN KEY (`template_id`) REFERENCES `template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板档案：存放每个模板的配置档案（如 report_profile.json）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt 加密后的密码',
  `role` varchar(50) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `avatar_url` varchar(512) DEFAULT NULL COMMENT '头像URL（可为空）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表：登录账号信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'mydatabase'
--

--
-- Dumping routines for database 'mydatabase'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-21 22:14:48

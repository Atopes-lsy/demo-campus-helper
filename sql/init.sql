CREATE DATABASE IF NOT EXISTS campus_helper DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
USE campus_helper;

CREATE TABLE IF NOT EXISTS `student` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_no` VARCHAR(20) NOT NULL COMMENT '学号',
  `name` VARCHAR(50) NOT NULL,
  `phone` VARCHAR(11) NOT NULL,
  `dormitory` VARCHAR(100) NOT NULL,
  `avatar_url` VARCHAR(255),
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-正常 0-禁用',
  `total_orders` INT DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生用户';

CREATE TABLE IF NOT EXISTS `runner` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `runner_no` VARCHAR(20) NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `phone` VARCHAR(11) NOT NULL,
  `id_card` VARCHAR(18) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-空闲 1-接单中 2-休息',
  `rating` DECIMAL(2,1) DEFAULT 5.0,
  `completed_orders` INT DEFAULT 0,
  `latitude` DECIMAL(10,7),
  `longitude` DECIMAL(10,7),
  `max_concurrent` INT DEFAULT 3,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_runner_no` (`runner_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跑腿员';

CREATE TABLE IF NOT EXISTS `order_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(30) NOT NULL,
  `student_id` BIGINT NOT NULL,
  `runner_id` BIGINT,
  `order_type` TINYINT NOT NULL COMMENT '1-外卖 2-包裹',
  `title` VARCHAR(100) NOT NULL,
  `description` VARCHAR(500),
  `pickup_location` VARCHAR(200) NOT NULL,
  `delivery_location` VARCHAR(200) NOT NULL,
  `pickup_lat` DECIMAL(10,7),
  `pickup_lng` DECIMAL(10,7),
  `delivery_lat` DECIMAL(10,7),
  `delivery_lng` DECIMAL(10,7),
  `amount` DECIMAL(8,2) NOT NULL,
  `tip` DECIMAL(6,2) DEFAULT 0,
  `packages` JSON COMMENT '包裹列表 [{size: small/medium/large}]',
  `pickup_code` VARCHAR(100) COMMENT '取件码',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态',
  `timeout_minutes` INT DEFAULT 30,
  `completed_at` DATETIME,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_runner_id` (`runner_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

CREATE TABLE IF NOT EXISTS `order_tracking` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `status_from` TINYINT,
  `status_to` TINYINT NOT NULL,
  `description` VARCHAR(200),
  `operator_id` BIGINT,
  `operator_type` VARCHAR(10) COMMENT 'STUDENT/RUNNER/SYSTEM',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单追踪';

CREATE TABLE IF NOT EXISTS `review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `reviewer_type` TINYINT NOT NULL COMMENT '1-学生评 2-跑腿评',
  `rating` TINYINT NOT NULL COMMENT '1-5',
  `content` VARCHAR(500),
  `tags` VARCHAR(200),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_reviewer` (`order_id`, `reviewer_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价';

CREATE TABLE IF NOT EXISTS `payment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `payment_no` VARCHAR(32) NOT NULL,
  `order_id` BIGINT NOT NULL,
  `payer_id` BIGINT NOT NULL,
  `payee_id` BIGINT NOT NULL,
  `amount` DECIMAL(8,2) NOT NULL,
  `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-待支付 1-支付中 2-已支付 3-已结算 4-已退款',
  `pay_channel` VARCHAR(20),
  `transaction_id` VARCHAR(64),
  `paid_at` DATETIME,
  `settled_at` DATETIME,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录';

CREATE TABLE IF NOT EXISTS `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `user_type` VARCHAR(10) NOT NULL,
  `notify_type` TINYINT NOT NULL COMMENT '1-订单 2-派单 3-支付 4-公告',
  `title` VARCHAR(100) NOT NULL,
  `content` VARCHAR(500) NOT NULL,
  `related_order_id` BIGINT,
  `is_read` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`, `user_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息';

CREATE TABLE IF NOT EXISTS `ai_chat_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` VARCHAR(64) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `user_type` VARCHAR(10) NOT NULL,
  `role` VARCHAR(10) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
  `content` TEXT NOT NULL,
  `intent` VARCHAR(20),
  `token_count` INT,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话记录';

CREATE TABLE IF NOT EXISTS `anomaly_alert` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_type` VARCHAR(20) NOT NULL,
  `target_id` BIGINT NOT NULL,
  `anomaly_type` VARCHAR(30) NOT NULL,
  `severity` TINYINT NOT NULL COMMENT '1-低 2-中 3-高',
  `description` VARCHAR(500) NOT NULL,
  `ai_analysis` TEXT,
  `is_resolved` TINYINT NOT NULL DEFAULT 0,
  `detected_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常检测记录';

CREATE TABLE IF NOT EXISTS `pricing_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  `condition_type` VARCHAR(30) NOT NULL,
  `condition_expr` VARCHAR(200) NOT NULL,
  `price_factor` DECIMAL(4,2) NOT NULL,
  `ai_reasoning` TEXT,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能定价策略';

INSERT INTO `student` (`student_no`, `name`, `phone`, `dormitory`, `status`, `total_orders`) VALUES
('20240101001','王小明','13800001001','东区3号楼501室',1,3),
('20240101002','李小红','13800001002','西区1号楼302室',1,1),
('20240101003','张大伟','13800001003','南区5号楼101室',1,5),
('20240101004','赵晓芳','13800001004','北区2号楼203室',1,0),
('20240101005','刘建国','13800001005','东区4号楼708室',0,2);

INSERT INTO `runner` (`runner_no`, `name`, `phone`, `id_card`, `status`, `rating`, `completed_orders`) VALUES
('RN20260701','张三','13900002001','3201****0101****11',0,4.8,45),
('RN20260702','李四','13900002002','3201****0102****22',0,4.5,32),
('RN20260703','王五','13900002003','3201****0103****33',0,5.0,18),
('RN20260704','赵六','13900002004','3201****0104****44',2,3.9,7),
('RN20260705','孙七','13900002005','3201****0105****55',0,4.2,21);

INSERT INTO `pricing_rule` (`name`, `condition_type`, `condition_expr`, `price_factor`, `is_active`) VALUES
('午餐高峰加价','TIME','{"slots":[2]}',1.30,1),
('晚餐高峰加价','TIME','{"slots":[4]}',1.30,1),
('远距离加价','DISTANCE','{"distance_gt":1000}',1.80,1),
('深夜加价','TIME','{"slots":[6]}',2.00,1),
('高需求加价','DEMAND','{"pending_gt":20}',1.40,1);

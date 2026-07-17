-- ============================================
-- 配送距离矩阵表
-- 用于记录不同取货点到不同送货点的实际配送耗时统计
-- 数据由 DeliveryMatrixService 定时刷新
-- ============================================
CREATE TABLE IF NOT EXISTS `delivery_distance_matrix` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `pickup_location` VARCHAR(200) NOT NULL COMMENT '取货点',
  `delivery_location` VARCHAR(200) NOT NULL COMMENT '送货点',
  `avg_minutes` DECIMAL(6,1) DEFAULT NULL COMMENT '平均配送时长（分钟）',
  `median_minutes` DECIMAL(6,1) DEFAULT NULL COMMENT '中位数配送时长（分钟）',
  `min_minutes` DECIMAL(6,1) DEFAULT NULL COMMENT '最快配送时长（分钟）',
  `max_minutes` DECIMAL(6,1) DEFAULT NULL COMMENT '最慢配送时长（分钟）',
  `order_count` INT DEFAULT 0 COMMENT '样本订单数',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route` (`pickup_location`, `delivery_location`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送距离矩阵';

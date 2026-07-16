-- Migration: Add packages and pickup_code columns to order_info
-- Run this against existing databases

ALTER TABLE `order_info`
  ADD COLUMN `packages` JSON COMMENT '包裹列表 [{size: small/medium/large}]' AFTER `tip`,
  ADD COLUMN `pickup_code` VARCHAR(100) COMMENT '取件码' AFTER `packages`;

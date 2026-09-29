-- 索引优化：按实际查询条件补齐复合索引
--
-- 原则：
--   1. 复合索引按「等值过滤列在前、范围/排序列在后」排列，让最左前缀能同时命中过滤与排序；
--   2. 单列索引若已被复合索引的最左前缀完全覆盖，则删除，避免冗余索引拖慢写入。
--
-- 覆盖的查询：
--   order_info  (student_id, created_at)   学生订单列表  WHERE student_id=? ORDER BY created_at DESC
--   order_info  (runner_id, status, ...)   跑腿员收入    WHERE runner_id=? AND status=5 ORDER BY completed_at DESC
--                                          派单负载统计  WHERE runner_id=? AND status IN (1,2,3)
--   order_info  (status, created_at)       待接单列表    WHERE status=0 ORDER BY created_at DESC
--   order_info  (status, completed_at)     今日已完成    WHERE status=5 AND completed_at >= ?

ALTER TABLE `order_info`
  DROP KEY `idx_student_id`,
  DROP KEY `idx_runner_id`,
  DROP KEY `idx_status`,
  ADD KEY `idx_student_created`  (`student_id`, `created_at`),
  ADD KEY `idx_runner_status`    (`runner_id`, `status`, `completed_at`),
  ADD KEY `idx_status_created`   (`status`, `created_at`),
  ADD KEY `idx_status_completed` (`status`, `completed_at`);

-- 派单候选筛选 WHERE status=0、在线跑腿员统计 WHERE status IN (0,1)
ALTER TABLE `runner`
  ADD KEY `idx_status` (`status`);

-- 每次派单都读一次生效中的定价规则 WHERE is_active=1
ALTER TABLE `pricing_rule`
  ADD KEY `idx_is_active` (`is_active`);

-- 管理端概览统计未处理告警 WHERE is_resolved=0
ALTER TABLE `anomaly_alert`
  ADD KEY `idx_is_resolved` (`is_resolved`);

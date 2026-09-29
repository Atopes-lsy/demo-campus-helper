CREATE TABLE IF NOT EXISTS `event_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `aggregate_type` VARCHAR(32) NOT NULL,
  `aggregate_id` BIGINT NOT NULL,
  `topic` VARCHAR(128) NOT NULL,
  `event_key` VARCHAR(128) NOT NULL,
  `payload` JSON NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_retry_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `error_message` VARCHAR(1000),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `sent_at` DATETIME,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_ready` (`status`, `next_retry_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务事件发件箱';

CREATE TABLE IF NOT EXISTS `processed_event` (
  `event_id` VARCHAR(64) NOT NULL,
  `consumer_name` VARCHAR(64) NOT NULL,
  `processed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`event_id`, `consumer_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Kafka消费者幂等记录';

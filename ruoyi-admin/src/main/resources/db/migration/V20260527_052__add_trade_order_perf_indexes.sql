-- Add read-path indexes used by H5/PC current and historical order lists.

SET @idx_currency := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_currency_order'
      AND index_name = 'idx_currency_order_user_status_time'
);
SET @sql_currency := IF(
    @idx_currency = 0,
    'CREATE INDEX idx_currency_order_user_status_time ON t_currency_order (user_id, status, create_time)',
    'SELECT 1'
);
PREPARE stmt_currency FROM @sql_currency;
EXECUTE stmt_currency;
DEALLOCATE PREPARE stmt_currency;

SET @idx_tradfi := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_tradfi_order'
      AND index_name = 'idx_tradfi_order_user_status_time'
);
SET @sql_tradfi := IF(
    @idx_tradfi = 0,
    'CREATE INDEX idx_tradfi_order_user_status_time ON t_tradfi_order (user_id, status, create_time)',
    'SELECT 1'
);
PREPARE stmt_tradfi FROM @sql_tradfi;
EXECUTE stmt_tradfi;
DEALLOCATE PREPARE stmt_tradfi;

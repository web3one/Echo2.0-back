-- 邮箱服务从 SendCloud SMTP 切换到 apihz.cn mailyzm1/mailyzm2 HTTP API。
-- EMAIL_SETTING JSON 结构由 SMTP 7 字段（mailHost/mailPort/mailUsername/mailPassword/mailFrom/mailAppName/mailTemplate）
-- 改为 apihz 3 字段（apiId/apiKey/senderName）。

UPDATE t_setting
SET setting_value = '{"apiId":"10016475","apiKey":"471b17b1c0ae0693f3054abd12b492a3","senderName":"xagent"}',
    update_time = NOW()
WHERE id = 'EMAIL_SETTING';

INSERT INTO t_setting (id, delete_flag, setting_value, create_time, update_time)
SELECT 'EMAIL_SETTING', b'0',
       '{"apiId":"10016475","apiKey":"471b17b1c0ae0693f3054abd12b492a3","senderName":"xagent"}',
       NOW(), NOW()
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM t_setting WHERE id = 'EMAIL_SETTING');

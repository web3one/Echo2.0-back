# Flyway 数据库迁移脚本

## 命名规范

`V<版本号>__<描述>.sql`

- 版本号用日期+序号：`V20260427_001`、`V20260427_002`
- 描述用下划线分隔的英文：`init_chain_config`
- 完整示例：`V20260427_001__init_chain_config.sql`

## 规则

1. **每个脚本只会执行一次**：Flyway 会把执行成功的脚本登记到 `flyway_schema_history` 表，下次启动跳过。
2. **不要修改已经跑过的脚本**：Flyway 会校验 checksum，改了就启动失败。需要修正请新增 V 脚本回滚。
3. **首次部署**：Flyway 会先把现有库里的 echo2.sql 标记成 baseline（version=1），然后从 V2 开始执行新脚本。
4. **版本号单调递增**：日期前缀保证顺序，同一天多个用 `_001/_002` 区分。

## 操作

- **看待执行的脚本**：`SELECT * FROM flyway_schema_history ORDER BY installed_rank;`
- **重置某个脚本**：`DELETE FROM flyway_schema_history WHERE version = '20260427_001';`（仅开发环境）
- **强制清空**：`DROP TABLE flyway_schema_history;` 然后重启（仅开发环境）

## 当前脚本

（暂无，Phase 1 起开始建表）

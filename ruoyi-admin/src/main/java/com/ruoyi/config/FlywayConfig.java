package com.ruoyi.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * Flyway 配置：显式绑定到 masterDataSource，绕开 DynamicDataSource 路由问题。
 * 启动时自动执行 db/migration 下未跑过的 V*.sql 脚本。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = false)
public class FlywayConfig {

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String locations;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.baseline-version:1}")
    private String baselineVersion;

    @Value("${spring.flyway.baseline-description:Initial schema baseline}")
    private String baselineDescription;

    @Value("${spring.flyway.validate-on-migrate:true}")
    private boolean validateOnMigrate;

    @Value("${spring.flyway.encoding:UTF-8}")
    private String encoding;

    @Value("${spring.flyway.table:flyway_schema_history}")
    private String table;

    @Value("${spring.flyway.out-of-order:false}")
    private boolean outOfOrder;

    @Value("${spring.flyway.ignore-migration-patterns:*:missing}")
    private String ignoreMigrationPatterns;

    @Bean(initMethod = "migrate")
    public Flyway flyway(@Qualifier("masterDataSource") DataSource masterDataSource) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(masterDataSource)
                .locations(locations.split(","))
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .baselineDescription(baselineDescription)
                .validateOnMigrate(validateOnMigrate)
                .encoding(encoding)
                .table(table)
                .outOfOrder(outOfOrder);

        if (ignoreMigrationPatterns != null && !ignoreMigrationPatterns.trim().isEmpty()) {
            String[] patterns = Arrays.stream(ignoreMigrationPatterns.split(","))
                    .map(String::trim)
                    .filter(pattern -> !pattern.isEmpty())
                    .toArray(String[]::new);
            if (patterns.length > 0) {
                configuration.ignoreMigrationPatterns(patterns);
            }
        }

        Flyway flyway = configuration.load();

        log.info("Flyway initialized: locations={}, baselineVersion={}, ignoreMigrationPatterns={}",
                locations, baselineVersion, ignoreMigrationPatterns);
        MigrationInfo[] pending = flyway.info().pending();
        if (pending.length > 0) {
            log.info("Flyway pending migrations: {}", pending.length);
            for (MigrationInfo info : pending) {
                log.info("  - V{} {}", info.getVersion(), info.getDescription());
            }
        }
        return flyway;
    }
}

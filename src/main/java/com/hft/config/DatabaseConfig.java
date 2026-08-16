package com.hft.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Two datasources: the app's primary JPA store (H2 in dev/test, MySQL via the
 * "docker"/prod profiles — spring.datasource.*) and an optional secondary
 * ClickHouse store for analytics (hft.clickhouse.*, plain JDBC, no JPA/Hibernate —
 * ClickHouse's MergeTree engine doesn't support the row-level UPDATE/DELETE
 * transactions Hibernate assumes).
 *
 * primaryDataSource() re-declares Boot's auto-configured DataSource explicitly
 * and marks it @Primary — required the moment a second DataSource bean exists,
 * otherwise every unqualified DataSource/JdbcTemplate injection point (including
 * inside Spring Data JPA's own autoconfiguration) becomes ambiguous. Binds
 * spring.datasource.* onto DataSourceProperties first and builds from that
 * (not straight onto DataSourceBuilder.build()) — DataSourceProperties is what
 * knows to translate the vendor-neutral "url" into Hikari's "jdbcUrl"; binding
 * @ConfigurationProperties directly onto an already-built HikariDataSource skips
 * that translation and leaves jdbcUrl unset. Same spring.datasource.* properties
 * Boot would have used implicitly, so H2 (dev/test) and MySQL (docker/prod)
 * behavior is unchanged.
 */
@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource primaryDataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConditionalOnProperty(name = "hft.clickhouse.enabled", havingValue = "true")
    public DataSource clickHouseDataSource(
            @Value("${hft.clickhouse.jdbc-url}") String jdbcUrl,
            @Value("${hft.clickhouse.username}") String username,
            @Value("${hft.clickhouse.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        ds.setPoolName("clickhouse-pool");
        ds.setMaximumPoolSize(5);
        return ds;
    }

    @Bean
    @ConditionalOnProperty(name = "hft.clickhouse.enabled", havingValue = "true")
    public JdbcTemplate clickHouseJdbcTemplate(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}

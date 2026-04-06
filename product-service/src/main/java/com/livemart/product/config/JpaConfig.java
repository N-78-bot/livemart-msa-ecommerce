package com.livemart.product.config;

import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * JPA & Hibernate 성능 최적화 설정
 *
 * 최적화 전략:
 * 1. 2차 캐시 활성화 (Ehcache)
 * 2. Batch Insert/Update
 * 3. JDBC Fetch Size 최적화
 * 4. Query Plan Cache
 * 5. Statistics 수집
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing
public class JpaConfig {

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.livemart.product.domain");
        em.setPersistenceProviderClass(HibernatePersistenceProvider.class);

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(false);
        vendorAdapter.setGenerateDdl(false);
        em.setJpaVendorAdapter(vendorAdapter);

        em.setJpaProperties(hibernateProperties());

        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory(dataSource).getObject());
        return transactionManager;
    }

    /**
     * Hibernate 성능 최적화 속성
     */
    private Properties hibernateProperties() {
        Properties properties = new Properties();

        // 기본 설정
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.use_sql_comments", "true");

        // ===== 성능 최적화 =====

        // 1. Batch Processing (대량 처리 최적화)
        properties.setProperty("hibernate.jdbc.batch_size", "50");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.batch_versioned_data", "true");

        // 2. JDBC Fetch Size (한 번에 가져올 Row 수)
        properties.setProperty("hibernate.jdbc.fetch_size", "100");

        // 3. 2차 캐시 비활성화 (JCache 라이브러리 없음)
        properties.setProperty("hibernate.cache.use_second_level_cache", "false");
        properties.setProperty("hibernate.cache.use_query_cache", "false");

        // 4. Query Plan Cache (쿼리 계획 캐싱)
        properties.setProperty("hibernate.query.plan_cache_max_size", "2048");
        properties.setProperty("hibernate.query.plan_parameter_metadata_max_size", "128");

        // 5. Connection Pool 관리
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true");

        // 6. Statement Caching
        properties.setProperty("hibernate.query.in_clause_parameter_padding", "true");

        // 7. Lazy Loading 최적화
        properties.setProperty("hibernate.enable_lazy_load_no_trans", "false");
        properties.setProperty("hibernate.bytecode.use_reflection_optimizer", "true");

        // 8. Statistics (프로덕션에서는 false)
        properties.setProperty("hibernate.generate_statistics", "true");
        properties.setProperty("hibernate.session.events.log", "false");

        // 9. ID 생성 전략 최적화
        properties.setProperty("hibernate.id.new_generator_mappings", "true");
        properties.setProperty("hibernate.id.optimizer.pooled.prefer_lo", "true");

        // 10. JDBC Time Zone
        properties.setProperty("hibernate.jdbc.time_zone", "Asia/Seoul");

        // 11. Logging
        properties.setProperty("hibernate.connection.autocommit", "false");

        // 12. Naming Strategy (snake_case)
        properties.setProperty("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");

        return properties;
    }
}

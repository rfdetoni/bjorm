package com.github.rfdetoni.bjorm.spring;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.BjormOptions;
import com.github.rfdetoni.bjorm.EntityMapper;
import com.github.rfdetoni.bjorm.SqlDialects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;

/** Optional Spring integration. JDBC context borrows Spring-bound connections through proxy. */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnBean(DataSource.class)
public class BjormAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(Bjorm.class)
    public Bjorm bjorm(DataSource source,ObjectProvider<EntityMapper<?>> mappers,
                       @Value("${bjorm.jdbc.query-timeout-seconds:30}") int queryTimeoutSeconds,
                       @Value("${bjorm.jdbc.max-buffered-rows:100000}") int maxBufferedRows,
                       @Value("${bjorm.jdbc.fetch-size:128}") int fetchSize,
                       @Value("${bjorm.dialect:postgresql}") String dialectName) {
        EntityMapper<?>[] mapped=mappers.orderedStream().toArray(EntityMapper<?>[]::new);
        TransactionAwareDataSourceProxy transactionalSource=new TransactionAwareDataSourceProxy(source);
        BjormOptions options=new BjormOptions(queryTimeoutSeconds,maxBufferedRows,fetchSize);
        return Bjorm.open(transactionalSource,options,SqlDialects.named(dialectName),mapped);
    }
    @Bean
    @ConditionalOnMissingBean(BjormPages.class)
    public BjormPages bjormPages(Bjorm bjorm, @Value("${bjorm.pagination.max-offset:10000}") long maxOffset) {
        return new BjormPages(bjorm,maxOffset);
    }
}

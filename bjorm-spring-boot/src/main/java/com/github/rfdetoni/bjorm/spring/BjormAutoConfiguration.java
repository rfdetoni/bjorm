package com.github.rfdetoni.bjorm.spring;

import com.github.rfdetoni.bjorm.Bjorm;
import com.github.rfdetoni.bjorm.EntityMapper;
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
    public Bjorm bjorm(DataSource source,ObjectProvider<EntityMapper<?>> mappers) {
        EntityMapper<?>[] mapped=mappers.orderedStream().toArray(EntityMapper<?>[]::new);
        TransactionAwareDataSourceProxy transactionalSource=new TransactionAwareDataSourceProxy(source);
        return mapped.length==0 ? Bjorm.open(transactionalSource) : Bjorm.open(transactionalSource,mapped);
    }
    @Bean
    @ConditionalOnMissingBean(BjormPages.class)
    public BjormPages bjormPages(Bjorm bjorm, @Value("${bjorm.pagination.max-offset:10000}") long maxOffset) {
        return new BjormPages(bjorm,maxOffset);
    }
}

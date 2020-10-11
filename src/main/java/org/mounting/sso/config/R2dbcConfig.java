package org.mounting.sso.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mounting.sso.config.properties.DBProperties;
import org.mounting.sso.config.properties.MasterDBProperties;
import org.mounting.sso.config.properties.SlaveDBProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.connectionfactory.R2dbcTransactionManager;
import org.springframework.data.r2dbc.connectionfactory.lookup.AbstractRoutingConnectionFactory;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * @author hubert.squid
 * @since 2020.10.09
 */
@Slf4j
@EnableTransactionManagement
@EnableConfigurationProperties({ MasterDBProperties.class, SlaveDBProperties.class })
@EnableR2dbcRepositories(basePackages = "org.mounting.sso.domain.repository")
@RequiredArgsConstructor
@Configuration
public class R2dbcConfig extends AbstractR2dbcConfiguration {

    private final MasterDBProperties masterDBProperties;
    private final SlaveDBProperties slaveDBProperties;

    @Bean
    @Override
    public ConnectionFactory connectionFactory() {
        Map<DatabaseType, ConnectionFactory> factories = new HashMap<>();
        factories.put(DatabaseType.MASTER, createConnectionFactory(DatabaseType.MASTER));
        factories.put(DatabaseType.SLAVE, createConnectionFactory(DatabaseType.SLAVE));

        RoutingConnectionFactory routingConnectionFactory = new RoutingConnectionFactory();
        routingConnectionFactory.setTargetConnectionFactories(factories);
        routingConnectionFactory.setDefaultTargetConnectionFactory(factories.get(DatabaseType.MASTER));
        routingConnectionFactory.afterPropertiesSet();

        return routingConnectionFactory;
    }

    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    private ConnectionFactory createConnectionFactory(DatabaseType type) {
        DBProperties dbProperties = selectProperties(type);
        ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
            .option(DRIVER, "pool")
            .option(PROTOCOL, "mysql") // driver identifier, PROTOCOL is delegated as DRIVER by the pool.
            .option(HOST, dbProperties.getHost())
            .option(PORT, dbProperties.getPort())
            .option(USER, dbProperties.getUsername())
            .option(PASSWORD, dbProperties.getPassword())
            .option(DATABASE, dbProperties.getDatabase())
            .option(CONNECT_TIMEOUT, Duration.ofMillis(dbProperties.getConnectTimeout()))
            .option(SSL, false)
            .option(Option.valueOf("useServerPrepareStatement"), true)
            .option(Option.valueOf("tcpKeepAlive"), false)
            .option(Option.valueOf("tcpNoDelay"), false)
            .build());

        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder(connectionFactory)
            .maxCreateConnectionTime(Duration.ofMillis(dbProperties.getConnectTimeout()))
            .maxAcquireTime(Duration.ofMillis(dbProperties.getConnectTimeout()))
            .maxSize(dbProperties.getMaxIdle())
            .initialSize(dbProperties.getMinIdle())
            .validationQuery("SELECT 1")
            .build();

        return new ConnectionPool(poolConfiguration);
    }

    private DBProperties selectProperties(DatabaseType type) throws IllegalArgumentException {
        switch (type) {
            case MASTER:
                return masterDBProperties;
            case SLAVE:
                return slaveDBProperties;
            default:
                throw new IllegalArgumentException("Not support database type");
        }
    }

    static class RoutingConnectionFactory extends AbstractRoutingConnectionFactory {

        /**
         * AbstractRoutingDataSource와 다르게 현재 transaction의 상태로 connection(datasource)을 선택할 수 없음.
         * 트랜잭션의 정보가 아닌 다른 정보를 이용하여 connection을 선택하도록 해야함.
         * Reason. @Transactional의 attr를 TransactionContext에 반영하기 전에 해당 메서드를 호출함.
         */
        @Override
        protected Mono<Object> determineCurrentLookupKey() {
//            return TransactionSynchronizationManager.forCurrentTransaction()
//                .handle((tx, sink) -> sink.next(DatabaseType.determine(tx.isCurrentTransactionReadOnly())))
//                .onErrorResume(throwable -> {
//                    log.debug("determine master db connection!!!");
//                    return Mono.just(DatabaseType.MASTER);
//                });
            return Mono.just(DatabaseType.MASTER);
        }
    }

    enum DatabaseType {
        MASTER, SLAVE;

        static DatabaseType determine(boolean isReadOnly) {
            if (isReadOnly) {
                log.debug("determine slave db connection!!!");
                return SLAVE;
            }

            log.debug("determine master db connection!!!");
            return MASTER;
        }
    }
}

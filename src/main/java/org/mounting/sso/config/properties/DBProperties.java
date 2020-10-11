package org.mounting.sso.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * @author hubert.squid
 * @since 2020.10.09
 */
@Getter
@Setter
public abstract class DBProperties {

    protected String username;
    protected String password;
    protected String host;
    protected String database;
    protected int port;
    protected int maxIdle;
    protected int minIdle;
    protected int connectTimeout;
}

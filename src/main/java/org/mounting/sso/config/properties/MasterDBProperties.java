package org.mounting.sso.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author hubert.squid
 * @since 2020.10.10
 */
@ConfigurationProperties(prefix = "db.master")
public class MasterDBProperties extends DBProperties {

}

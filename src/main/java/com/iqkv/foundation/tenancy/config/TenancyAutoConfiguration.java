/*
 * Copyright 2026 IQKV Foundation Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iqkv.foundation.tenancy.config;

import java.util.Collections;
import javax.sql.DataSource;

import com.iqkv.foundation.tenancy.LiquibaseConfigurationProperties;
import com.iqkv.foundation.tenancy.TenantKeyProvider;
import com.iqkv.foundation.tenancy.TenantLiquibaseRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * Auto-configuration for tenancy components.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@EnableConfigurationProperties(LiquibaseConfigurationProperties.class)
public class TenancyAutoConfiguration {

  /**
   * No-op {@link TenantKeyProvider} registered when no service-specific implementation is present.
   * Returns an empty list, which causes {@link TenantLiquibaseRunner} to skip the upgrade scan
   * while still running system and demo-tenant migrations.
   */
  @Bean
  @ConditionalOnMissingBean(TenantKeyProvider.class)
  public TenantKeyProvider noOpTenantKeyProvider() {
    return Collections::emptyList;
  }

  /**
   * Registers the {@link TenantLiquibaseRunner} when:
   * <ul>
   *   <li>Liquibase is on the classpath</li>
   *   <li>A {@link DataSource} bean is available</li>
   *   <li>{@code iqkv.liquibase.tenant-runner-enabled=true}</li>
   *   <li>No other {@link TenantLiquibaseRunner} bean is already defined</li>
   * </ul>
   * The runner receives the {@link TenantKeyProvider} bean (either the service-specific
   * implementation or the no-op fallback above).
   */
  @Bean
  @Order(1)
  @ConditionalOnClass(name = "liquibase.Liquibase")
  @ConditionalOnProperty(
      name = "iqkv.liquibase.tenant-runner-enabled",
      havingValue = "true",
      matchIfMissing = false)
  @ConditionalOnMissingBean(TenantLiquibaseRunner.class)
  public TenantLiquibaseRunner tenantLiquibaseRunner(
      final DataSource dataSource,
      final LiquibaseConfigurationProperties liquibaseProps,
      final TenantKeyProvider tenantKeyProvider) {
    return new TenantLiquibaseRunner(dataSource, liquibaseProps, tenantKeyProvider);
  }
}

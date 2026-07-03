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

package com.iqkv.foundation.tenancy;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Application runner that executes tenant-aware Liquibase migrations on startup. */
@Component
@ConditionalOnProperty(
    name = "iqkv.liquibase.tenant-runner-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TenantLiquibaseRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(TenantLiquibaseRunner.class);

  private final DataSource dataSource;
  private final LiquibaseConfigurationProperties liquibaseProps;

  public TenantLiquibaseRunner(
      final DataSource dataSource, final LiquibaseConfigurationProperties liquibaseProps) {
    this.dataSource = dataSource;
    this.liquibaseProps = liquibaseProps;
  }

  @Override
  public void run(final ApplicationArguments args) throws Exception {
    log.info("Running system schema migrations");
    runMigrations("public", liquibaseProps.systemChangeLog());
    log.info("System schema migrations complete");

    if (liquibaseProps.demoTenants() != null && !liquibaseProps.demoTenants().isEmpty()) {
      log.info("Running tenant schema migrations for demo tenants: {}", liquibaseProps.demoTenants());
      for (final String tenantKey : liquibaseProps.demoTenants()) {
        runMigrationsForTenant(tenantKey);
      }
      log.info("Demo tenant schema migrations complete");
    }
  }

  public void runMigrationsForTenant(final String tenantKey) throws Exception {
    final String schema = "t_" + tenantKey;
    log.info("Running tenant schema migrations for schema: {}", schema);
    runMigrations(schema, liquibaseProps.tenantChangeLog());
    log.info("Tenant schema migrations complete for schema: {}", schema);
  }

  private void runMigrations(final String schema, final String changelogPath) throws Exception {
    try (final Connection connection = dataSource.getConnection()) {
      try (final Statement stmt = connection.createStatement()) {
        stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        stmt.execute("SET search_path TO " + schema);
      }

      final Database database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      database.setDefaultSchemaName(schema);
      database.setLiquibaseSchemaName(schema);

      final Contexts contexts =
          StringUtils.hasText(liquibaseProps.contexts())
              ? new Contexts(liquibaseProps.contexts())
              : new Contexts();

      try (final Liquibase liquibase =
          new Liquibase(
              changelogPath, new ClassLoaderResourceAccessor(), database)) {
        liquibase.update(contexts, new LabelExpression());
      }
    }
  }
}

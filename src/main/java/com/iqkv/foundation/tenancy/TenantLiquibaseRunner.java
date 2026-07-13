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
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.util.StringUtils;

/**
 * Application runner that executes tenant-aware Liquibase migrations on startup.
 *
 * <p>Startup sequence:
 * <ol>
 *   <li>Migrate the {@code public} (system) schema.</li>
 *   <li>If {@link LiquibaseConfigurationProperties#upgradeExistingTenants()} is {@code true},
 *       iterate all tenant keys returned by the registered {@link TenantKeyProvider} and apply
 *       any pending changesets to each tenant schema. Per-tenant failures are logged and skipped
 *       so that one bad schema does not abort the entire startup.</li>
 *   <li>Migrate any additional seed tenants listed in
 *       {@link LiquibaseConfigurationProperties#bootstrapTenants()}. This step is idempotent — bootstrap
 *       tenants already covered by the provider scan are silently no-ops.</li>
 * </ol>
 */
public class TenantLiquibaseRunner implements ApplicationRunner {

  private static final String PLATFORM_TENANT_KEY = "platform";

  private static final Logger log = LoggerFactory.getLogger(TenantLiquibaseRunner.class);

  private final DataSource dataSource;
  private final LiquibaseConfigurationProperties liquibaseProps;
  private final TenantKeyProvider tenantKeyProvider;

  public TenantLiquibaseRunner(
      final DataSource dataSource,
      final LiquibaseConfigurationProperties liquibaseProps,
      final TenantKeyProvider tenantKeyProvider) {
    this.dataSource = dataSource;
    this.liquibaseProps = liquibaseProps;
    this.tenantKeyProvider = tenantKeyProvider;
  }

  @Override
  public void run(final ApplicationArguments args) throws Exception {
    // Step 1 — system schema
    log.info("Running system schema migrations");
    runMigrations("public", liquibaseProps.systemChangeLog());
    log.info("System schema migrations complete");

    // Step 2 — upgrade existing tenant schemas
    if (liquibaseProps.upgradeExistingTenants()) {
      final List<String> tenantKeys = tenantKeyProvider.findAllTenantKeys();
      if (!tenantKeys.isEmpty()) {
        log.info("Upgrading existing tenant schemas: {} tenant(s) found", tenantKeys.size());
        final List<String> failed = new ArrayList<>();
        for (final String tenantKey : tenantKeys) {
          try {
            runMigrationsForTenant(tenantKey);
          } catch (final Exception e) {
            log.error("Failed to upgrade schema for tenant '{}', skipping", tenantKey, e);
            failed.add(tenantKey);
          }
        }
        if (!failed.isEmpty()) {
          log.warn("Schema upgrade failed for {}/{} tenant(s): {}",
              failed.size(), tenantKeys.size(), failed);
        } else {
          log.info("All existing tenant schema upgrades complete");
        }
      } else {
        log.debug("No existing tenant keys returned by TenantKeyProvider — skipping upgrade scan");
      }
    } else {
      log.info("Existing tenant schema upgrade scan disabled (iqkv.liquibase.upgrade-existing-tenants=false)");
    }

    // Step 3 — seed, bootstrap tenants (idempotent)
    if (liquibaseProps.bootstrapTenants() != null && !liquibaseProps.bootstrapTenants().isEmpty()) {
      log.info("Running tenant schema migrations for pre-provisioned tenants: {}", liquibaseProps.bootstrapTenants());
      for (final String tenantKey : liquibaseProps.bootstrapTenants()) {
        runMigrationsForTenant(tenantKey);
      }
      log.info("Bootstrap (pre-provisioned) tenant schema migrations complete");
    }

    // Step 4 - The platform tenant (hardcoded key "platform") is used as a "landing zone" for every new user
    // before they create an org. This design is fine but we should make sure the platform tenant's schema is seeded at bootstrap.
    runMigrationsForTenant(PLATFORM_TENANT_KEY);
  }

  /**
   * Creates the tenant schema if absent and applies all pending Liquibase changesets.
   * Safe to call repeatedly — Liquibase tracks applied changesets in each schema's
   * {@code DATABASECHANGELOG} table and skips already-applied entries.
   *
   * @param tenantKey the tenant key (schema will be {@code t_{tenantKey}})
   * @throws Exception if Liquibase fails to apply a changeset
   */
  public void runMigrationsForTenant(final String tenantKey) throws Exception {
    final String schema = "t_" + tenantKey;
    log.info("Running tenant schema migrations for schema: {}", schema);
    runMigrations(schema, liquibaseProps.tenantChangeLog());
    log.info("Tenant schema migrations complete for schema: {}", schema);
  }

  private void runMigrations(final String schema, final String changelogPath) throws Exception {
    // Use a dedicated connection for schema creation only. Liquibase gets its own
    // separate connection (via the second getConnection() call) which it is free to
    // close when the Liquibase try-with-resources block exits. This avoids the
    // "Connection is closed" error that occurs when the finally-reset block tries
    // to reuse a connection that Liquibase has already closed.
    try (final Connection schemaConnection = dataSource.getConnection();
         final Statement stmt = schemaConnection.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
    }

    try (final Connection liquibaseConnection = dataSource.getConnection()) {
      // Set search_path so Liquibase resolves unqualified table references to the
      // correct schema. Liquibase will close this connection on exit; we do not
      // touch it afterwards.
      try (final Statement stmt = liquibaseConnection.createStatement()) {
        stmt.execute("SET search_path TO " + schema);
      }

      final Database database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(liquibaseConnection));
      database.setDefaultSchemaName(schema);
      database.setLiquibaseSchemaName(schema);

      final Contexts contexts =
          StringUtils.hasText(liquibaseProps.contexts())
              ? new Contexts(liquibaseProps.contexts())
              : new Contexts();

      try (final Liquibase liquibase =
               new Liquibase(changelogPath, new ClassLoaderResourceAccessor(), database)) {
        liquibase.update(contexts, new LabelExpression());
      }
      // Liquibase.close() closes the Database which closes liquibaseConnection.
      // HikariCP evicts closed connections from the pool automatically, so no
      // explicit search_path reset is needed — the connection is gone, not recycled.
    }
  }
}

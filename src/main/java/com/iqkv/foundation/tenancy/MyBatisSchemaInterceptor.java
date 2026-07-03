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

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis interceptor that sets the PostgreSQL search_path to the tenant schema before each
 * statement when a tenant context is active.
 *
 * <p>When no tenant context is set, the interceptor proceeds without modifying the search_path,
 * leaving the default public schema in effect.
 */
@Intercepts({
  @Signature(
      type = StatementHandler.class,
      method = "prepare",
      args = {Connection.class, Integer.class})
})
public class MyBatisSchemaInterceptor implements Interceptor {

  private static final Logger log = LoggerFactory.getLogger(MyBatisSchemaInterceptor.class);
  private static final java.util.regex.Pattern SAFE_TENANT_KEY =
      java.util.regex.Pattern.compile("^[a-zA-Z0-9_]+$");

  @Override
  public Object intercept(final Invocation invocation) throws Throwable {
    final Connection connection = (Connection) invocation.getArgs()[0];
    try {
      final String tenantKey = TenantContext.getCurrentTenant();
      if (!SAFE_TENANT_KEY.matcher(tenantKey).matches()) {
        throw new IllegalArgumentException("Invalid tenant key: " + tenantKey);
      }
      final String schema = "t_" + tenantKey;
      try (final var stmt =
          connection.prepareStatement("SET search_path TO " + schema + ", public")) {
        stmt.execute();
      }
      log.trace("search_path set to {}, public", schema);
    } catch (final IllegalStateException e) {
      // No tenant context — stay on public schema (system-level operations)
      log.trace("No tenant context active, using default public schema");
    }
    return invocation.proceed();
  }
}

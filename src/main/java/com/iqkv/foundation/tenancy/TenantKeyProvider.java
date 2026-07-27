/*
 * Copyright 2026 iQKV Foundation Team.
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

import java.util.List;

/**
 * SPI for resolving all tenant keys that require Liquibase schema migrations on startup.
 *
 * <p>Implementing services register a bean of this type to instruct
 * {@link TenantLiquibaseRunner} which tenant schemas to migrate when the application starts.
 * This covers the <em>ongoing upgrade</em> case: when a new changeset is deployed, every
 * existing tenant schema is migrated automatically on the next startup.
 *
 * <p>Each service should implement this interface according to its own data model:
 * <ul>
 *   <li><b>IAM service</b> — query {@code public.tenants} for active/provisioning tenants.</li>
 *   <li><b>Other services</b> — query {@code information_schema.schemata} for schemas
 *       matching the {@code t_} prefix that were already provisioned in that service's DB.</li>
 * </ul>
 *
 * <p>If no bean is registered, {@link TenancyAutoConfiguration} provides a no-op implementation
 * that returns an empty list, preserving backwards-compatible behaviour.
 *
 * @see TenantLiquibaseRunner
 */
public interface TenantKeyProvider {

  /**
   * Returns the list of tenant keys whose schemas should be migrated on startup.
   * The list may be empty but must not be {@code null}.
   *
   * @return tenant keys (without the {@code t_} schema prefix)
   */
  List<String> findAllTenantKeys();
}

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

/**
 * ThreadLocal holder for the current tenant key.
 * Must be cleared at the end of each request via {@link #clear()}.
 */
public final class TenantContext {

  private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  private TenantContext() {
  }

  /**
   * Sets the current tenant key for this thread.
   *
   * @param tenantKey the tenant key to set
   * @throws IllegalArgumentException if tenantKey is null or blank
   */
  public static void setCurrentTenant(final String tenantKey) {
    if (tenantKey == null || tenantKey.isBlank()) {
      throw new IllegalArgumentException("Tenant ID cannot be null or blank");
    }
    CURRENT_TENANT.set(tenantKey);
  }

  /**
   * Returns the current tenant key for this thread.
   *
   * @return the current tenant key
   * @throws IllegalStateException if no tenant context has been set
   */
  public static String getCurrentTenant() {
    final String tenantKey = CURRENT_TENANT.get();
    if (tenantKey == null) {
      throw new IllegalStateException("No tenant context set for current thread");
    }
    return tenantKey;
  }

  /**
   * Clears the current tenant context. Must be called in a finally block after each request.
   */
  public static void clear() {
    CURRENT_TENANT.remove();
  }
}

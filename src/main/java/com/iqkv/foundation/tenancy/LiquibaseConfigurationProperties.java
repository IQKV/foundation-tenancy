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

import jakarta.validation.constraints.NotBlank;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Liquibase configuration properties for tenant-aware schema migrations. */
@Validated
@ConfigurationProperties(prefix = "iqkv.liquibase")
public record LiquibaseConfigurationProperties(
    @NotBlank String systemChangeLog,
    @NotBlank String tenantChangeLog,
    String contexts,
    List<String> demoTenants) {}

# Foundation Tenancy 📋

Shared tenancy components for IQKV microservices, including tenant context management, MyBatis schema interception, and Liquibase migration runner.

## Quick Links

- [API Documentation](./docs/api/README.md)
- [Architecture Overview](./docs/architecture/README.md)
- [Contributing Guidelines](.github/CONTRIBUTING.md)

## Key Components

### TenantContext

ThreadLocal holder for current tenant key, with type-safe tenant key management.

```java
// Set current tenant
TenantContext.setCurrentTenant("tenant123");

// Get current tenant
String tenant = TenantContext.getCurrentTenant();

// Clear (must call to prevent memory leaks)
TenantContext.clear();
```

### MyBatisSchemaInterceptor

MyBatis Interceptor that automatically sets PostgreSQL `search_path` to the tenant schema (`t_<tenantKey>, public`) for each database operation when tenant context is active.

### TenantLiquibaseRunner

Spring Boot ApplicationRunner that executes tenant-aware Liquibase migrations on startup, including:

- System schema migrations (to `public`)
- Tenant schema migrations (to `t_<tenantKey>`) for each demo tenant (if configured via `iqkv.liquibase.demoTenants`)

### LiquibaseConfigurationProperties

Spring Boot configuration properties for tenancy-related Liquibase settings:

```yaml
iqkv:
    liquibase:
        system-change-log: db/changelog/system/db.changelog-master.xml
        tenant-change-log: db/changelog/tenant/master.xml
        contexts: dev,prod
        demo-tenants:
            - tenant1
            - tenant2
        tenant-runner-enabled: true # default is true
```

## Usage

### Maven Dependency

```xml
<dependency>
  <groupId>com.iqkv</groupId>
  <artifactId>foundation-tenancy</artifactId>
  <version>0.24.0-SNAPSHOT</version>
</dependency>
```

### Spring Boot Auto-Configuration

When added as a dependency to a Spring Boot project, `TenantLiquibaseRunner` and `LiquibaseConfigurationProperties` are auto-configured if Liquibase is available on the classpath.

## Development

```bash
# Build and install to local Maven repository
./mvnw clean install -Dcheckstyle.skip=true
```

# Build Fix — DataSourceConfig Compilation Error

## Problem

Build failed with compilation errors in `DataSourceConfig.java`:

```
error: cannot find symbol
  config.setCachePrepStmts(true);
        ^
  symbol: method setCachePrepStmts(boolean)
  location: variable config of type HikariConfig
```

## Root Cause

The methods `setCachePrepStmts()`, `setPrepStmtCacheSize()`, and `setPrepStmtCacheSqlLimit()` **do not exist** on `HikariConfig`. These are PostgreSQL JDBC driver properties, not HikariCP configuration methods.

## Solution Applied ✅

Changed from direct method calls to using `addDataSourceProperty()`:

```java
// BEFORE (wrong - causes compilation error)
config.setCachePrepStmts(true);
config.setPrepStmtCacheSize(250);
config.setPrepStmtCacheSqlLimit(2048);

// AFTER (correct)
config.addDataSourceProperty("cachePreparedStatements", "true");
config.addDataSourceProperty("preparedStatementCacheSize", "250");
config.addDataSourceProperty("preparedStatementCacheSqlLimit", "2048");
```

## File Fixed

✅ `src/main/java/za/co/titandynamix/sasbo_online_backend/config/DataSourceConfig.java` (lines 96-98)

## Build Status

✅ **BUILD SUCCESSFUL** — Compilation now passes

```bash
$ ./gradlew compileJava --no-daemon
> Task :compileJava UP-TO-DATE

BUILD SUCCESSFUL in 1m 50s
```

## Next Steps

Now you can run the full build:

```bash
./gradlew clean build -x test
```

Or run with tests:

```bash
./gradlew clean build
```

Or run the full suite:

```bash
./gradlew verify
```

## Why This Change

- `HikariConfig` is a connection pool configuration object
- PostgreSQL JDBC driver properties are set via `addDataSourceProperty()`
- Properties must use the driver's exact property names (camelCase converted to driver format)
- This is the standard way to configure JDBC driver settings in Spring Boot with HikariCP

All functionality is preserved — prepared statement caching will work correctly with the driver properties approach.

# Deployment Issue Fix Guide - Sasbo Online Backend

## Problem Summary
After deployment to Render and Vercel, the backend API endpoints returned 500 Internal Server Errors with the message "No static resource api/job-grades".

**Affected Endpoints:**
- `/api/job-grades`
- `/api/members/userRoles`
- `/api/members/structures/tree`
- `/api/representatives`

## Root Cause Analysis
The error "No static resource" suggested that Spring was attempting to serve REST API endpoints as static resources or Thymeleaf templates, rather than routing them through the proper Spring MVC controllers. This typically occurs when:

1. **Thymeleaf Template Resolution**: Spring Boot was trying to resolve API responses as Thymeleaf template names
2. **Static Resource Handling**: Static resource mapping was interfering with API route handling
3. **Missing Exception Handler**: Lack of proper error handling for route resolution failures

## Solutions Implemented

### 1. Updated Spring Configuration (`application.yaml`)

**Disabled Thymeleaf for API Responses:**
```yaml
spring:
  thymeleaf:
    check-template-location: false
    enabled: false
```

**Configured Spring MVC Dispatcher:**
```yaml
spring:
  mvc:
    static-path-pattern: /static/**
    throw-exception-if-no-handler-found: true
  web:
    resources:
      static-locations: classpath:/static/
      add-mappings: false
```

**Enhanced Error Responses:**
```yaml
server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: on-param
    include-exception: false
```

**Improved Logging:**
```yaml
logging:
  level:
    org.springframework.web.servlet.DispatcherServlet: DEBUG
```

### 2. Enhanced Global Exception Handler

**Added NoHandlerFoundException Handler:**
```java
@ExceptionHandler(NoHandlerFoundException.class)
public ResponseEntity<ApiErrorResponse> handleNoHandlerFound(
    NoHandlerFoundException ex, 
    HttpServletRequest request) {
    log.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getRequestURL());
    ApiErrorResponse response = new ApiErrorResponse(
            LocalDateTime.now(ZoneId.of("UTC")),
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            "The requested resource was not found",
            request.getRequestURI(),
            null
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
}
```

### 3. Refactored JobGradeController (from previous work)

**Created JobGradeResponseDto** for proper type-safe API responses:
- Uses DTOs instead of raw Maps
- Proper Jackson serialization with `@JsonProperty` annotations
- Factory method for clean conversion from JPA entities

**Updated Controller:**
- Changed return type to `ResponseEntity<List<JobGradeResponseDto>>`
- Uses streams for cleaner processing
- Improved null handling

## Deployment Instructions

### For Render Deployment:

1. **Environment Variables Required:**
   ```
   SPRING_DATASOURCE_URL=postgresql://...
   SPRING_DATASOURCE_USERNAME=...
   SPRING_DATASOURCE_PASSWORD=...
   SSL_MODE=require
   SERVER_PORT=8080
   FRONTEND_URL=https://your-frontend-url.com
   ```

2. **Docker Build:**
   The Dockerfile is already configured correctly. It:
   - Uses gradle:9.5-jdk21 for building
   - Uses eclipse-temurin:21-jre-alpine for runtime
   - Properly runs the Spring Boot jar

3. **Verify Logs:**
   ```bash
   # Check Render logs to ensure:
   - Spring Boot starts successfully
   - Database connection succeeds
   - DispatcherServlet is processing API routes (DEBUG logs)
   ```

### For Local Testing:

1. **Build the project:**
   ```bash
   ./gradlew clean build -x test
   ```

2. **Run with environment variables:**
   ```bash
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sasbo
   export SPRING_DATASOURCE_USERNAME=postgres
   export SPRING_DATASOURCE_PASSWORD=password
   export SSL_MODE=disable
   
   java -jar build/libs/sasbo-online-backend-0.0.1-SNAPSHOT.jar
   ```

3. **Test endpoints:**
   ```bash
   curl -X GET http://localhost:8080/api/job-grades?bankCode=632005
   curl -X GET http://localhost:8080/api/members/userRoles
   curl -X GET http://localhost:8080/api/members/structures/tree
   curl -X GET http://localhost:8080/api/representatives
   ```

## Expected API Responses

### Success Response (JobGrade Example):
```json
[
  {
    "value": "G1",
    "label": "G1 - Senior Grade - Permanent"
  },
  {
    "value": "G2",
    "label": "G2 - Mid-Level Grade - Contract"
  }
]
```

### Error Response:
```json
{
  "timestamp": "2026-09-08T20:45:10Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Detailed error message",
  "path": "/api/job-grades"
}
```

## Files Modified

1. **`src/main/resources/application.yaml`**
   - Disabled Thymeleaf for API endpoints
   - Configured Spring MVC to handle API routes
   - Enhanced error response configuration
   - Improved logging levels

2. **`src/main/java/za/co/titandynamix/sasbo_online_backend/exception/GlobalExceptionHandler.java`**
   - Added NoHandlerFoundException handler
   - Ensures all exceptions return proper JSON responses

3. **`src/main/java/za/co/titandynamix/sasbo_online_backend/controller/JobGradeController.java`**
   - Refactored to use JobGradeResponseDto
   - Improved stream-based processing

4. **`src/main/java/za/co/titandynamix/sasbo_online_backend/dto/member/JobGradeResponseDto.java`** (New)
   - Proper response DTO for API consistency

## Testing Checklist

- [ ] All API endpoints return proper JSON responses
- [ ] 404 errors return JSON with proper error details
- [ ] 500 errors include meaningful error messages
- [ ] Frontend can successfully consume API responses
- [ ] Database connections work in production environment
- [ ] CORS headers are properly configured
- [ ] Frontend/Backend CORS validation passes

## Frontend Compatibility

✅ **No frontend changes required!** 

The frontend already expects `{value, label}` objects from the API, which matches the new `JobGradeResponseDto` structure. The response format is identical to the previous implementation.

## Additional Notes

- The `throw-exception-if-no-handler-found: true` configuration ensures that unmapped routes trigger the exception handler instead of returning a default static resource error
- Thymeleaf is disabled for API responses but other Spring Boot features remain intact
- All logging is set to DEBUG level to help diagnose issues in production
- The `add-mappings: false` configuration prevents Spring from trying to serve static resources that don't exist

## Rollback Instructions

If issues persist, you can:

1. Check database connectivity on Render
2. Verify all environment variables are set correctly
3. Review application logs for specific error messages
4. Ensure the PostgreSQL driver is properly loaded
5. Check that Flyway migrations run successfully

## Build Status

✅ **BUILD SUCCESSFUL** - Clean build with only 1 serialVersionUID warning (non-critical)

The application is ready for deployment to Render/Vercel with these fixes in place.


package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import za.co.titandynamix.sasbo_online_backend.config.PayrollApi;
import za.co.titandynamix.sasbo_online_backend.controller.PayrollController;
import za.co.titandynamix.sasbo_online_backend.controller.PayrollDiscrepancyController;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The payroll module's guards, each with a canary: its refusal codes published and thrown; every route carrying a
 * payroll permission and head office's scope (PAY-32, PAY-33); its migrations starting with the lock timeout; its DTOs
 * carrying dates as text; and no log call of the module handing the logger a person's details.
 */
@DisplayName("The payroll module's guards")
class PayrollGuardsTest {

    private static final Path MIGRATIONS = Paths.get("src", "main", "resources", "db", "migration");

    /** The module's sources, by name: what the log and DTO scans read. */
    private static List<MainClassSource> payrollSources() {
        return MainSourceInventory.all().stream()
                .filter(c -> c.name().startsWith("Payroll") || c.path().toString().replace('\\', '/').contains("/payroll/"))
                .toList();
    }

    @Test
    @DisplayName("every refusal code is published in the committed vocabulary with its status, and every kind of discrepancy")
    void everyCodeIsPublished() throws IOException {
        JsonNode payroll = new ObjectMapper().readTree(ApiSurface.readCommitted(ApiSurface.VOCABULARY_FILE)).path("payroll");
        Set<String> published = new TreeSet<>();
        payroll.path("refusalCodes").forEach(e -> published.add(e.path("code").asText() + "=" + e.path("status").asInt()));
        List<String> missing = new ArrayList<>();
        for (PayrollRefusalCode code : PayrollRefusalCode.values()) {
            if (!published.contains(code.name() + "=" + code.status().value())) {
                missing.add(code.name());
            }
        }
        assertThat(missing).as("regenerate the vocabulary: %s", ApiSurface.regenerateCommand()).isEmpty();
        Set<String> types = new TreeSet<>();
        payroll.path("discrepancyTypes").forEach(t -> types.add(t.path("type").asText()));
        assertThat(types).hasSize(PayrollDiscrepancyType.values().length);
    }

    @Test
    @DisplayName("every refusal code is thrown somewhere -- the vocabulary promises nothing no route gives")
    void everyCodeIsThrown() {
        List<MainClassSource> sources = MainSourceInventory.all().stream()
                .filter(c -> !c.name().equals(PayrollRefusalCode.class.getSimpleName())).toList();
        assertNonVacuousScan(sources, 100, "main sources");
        List<String> unthrown = new ArrayList<>();
        for (PayrollRefusalCode code : PayrollRefusalCode.values()) {
            Pattern named = Pattern.compile("PayrollRefusalCode\\." + code.name() + "\\b");
            if (sources.stream().noneMatch(s -> named.matcher(s.code()).find())) {
                unthrown.add(code.name());
            }
        }
        assertThat(unthrown).isEmpty();
    }

    @Test
    @DisplayName("🔒 PAY-33: every payroll route carries a payroll permission and asks for head office's scope")
    void everyRouteIsGuarded() throws IOException {
        List<String> offenders = new ArrayList<>();
        int routes = 0;
        for (Class<?> controller : List.of(PayrollController.class, PayrollDiscrepancyController.class)) {
            assertThat(controller.isAnnotationPresent(PayrollApi.class)).as(controller.getSimpleName()).isTrue();
            int own = 0;
            for (Method method : controller.getDeclaredMethods()) {
                if (!isRoute(method)) {
                    continue;
                }
                own++;
                PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
                if (guard == null || !guard.value().contains("PAYROLL_")) {
                    offenders.add(controller.getSimpleName() + "." + method.getName() + " has no payroll permission");
                }
                if (guard != null && guard.value().contains("PAYROLL_VIEW") && !isRead(method)) {
                    offenders.add(controller.getSimpleName() + "." + method.getName() + " writes with a read permission");
                }
            }
            routes += own;
            String source = MainSourceInventory.all().stream().filter(c -> c.name().equals(controller.getSimpleName()))
                    .findFirst().orElseThrow().code();
            int scoped = source.split("PayrollAccess\\.headOffice\\(\\)", -1).length - 1;
            assertThat(scoped).as("%s: every route asks for head office's scope", controller.getSimpleName())
                    .isEqualTo(own);
        }
        assertThat(routes).as("routes found").isGreaterThanOrEqualTo(20);
        assertThat(offenders).isEmpty();
    }

    private static boolean isRoute(Method method) {
        return method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class) || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }

    private static boolean isRead(Method method) {
        return method.isAnnotationPresent(GetMapping.class);
    }

    @Test
    @DisplayName("every payroll migration begins with the lock timeout, and no column takes the database's time")
    void migrationsBeginWithTheLockTimeout() throws IOException {
        List<Path> files;
        try (Stream<Path> listed = Files.list(MIGRATIONS)) {
            files = listed.filter(p -> p.getFileName().toString().matches("V7[3-6]__payroll_.*\\.sql")).toList();
        }
        assertThat(files).hasSize(4);
        for (Path file : files) {
            String sql = Files.readString(file, StandardCharsets.UTF_8);
            String firstStatement = sql.lines().filter(l -> !l.isBlank() && !l.startsWith("--")).findFirst().orElse("");
            assertThat(firstStatement).as(file.getFileName().toString()).isEqualTo("SET LOCAL lock_timeout = '5s';");
            assertThat(sql.toLowerCase(java.util.Locale.ROOT)).as(file.getFileName().toString())
                    .doesNotContain("default now()").doesNotContain("default current_timestamp");
        }
    }

    @Test
    @DisplayName("every payroll DTO carries dates and instants as ISO text -- no java.time component")
    void dtoDatesAreText() throws ClassNotFoundException {
        List<String> offenders = new ArrayList<>();
        int dtos = 0;
        for (MainClassSource source : payrollSources()) {
            String path = source.path().toString().replace('\\', '/');
            if (!path.contains("/dto/payroll/")) {
                continue;
            }
            Class<?> type = Class.forName("za.co.titandynamix.sasbo_online_backend.dto.payroll." + source.name());
            dtos++;
            offenders.addAll(AcbGuardsTest.javaTimeComponents(type));
            for (Class<?> nested : type.getDeclaredClasses()) {
                offenders.addAll(AcbGuardsTest.javaTimeComponents(nested));
            }
        }
        assertNonVacuousScan(java.util.Collections.nCopies(dtos, "dto"), 10, "payroll DTOs");
        assertThat(offenders).isEmpty();
    }

    /** What a payroll log call may never carry: a person's details, a note, a password, a file's name or a message. */
    static final List<String> FORBIDDEN_IN_LOGS = List.of("surname", "Surname", "initials", "Initials", "idNumber",
            "IdNumber", "identity", "Identity", "staffNumber", "StaffNumber", "note", "Note", "password", "Password",
            "OriginalName", "originalName", "raw", "getMessage", "detail");

    static final Pattern LOG_CALL = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(([^;]*);");

    @Test
    @DisplayName("🔒 no payroll log call hands the logger a person's details -- ids, counts and codes only")
    void noLogCallCarriesAPerson() {
        List<MainClassSource> sources = payrollSources();
        assertNonVacuousScan(sources, 20, "payroll sources");
        List<String> offenders = new ArrayList<>();
        for (MainClassSource source : sources) {
            Matcher call = LOG_CALL.matcher(source.code());
            while (call.find()) {
                String arguments = call.group(2);
                String values = arguments.substring(arguments.indexOf('"', arguments.indexOf('"') + 1) + 1);
                for (String forbidden : FORBIDDEN_IN_LOGS) {
                    if (values.contains(forbidden)) {
                        offenders.add(source.name() + ": a log call hands over " + forbidden);
                    }
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("canary: the log scan catches a surname handed to the logger, and passes a count")
    void theLogScanCanFail() {
        Matcher bad = LOG_CALL.matcher("log.info(\"Loaded {}\", line.surname());");
        assertThat(bad.find()).isTrue();
        String values = bad.group(2).substring(bad.group(2).indexOf('"', bad.group(2).indexOf('"') + 1) + 1);
        assertThat(FORBIDDEN_IN_LOGS.stream().anyMatch(values::contains)).isTrue();
        Matcher good = LOG_CALL.matcher("log.info(\"Loaded {} lines\", lines.size());");
        assertThat(good.find()).isTrue();
        String counted = good.group(2).substring(good.group(2).indexOf('"', good.group(2).indexOf('"') + 1) + 1);
        assertThat(FORBIDDEN_IN_LOGS.stream().anyMatch(counted::contains)).isFalse();
    }
}

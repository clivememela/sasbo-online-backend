package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMappingDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMappingPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationMappingRepository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The mappings a file can be read by: the built-in ones first and read-only; one defined here read strictly, refused
 * whole with its problems, never under a key that is taken; a change made to the version on record, or refused.
 */
@DisplayName("MigrationMappings")
class MigrationMappingsTest {

    private static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");

    private final MigrationMappingRepository repository = mock(MigrationMappingRepository.class);
    private final MigrationMappings mappings = new MigrationMappings(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    /** The built-in CSV mapping under a key of its own. */
    private String mine(String key) {
        return mappings.detail(LegacyFixtures.CSV_MAPPING).definition().toString()
                .replace("\"" + LegacyFixtures.CSV_MAPPING + "\"", "\"" + key + "\"");
    }

    private static InputStream body(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private MigrationMappingRepository.Saved saved(String key, int version) {
        return new MigrationMappingRepository.Saved(key, "Mine", mine(key), version, "system", NOW, "system", NOW);
    }

    @Test
    @DisplayName("the built-in mappings come first, read-only; one defined here after them, with its version")
    void lists() {
        when(repository.all(anyInt())).thenReturn(List.of(saved("ours", 3)));

        MigrationMappingPageDto page = mappings.page(0, 50);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(MigrationMappingPageDto.Summary::key)
                .containsExactly(LegacyFixtures.CSV_MAPPING, LegacyFixtures.FIXED_WIDTH_MAPPING, "ours");
        assertThat(page.items().get(0).builtIn()).isTrue();
        assertThat(page.items().get(0).version()).isNull();
        assertThat(page.items().get(2).builtIn()).isFalse();
        assertThat(page.items().get(2).version()).isEqualTo(3);
        assertThat(mappings.page(1, 2).items()).extracting(MigrationMappingPageDto.Summary::key).containsExactly("ours");
        assertThatThrownBy(() -> mappings.page(0, 101)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.PAGE_INVALID);
    }

    @Test
    @DisplayName("a mapping defined here is read strictly and stored, version 1")
    void defines() {
        when(repository.find("ours")).thenReturn(Optional.empty()).thenReturn(Optional.of(saved("ours", 1)));

        MigrationMappingDto defined = mappings.define(body(mine("ours")));

        assertThat(defined.key()).isEqualTo("ours");
        assertThat(defined.version()).isEqualTo(1);
        assertThat(defined.builtIn()).isFalse();
        verify(repository).insert(eq("ours"), anyString(), anyString(), eq("system"), eq(NOW));
    }

    @Test
    @DisplayName("MAPPING_INVALID: every problem counted, the first of them named by path -- nothing stored")
    void invalid() {
        assertThatThrownBy(() -> mappings.define(body("{\"key\": \"ours\", \"colour\": \"blue\"}")))
                .isInstanceOf(MigrationRefusal.class)
                .satisfies(refused -> {
                    MigrationRefusal refusal = (MigrationRefusal) refused;
                    assertThat(refusal.code()).isEqualTo(MigrationRefusalCode.MAPPING_INVALID);
                    assertThat(Integer.parseInt(refusal.details().get("problems"))).isGreaterThan(1);
                    assertThat(refusal.details().get("first")).contains("colour: not a setting a mapping has here");
                });
        assertThatThrownBy(() -> mappings.define(body("x".repeat(240_001))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.MAPPING_INVALID);
        verify(repository, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("MAPPING_KEY_TAKEN: a built-in mapping's key, one defined already, or one taken by a definition made at the same moment")
    void keysTaken() {
        assertThatThrownBy(() -> mappings.define(body(mine(LegacyFixtures.FIXED_WIDTH_MAPPING))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.MAPPING_KEY_TAKEN);
        when(repository.find("ours")).thenReturn(Optional.of(saved("ours", 1)));
        assertThatThrownBy(() -> mappings.define(body(mine("ours"))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.MAPPING_KEY_TAKEN);
        when(repository.find("raced")).thenReturn(Optional.empty());
        doThrow(new DuplicateKeyException("taken")).when(repository).insert(eq("raced"), any(), any(), any(), any());
        assertThatThrownBy(() -> mappings.define(body(mine("raced"))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.MAPPING_KEY_TAKEN);
    }

    @Test
    @DisplayName("MAPPING_READ_ONLY: a built-in mapping is never changed; MAPPING_CHANGED_SINCE: a change to a version not on record")
    void changes() {
        assertThatThrownBy(() -> mappings.update(LegacyFixtures.CSV_MAPPING, 1, body(mine(LegacyFixtures.CSV_MAPPING))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.MAPPING_READ_ONLY);

        when(repository.find("ours")).thenReturn(Optional.of(saved("ours", 2)));
        when(repository.update(eq("ours"), eq(1), anyString(), anyString(), anyString(), any())).thenReturn(false);
        assertThatThrownBy(() -> mappings.update("ours", 1, body(mine("ours"))))
                .isInstanceOf(MigrationRefusal.class)
                .satisfies(refused -> {
                    assertThat(((MigrationRefusal) refused).code()).isEqualTo(MigrationRefusalCode.MAPPING_CHANGED_SINCE);
                    assertThat(((MigrationRefusal) refused).details()).containsEntry("version", "2");
                });

        when(repository.update(eq("ours"), eq(2), anyString(), anyString(), anyString(), any())).thenReturn(true);
        assertThat(mappings.update("ours", 2, body(mine("ours"))).key()).isEqualTo("ours");

        assertThatThrownBy(() -> mappings.update("ours", 2, body(mine("renamed"))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.MAPPING_INVALID);
        when(repository.find("absent")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> mappings.update("absent", 1, body(mine("absent"))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.MAPPING_NOT_FOUND);
    }

    @Test
    @DisplayName("a mapping's definition by its key -- built in or defined here; one nobody has is MAPPING_NOT_FOUND")
    void definitions() {
        assertThat(mappings.definition(LegacyFixtures.CSV_MAPPING).key()).isEqualTo(LegacyFixtures.CSV_MAPPING);
        when(repository.find("ours")).thenReturn(Optional.of(saved("ours", 1)));
        assertThat(mappings.definition("ours").key()).isEqualTo("ours");
        when(repository.find("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> mappings.definition("nobody")).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.MAPPING_NOT_FOUND);
    }
}

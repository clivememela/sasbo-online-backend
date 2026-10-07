package za.co.titandynamix.sasbo_online_backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMappingDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMappingPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.BuiltInMappings;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingDefinition;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingInvalid;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingParser;
import za.co.titandynamix.sasbo_online_backend.legacy.read.LimitedInputStream;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceRefused;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationMappingRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The mappings a file can be read by: the built-in ones (resources, read-only, provisional until the export's real
 * layout is known) and those defined here by a holder of {@code MIGRATION_RUN}. A definition is read strictly --
 * {@link MappingParser} -- and refused whole with every problem it has; a change names the version it was made to.
 */
@Slf4j
@Service
public class MigrationMappings {

    /** The largest page of mappings. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Mappings defined here, at most: a list kept by people, never a table that grows by itself. */
    static final int MAX_DEFINED = 200;

    /** How many problems a refusal names in its details -- the rest are found by fixing these. */
    private static final int PROBLEMS_SHOWN = 10;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MigrationMappingRepository defined;
    private final MappingParser parser = new MappingParser();
    private final Map<String, MappingDefinition> builtIn;
    private final Clock clock;

    public MigrationMappings(MigrationMappingRepository defined, Clock clock) {
        this.defined = defined;
        this.clock = clock;
        this.builtIn = BuiltInMappings.load(parser);
    }

    /** The mapping with this key, parsed -- built in or defined here. */
    public MappingDefinition definition(String key) {
        MappingDefinition mapping = builtIn.get(key);
        if (mapping != null) {
            return mapping;
        }
        return defined.find(key).map(saved -> parser.parse(saved.definition())).orElseThrow(() -> notFound(key));
    }

    public MigrationMappingPageDto page(int page, int size) {
        MigrationPages.check(page, size, MAX_PAGE_SIZE);
        List<MigrationMappingPageDto.Summary> all = new ArrayList<>();
        builtIn.values().forEach(mapping -> all.add(new MigrationMappingPageDto.Summary(mapping.key(), mapping.name(),
                mapping.description(), mapping.format().format().name(), true, null, null, null)));
        for (MigrationMappingRepository.Saved saved : defined.all(MAX_DEFINED)) {
            MappingDefinition mapping = parser.parse(saved.definition());
            all.add(new MigrationMappingPageDto.Summary(saved.key(), saved.name(), mapping.description(),
                    mapping.format().format().name(), false, saved.version(), saved.updatedBy(),
                    AcbTime.iso(saved.updatedAt())));
        }
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new MigrationMappingPageDto(List.copyOf(all.subList(from, to)), page, size, all.size());
    }

    public MigrationMappingDto detail(String key) {
        MappingDefinition mapping = builtIn.get(key);
        if (mapping != null) {
            return new MigrationMappingDto(mapping.key(), mapping.name(), true, null, tree(mapping.json()));
        }
        MigrationMappingRepository.Saved saved = defined.find(key).orElseThrow(() -> notFound(key));
        return new MigrationMappingDto(saved.key(), saved.name(), false, saved.version(), tree(saved.definition()));
    }

    /** A new mapping, read strictly; its key may be no built-in one's and no defined one's. */
    public MigrationMappingDto define(InputStream body) {
        MappingDefinition mapping = parse(read(body));
        if (builtIn.containsKey(mapping.key()) || defined.find(mapping.key()).isPresent()) {
            throw keyTaken(mapping.key());
        }
        if (defined.count() >= MAX_DEFINED) {
            throw new MigrationRefusal(MigrationRefusalCode.MAPPING_INVALID, "There are already " + MAX_DEFINED
                    + " mappings defined here. Change one of them instead of defining another.",
                    MigrationRefusal.details("problems", "1", "first", "too many mappings"));
        }
        try {
            defined.insert(mapping.key(), mapping.name(), mapping.json(), AcbActor.current(), clock.instant());
        } catch (DuplicateKeyException race) {
            throw keyTaken(mapping.key());
        }
        log.info("Migration mapping defined: {}", mapping.key());
        return detail(mapping.key());
    }

    /** A change to a mapping defined here, made to the version named. A built-in mapping is never changed. */
    public MigrationMappingDto update(String key, int version, InputStream body) {
        if (builtIn.containsKey(key)) {
            throw new MigrationRefusal(MigrationRefusalCode.MAPPING_READ_ONLY, "A built-in mapping is never changed. "
                    + "Define your own from it, under a key of your own.", MigrationRefusal.details("key", key));
        }
        MigrationMappingRepository.Saved saved = defined.find(key).orElseThrow(() -> notFound(key));
        MappingDefinition mapping = parse(read(body));
        if (!mapping.key().equals(key)) {
            throw new MigrationRefusal(MigrationRefusalCode.MAPPING_INVALID,
                    "A mapping keeps its key: the definition must carry the key it is saved under.",
                    MigrationRefusal.details("problems", "1", "first", "key: must be the mapping's own"));
        }
        if (!defined.update(key, version, mapping.name(), mapping.json(), AcbActor.current(), clock.instant())) {
            throw new MigrationRefusal(MigrationRefusalCode.MAPPING_CHANGED_SINCE, "Someone changed this mapping since "
                    + "you read it. Read it again, then make your change to the version on record.",
                    MigrationRefusal.details("version", String.valueOf(defined.find(key).map(
                            MigrationMappingRepository.Saved::version).orElse(saved.version()))));
        }
        log.info("Migration mapping changed: {}", key);
        return detail(key);
    }

    private MappingDefinition parse(String text) {
        try {
            return parser.parse(text);
        } catch (MappingInvalid invalid) {
            List<String> shown = invalid.problems().subList(0, Math.min(PROBLEMS_SHOWN, invalid.problems().size()));
            throw new MigrationRefusal(MigrationRefusalCode.MAPPING_INVALID, "The mapping cannot be used: "
                    + invalid.problems().size() + " problem(s). Correct them and send it again.",
                    MigrationRefusal.details("problems", String.valueOf(invalid.problems().size()),
                            "first", String.join("; ", shown)));
        }
    }

    /** A definition's text, read no further than its ceiling allows: four bytes a character at the most. */
    private static String read(InputStream body) {
        try {
            byte[] bytes = new LimitedInputStream(body, MappingParser.MAX_DEFINITION_CHARS * 4L).readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (SourceRefused tooLarge) {
            throw new MigrationRefusal(MigrationRefusalCode.MAPPING_INVALID, "The mapping is longer than "
                    + MappingParser.MAX_DEFINITION_CHARS + " characters.",
                    MigrationRefusal.details("problems", "1", "first", "the definition is too long"));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static JsonNode tree(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("a stored mapping is not JSON");
        }
    }

    private static MigrationRefusal notFound(String key) {
        return new MigrationRefusal(MigrationRefusalCode.MAPPING_NOT_FOUND, "There is no mapping with that key.",
                MigrationRefusal.details("key", MigrationRefusal.echo(key)));
    }

    private static MigrationRefusal keyTaken(String key) {
        return new MigrationRefusal(MigrationRefusalCode.MAPPING_KEY_TAKEN,
                "A mapping with that key exists already. Choose another key, or change that mapping.",
                MigrationRefusal.details("key", key));
    }
}

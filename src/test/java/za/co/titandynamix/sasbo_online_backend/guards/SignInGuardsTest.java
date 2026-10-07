package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Sign-in's vocabulary held to its code (the pattern of month end's MV-33): every refusal code is published in
 * {@code api-vocabulary.json} under {@code auth}, with its status, and thrown or answered somewhere -- so the browser is
 * never told of a refusal no route gives, nor handed one it was never told of. The catalogue it publishes is the enum.
 */
@DisplayName("Sign-in's guards")
class SignInGuardsTest {

    static Set<String> publishedCodes(String vocabulary) throws IOException {
        Set<String> codes = new TreeSet<>();
        for (JsonNode entry : new ObjectMapper().readTree(vocabulary).path("auth").path("refusalCodes")) {
            codes.add(entry.path("code").asText() + "=" + entry.path("status").asInt());
        }
        return codes;
    }

    @Test
    @DisplayName("every sign-in refusal code is published in the committed vocabulary, with its status")
    void everyCodeIsPublished() throws IOException {
        Set<String> published = publishedCodes(ApiSurface.readCommitted(ApiSurface.VOCABULARY_FILE));
        List<String> missing = new ArrayList<>();
        for (SignInRefusalCode code : SignInRefusalCode.values()) {
            if (!published.contains(code.name() + "=" + code.status().value())) {
                missing.add(code.name() + " (" + code.status().value() + ")");
            }
        }
        assertThat(missing).as("codes the server answers that the browser has never been told about. Regenerate: %s, "
                + "and copy the file to the client", ApiSurface.regenerateCommand()).isEmpty();
    }

    @Test
    @DisplayName("the published catalogue is the enum, every name, in its order -- the client's PERMISSIONS equals it")
    void theCatalogueIsTheEnum() throws IOException {
        JsonNode auth = new ObjectMapper().readTree(ApiSurface.readCommitted(ApiSurface.VOCABULARY_FILE)).path("auth");
        List<String> published = new ArrayList<>();
        auth.path("permissions").forEach(name -> published.add(name.asText()));
        assertThat(published).containsExactlyElementsOf(Arrays.stream(Permission.values()).map(Enum::name).toList());
        List<String> modes = new ArrayList<>();
        auth.path("modes").forEach(mode -> modes.add(mode.asText()));
        assertThat(modes).containsExactly("open", "required");
    }

    @Test
    @DisplayName("canary: an unpublished code is caught")
    void theVocabularyPredicateCanFail() throws IOException {
        Set<String> published = publishedCodes("{\"auth\": {\"refusalCodes\": [{\"code\": \"SIGN_IN_FAILED\", "
                + "\"status\": 401}]}}");
        assertThat(published).containsExactly("SIGN_IN_FAILED=401");
        assertThat(published).doesNotContain("ACCOUNT_LOCKED=423");
    }

    static boolean names(String blankedSource, SignInRefusalCode code) {
        return Pattern.compile("SignInRefusalCode\\." + code.name() + "\\b").matcher(blankedSource).find();
    }

    @Test
    @DisplayName("every sign-in refusal code is answered somewhere -- the vocabulary promises nothing no route can give")
    void everyCodeIsUsed() {
        List<MainClassSource> sources = MainSourceInventory.all().stream()
                .filter(c -> !c.name().equals(SignInRefusalCode.class.getSimpleName()))
                .toList();
        assertNonVacuousScan(sources, 100, "main sources");
        List<String> unused = new ArrayList<>();
        for (SignInRefusalCode code : SignInRefusalCode.values()) {
            if (sources.stream().noneMatch(source -> names(source.code(), code))) {
                unused.add(code.name());
            }
        }
        assertThat(unused).as("codes declared ahead of the code that answers them").isEmpty();
    }

    @Test
    @DisplayName("canary: a code named only in a comment is caught as unused")
    void theUsePredicateCanFail() {
        String used = MainSourceInventory.blankComments("class X { Object f() { return SignInRefusalCode.TOO_MANY; } }");
        assertThat(names(used, SignInRefusalCode.TOO_MANY)).isTrue();
        assertThat(names(used, SignInRefusalCode.ROLE_FIXED)).isFalse();
        String commented = MainSourceInventory.blankComments("class X { // SignInRefusalCode.ROLE_FIXED one day\n }");
        assertThat(names(commented, SignInRefusalCode.ROLE_FIXED)).isFalse();
    }
}

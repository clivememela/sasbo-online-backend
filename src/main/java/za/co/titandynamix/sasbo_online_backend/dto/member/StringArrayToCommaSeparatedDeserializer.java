package za.co.titandynamix.sasbo_online_backend.dto.member;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes either a JSON array (["PEC","CEC"]) or a plain string ("PEC,CEC")
 * into a comma-separated String for storage.
 *
 * <p>🔴 AN EMPTY ARRAY IS {@code ""}, NOT {@code null} (V55). The member merge ignores a null
 * ({@code MemberMapper.updateFromMember}, decision ⑨(b): absent keeps, blank clears), so a null here
 * meant that unticking every box KEPT the stored list -- an edit that answered 200 and changed
 * nothing. An empty list is an answer, and a blank one clears.
 */
public class StringArrayToCommaSeparatedDeserializer extends StdDeserializer<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    public StringArrayToCommaSeparatedDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        if (p.currentToken() == JsonToken.START_ARRAY) {
            List<String> items = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                items.add(p.getText());
            }
            return String.join(",", items);
        }
        // Plain string passthrough
        return p.getText();
    }
}

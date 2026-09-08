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
            return items.isEmpty() ? null : String.join(",", items);
        }
        // Plain string passthrough
        return p.getText();
    }
}

package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import java.util.List;

/**
 * A mapping definition that cannot be used, with every problem found -- each a path into the definition and what is
 * wrong there ({@code fields.joinDate.dateFormats[1]: not a date pattern}). Nothing is half-accepted.
 */
public class MappingInvalid extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> problems;

    public MappingInvalid(List<String> problems) {
        super("The mapping definition cannot be used: " + problems.size() + " problem(s).");
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}

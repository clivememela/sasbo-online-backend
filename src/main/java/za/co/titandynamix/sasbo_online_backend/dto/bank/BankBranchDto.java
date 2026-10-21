package za.co.titandynamix.sasbo_online_backend.dto.bank;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.IOException;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankBranchDto {

    static class FlexibleStringDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isTextual()) return node.asText();
            if (node.isObject()) {
                String street = node.path("streetName").asText("");
                String number = node.path("streetNumber").asText("");
                return (number + " " + street).trim();
            }
            return node.asText("");
        }
    }
    // A29: every field the branch dialog writes is bounded by its column in `bank_branches`, and the two the dialog
    // marks required are required here too. Without these a long value failed in the database and came back as the
    // catch-all's 409 about "a member with the same identity number" -- the wrong sentence on the wrong screen.
    private Integer id;
    @NotBlank(message = "Bank Name is required.")
    @Size(max = 255, message = "Bank Name is at most 255 characters.")
    private String bankName;
    @Size(max = 255, message = "Abbreviation is at most 255 characters.")
    private String bankAbbreviation;
    @JsonAlias("branchName")
    @NotBlank(message = "Branch is required.")
    @Size(max = 255, message = "Branch is at most 255 characters.")
    private String branch;
    @Size(max = 6, message = "Branch Code is at most 6 characters.")
    private String branchCode;
    @Size(max = 255, message = "Area is at most 255 characters.")
    private String area;
    @Size(max = 255, message = "Random Access Number is at most 255 characters.")
    private String randomAccessNumber;
    @Size(max = 255, message = "Phone Number is at most 255 characters.")
    private String phoneNumber;
    @Size(max = 255, message = "Manager Name is at most 255 characters.")
    private String managerName;
    @Size(max = 255, message = "Fax is at most 255 characters.")
    private String fax;
    @Size(max = 255, message = "Email is at most 255 characters.")
    private String email;
    private String category;
    private Integer repsCount;
    private Integer membersCount;
    private Integer insertsCount;
    @PositiveOrZero(message = "Staff Complement cannot be negative.")
    private Integer branchStaffComplement;
    @PositiveOrZero(message = "Member Complement cannot be negative.")
    private Integer branchMemberComplement;
    private String lastVisitedDate;
    @Size(max = 255, message = "SASBO Area Code is at most 255 characters.")
    private String sasboAreaCode;
    private String bulkMail;
    @Size(max = 255, message = "Province Committee Code is at most 255 characters.")
    private String provinceCommitteeCode;
    @Size(max = 255, message = "Province Committee Name is at most 255 characters.")
    private String provinceCommitteeName;
    @Size(max = 255, message = "Bank Department Code is at most 255 characters.")
    private String bankDepartmentCode;
    @PositiveOrZero(message = "Subs Value Branch cannot be negative.")
    private Double subsValueBranch;
    @PositiveOrZero(message = "Table Code cannot be negative.")
    private Integer tableCode;
    /** One line: it is stored as the address's street name, a 100-character column. */
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @Size(max = 100, message = "Physical Address is at most 100 characters.")
    private String physicalAddress;
    @Size(max = 500, message = "Postal Address is at most 500 characters.")
    private String postalAddress;
}

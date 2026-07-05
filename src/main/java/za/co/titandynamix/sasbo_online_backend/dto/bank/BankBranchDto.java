package za.co.titandynamix.sasbo_online_backend.dto.bank;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
    private Integer id;
    private String bankName;
    private String bankAbbreviation;
    @JsonAlias("branchName")
    private String branch;
    private String branchCode;
    private String area;
    private String randomAccessNumber;
    private String phoneNumber;
    private String managerName;
    private String fax;
    private String email;
    private String category;
    private Integer repsCount;
    private Integer membersCount;
    private Integer insertsCount;
    private Integer branchStaffComplement;
    private Integer branchMemberComplement;
    private String lastVisitedDate;
    private String sasboAreaCode;
    private String bulkMail;
    private String provinceCommitteeCode;
    private String provinceCommitteeName;
    private String bankDepartmentCode;
    private Double subsValueBranch;
    private Integer tableCode;
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    private String physicalAddress;
    private String postalAddress;
}

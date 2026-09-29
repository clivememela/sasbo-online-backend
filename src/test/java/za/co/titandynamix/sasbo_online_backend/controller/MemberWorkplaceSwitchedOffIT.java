package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A member's workplace where the deployment's switch serves NONE -- which is every host until SASBO has answered MM79
 * (month end 01.3, MD62). A switch set back to none after the workplaces were seeded leaves their rows, and a member's
 * tie to one, where they are -- and NOTHING ANSWERS THEM: not the list, not the member's own answer; and a save that
 * names a workplace is refused as one this deployment does not have. The context is the lane's own, the switch unset.
 */
@AutoConfigureMockMvc
@DisplayName("A member's workplace, where the switch serves none")
class MemberWorkplaceSwitchedOffIT extends AbstractWritingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("MD62: set back to none, the rows stay and stop being answered; a save naming one is WORKPLACE_UNKNOWN")
    void setBackTheRowsStayAndStopBeingAnswered() throws Exception {
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('NORTH', 'NORTH')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'A', id FROM sasbo_regions WHERE code = 'NORTH'");
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, area_id, committee_code, staff_complement,"
                + " cl_code) SELECT 900001, 'INVENTED BRANCH', 'INVENTED BANK', id, 'Q99', 10, '00000000' "
                + "FROM sasbo_areas WHERE letter = 'A'");
        Long workplace = jdbc.queryForObject("SELECT id FROM workplaces", Long.class);
        Integer id = memberRepository.save(MemberFixture.aMember()).getId();
        jdbc.update("UPDATE members SET workplace_id = ? WHERE id = ?", workplace, id);

        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].workplaceId").doesNotExist())
                .andExpect(jsonPath("$[0].workplaceName").doesNotExist())
                .andExpect(jsonPath("$[0].workplaceRaNumber").doesNotExist());
        mockMvc.perform(get("/api/members/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workplaceName").doesNotExist())
                .andExpect(jsonPath("$.workplace").doesNotExist());
        mockMvc.perform(put("/api/members/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workplaceId\": " + workplace + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORKPLACE_UNKNOWN"));

        assertThat(jdbc.queryForObject("SELECT workplace_id FROM members WHERE id = ?", Long.class, id))
                .as("the rows stay: a switch decides what is served, not what is on record")
                .isEqualTo(workplace);
    }
}

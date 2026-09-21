package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * The Debits screen's member search. A POST, so an identity number never travels in a URL -- where
 * proxies and access logs keep it. Any field may be blank; with all three blank nothing is searched.
 *
 * @param surname          part of the surname, any case
 * @param membershipNumber part of the membership number, any case
 * @param identityNumber   the whole identity number -- matched exactly, never returned
 */
public record MemberSearchRequest(String surname, String membershipNumber, String identityNumber,
                                  Integer page, Integer size) {
}

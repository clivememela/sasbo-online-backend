package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceListAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceStatusDto;

/**
 * SASBO's lists, one typed read a list (01.1, MD41), and the status read that says for each why it is empty where it
 * is (ME-18, ME-19). Every read is open, as every reference read of the application is: a list of organisations and
 * codes names nobody (MD4).
 */
public interface ReferenceListService {

    ReferenceListAnswer<ReferenceRows.Region> regions();

    ReferenceListAnswer<ReferenceRows.Area> areas();

    ReferenceListAnswer<ReferenceRows.EmployerGroup> employerGroups();

    ReferenceListAnswer<ReferenceRows.Employer> employers();

    ReferenceListAnswer<ReferenceRows.TableCode> tableCodes();

    ReferenceListAnswer<ReferenceRows.CommitteeProvince> committeeProvinces();

    ReferenceListAnswer<ReferenceRows.Committee> committees();

    ReferenceListAnswer<ReferenceRows.SuspensionCode> suspensionCodes();

    ReferenceStatusDto status();
}

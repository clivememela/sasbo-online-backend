/**
 * RC15 -- THE RECRUITERS SCREEN'S ROWS: ONE PER RECRUITMENT CHANNEL, AND THE MEMBERS RECRUITED THROUGH IT.
 *
 * 🔴 The screen counted a channel's members by `recruitmentRepresentativeId` -- the member's RECRUITER, a person --
 * matched against the CHANNEL's id, so each row counted whoever's recruiter happened to share the channel's number
 * (BEC, id 1, counted the members of recruiter 1). A member is recruited through a channel when their own channel
 * (`recruitedHow` on the wire; `recruitment_channel_id` on record) is it.
 *
 * The rows come from the server's figures (`GET /api/recruitment-channels/summary`), counted in SQL from the member's own
 * channel. Step 1 (client 3c647cd) held the screen's own count and these rows to one fixture
 * (`src/test/fixtures/recruitment-channel-fixture.json`, the same file the server's test holds its SQL to) and found them
 * equal string for string; step 2 removed the screen's count with the register it was loaded from.
 */

/** A bank's province and area, from the branch listing -- the last branch listed for a bank name wins, as it did. */
export const bankPlacesOf = (branches) => {
  const places = {};
  (Array.isArray(branches) ? branches : []).forEach((branch) => {
    if (branch?.bankName) places[branch.bankName] = { province: branch.provinceCommitteeName, area: branch.area };
  });
  return places;
};

/** Reading order: ignoring case first, exactly after -- the server's own (`READING_ORDER`). */
const readingOrder = (a, b) => {
  const la = a.toLowerCase();
  const lb = b.toLowerCase();
  if (la !== lb) return la < lb ? -1 : 1;
  if (a === b) return 0;
  return a < b ? -1 : 1;
};

const listed = (values) => [...values].filter(Boolean).sort(readingOrder);

/** The row the table draws for one channel. */
const rowOf = (channel, count, banks, branches, places) => {
  const provinces = new Set();
  const areas = new Set();
  banks.forEach((bank) => {
    const place = places[bank] || {};
    if (place.province) provinces.add(place.province);
    if (place.area) areas.add(place.area);
  });
  return {
    id: channel.id,
    name: channel.name,
    enabled: channel.enabled,
    banks: banks.join(', '),
    branches: branches.join(', '),
    provinces: listed(provinces).join(', '),
    areas: listed(areas).join(', '),
    count,
  };
};

/** The rows in the screen's order: by name, numbers counted as numbers ("Channel 2" before "Channel 10"). */
const inScreenOrder = (rows) =>
  rows.sort((a, b) => (a.name || '').localeCompare(b.name || '', undefined, { numeric: true, sensitivity: 'base' }));

/** The rows from the server's figures, which are already counted and listed. */
export const channelRowsFromSummary = (summary, places) =>
  inScreenOrder((Array.isArray(summary) ? summary : []).map((channel) => rowOf(
    { id: channel.channelId, name: channel.name, enabled: channel.enabled },
    channel.members ?? 0,
    Array.isArray(channel.banks) ? channel.banks : [],
    Array.isArray(channel.branches) ? channel.branches : [],
    places,
  )));

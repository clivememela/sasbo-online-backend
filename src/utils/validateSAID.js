/**
 * Validates a South African ID number using the Luhn algorithm.
 * @param {string} id
 * @returns {string|null} error message or null if valid
 */
export function validateSAID(id) {
  if (!id) return null;
  if (id.length !== 13 || !/^\d+$/.test(id)) return 'ID must be exactly 13 digits';

  const month = parseInt(id.substring(2, 4), 10);
  const day = parseInt(id.substring(4, 6), 10);
  if (month < 1 || month > 12) return 'Invalid birth month';
  if (day < 1 || day > 31) return 'Invalid birth day';

  let sum = 0;
  for (let i = 0; i < 12; i++) {
    let digit = parseInt(id[i], 10);
    if (i % 2 !== 0) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
  }
  const checkDigit = (10 - (sum % 10)) % 10;
  if (checkDigit !== parseInt(id[12], 10)) return 'Invalid South African ID (Check digit failure)';

  return null;
}

/**
 * Validates an email address.
 * @param {string} email
 * @returns {string|null} error message or null if valid
 */
export function validateEmail(email) {
  if (!email) return null;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? null : 'Invalid email specified';
}

/**
 * Extracts the date of birth from a 13-digit South African ID number.
 * Format: YYMMDD... where century is inferred from the current year.
 * @param {string} id
 * @returns {string|null} ISO date string "yyyy-MM-dd" or null if invalid
 */
export function extractDateOfBirthFromSAID(id) {
  if (!id || id.length !== 13 || !/^\d+$/.test(id)) return null;

  const yy = parseInt(id.substring(0, 2), 10);
  const mm = id.substring(2, 4);
  const dd = id.substring(4, 6);

  const currentYear = new Date().getFullYear();
  const century = Math.floor(currentYear / 100) * 100;
  const twoDigitCurrent = currentYear % 100;

  const fullYear = yy > twoDigitCurrent ? century - 100 + yy : century + yy;
  const dateStr = `${fullYear}-${mm}-${dd}`;

  const date = new Date(dateStr);
  if (isNaN(date.getTime()) || date > new Date()) return null;
  return dateStr;
}

/**
 * Extracts the gender from a 13-digit South African ID number.
 * Sequence digits (6-9): 0000-4999 -> Female, 5000-9999 -> Male.
 * @param {string} id
 * @returns {string|null} "Male" or "Female", or null if invalid
 */
export function extractGenderFromSAID(id) {
  if (!id || id.length !== 13 || !/^\d+$/.test(id)) return null;
  const sequence = parseInt(id.substring(6, 10), 10);
  return sequence >= 5000 ? 'Male' : 'Female';
}

/**
 * Formats a date value (ISO string "yyyy-mm-dd" or array [yyyy, mm, dd]) to "dd-mm-yyyy".
 * @param {string|number[]} date
 * @returns {string}
 */
export function formatDate(date) {
  if (!date) return "-";
  if (Array.isArray(date)) {
    const [y, m, d] = date;
    return `${String(d).padStart(2, "0")}-${String(m).padStart(2, "0")}-${y}`;
  }
  const [y, m, d] = String(date).split("-");
  return `${d}-${m}-${y}`;
}

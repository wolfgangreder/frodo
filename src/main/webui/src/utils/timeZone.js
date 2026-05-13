/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Timezone utilities for Frodo frontend.
 *
 * The backend sends LocalDateTime as ISO strings WITHOUT a Z suffix
 * (e.g. "2026-05-11T12:00:00"). These represent UTC instants but lack
 * the timezone marker. Passing them directly to `new Date()` causes
 * Chrome to treat them as local time; Safari may return NaN.
 *
 * All helpers here append 'Z' before parsing so the value is always
 * interpreted as UTC, then display in the user's browser timezone
 * (fallback: Europe/Vienna).
 */

/**
 * Returns the user's IANA timezone string (e.g. "Europe/Vienna").
 * Falls back to "Europe/Vienna" if the browser doesn't support Intl.
 *
 * @returns {string}
 */
export function getUserTimezone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Vienna';
  } catch {
    return 'Europe/Vienna';
  }
}

/**
 * Parse a UTC ISO string (with or without trailing Z) into a Date.
 * Returns null if the value is falsy or unparseable.
 *
 * @param {string|null|undefined} isoString
 * @returns {Date|null}
 */
export function parseUtcIso(isoString) {
  if (!isoString) return null;
  // Append Z if missing so the browser treats it as UTC, not local.
  const normalized = isoString.endsWith('Z') ? isoString : isoString + 'Z';
  const d = new Date(normalized);
  return isNaN(d.getTime()) ? null : d;
}

/**
 * Format a UTC ISO string for locale-aware display in the user's timezone.
 * Shows date and time.
 *
 * @param {string|null|undefined} isoString
 * @param {string} [fallback='Never']
 * @returns {string}
 */
export function formatForDisplay(isoString, fallback = 'Never') {
  const d = parseUtcIso(isoString);
  if (!d) return fallback;
  return d.toLocaleString(undefined, { timeZone: getUserTimezone() });
}

/**
 * Format a UTC ISO string showing only the time portion.
 *
 * @param {string|null|undefined} isoString
 * @param {string} [fallback='—']
 * @returns {string}
 */
export function formatTimeOnly(isoString, fallback = '—') {
  const d = parseUtcIso(isoString);
  if (!d) return fallback;
  return d.toLocaleTimeString(undefined, { timeZone: getUserTimezone() });
}

/**
 * Format a UTC ISO string as a relative time-ago string.
 * e.g. "5s ago", "3m ago", "2h ago", "1d ago"
 *
 * @param {string|null|undefined} isoString
 * @returns {string}
 */
export function formatTimeAgo(isoString) {
  const d = parseUtcIso(isoString);
  if (!d) return '';
  const seconds = Math.floor((Date.now() - d.getTime()) / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

/**
 * Convert a Date to the value string expected by <input type="datetime-local">.
 * The input expects a value in local time (yyyy-MM-ddTHH:mm:ss).
 *
 * @param {Date} date
 * @returns {string}  e.g. "2026-05-11T14:00:00"
 */
export function toDateTimeLocalValue(date) {
  if (!date || isNaN(date.getTime())) return '';
  const tz = getUserTimezone();
  // Format the date in local timezone as ISO-like string.
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: tz,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date);

  const get = (type) => parts.find((p) => p.type === type)?.value ?? '00';
  // en-CA gives yyyy-MM-dd date format; hour12:false gives 00–23
  const dateStr = `${get('year')}-${get('month')}-${get('day')}`;
  const timeStr = `${get('hour').replace('24', '00')}:${get('minute')}:${get('second')}`;
  return `${dateStr}T${timeStr}`;
}

/**
 * Convenience: current moment as a datetime-local value string in user timezone.
 *
 * @returns {string}
 */
export function nowAsDateTimeLocalValue() {
  return toDateTimeLocalValue(new Date());
}

/**
 * Parse a datetime-local value (yyyy-MM-ddTHH:mm:ss, local timezone)
 * back to a UTC ISO string suitable for sending to the backend.
 *
 * @param {string} localValue  e.g. "2026-05-11T14:00:00"
 * @returns {string}  UTC ISO string without Z, e.g. "2026-05-11T12:00:00"
 */
export function fromDateTimeLocalValue(localValue) {
  if (!localValue) return '';
  const tz = getUserTimezone();
  // Interpret the local value as being in user timezone.
  // We parse it by treating it as UTC first, then adjust for the offset.
  const naive = new Date(localValue + 'Z'); // pretend UTC to get ms
  if (isNaN(naive.getTime())) return localValue;

  // Get offset between UTC and user timezone at this moment.
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: tz,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false,
  });

  // Find the UTC time that, when expressed in `tz`, equals `localValue`.
  // Binary search / Newton's method is overkill; use offset approach instead.
  const offsetMs = getTimezoneOffsetMs(tz, naive);
  const utc = new Date(naive.getTime() - offsetMs);
  // Return without Z — backend expects LocalDateTime ISO string (no offset)
  return utc.toISOString().replace('Z', '').slice(0, 19);
}

/**
 * Get the UTC offset in milliseconds for a given IANA timezone at a given Date.
 * Positive means the local time is ahead of UTC (e.g. Europe/Vienna is +60 or +120 min).
 *
 * @param {string} tz   IANA timezone string
 * @param {Date} date
 * @returns {number}  offset in ms
 */
function getTimezoneOffsetMs(tz, date) {
  // Format the date in the target timezone and parse back to determine offset.
  const fmt = new Intl.DateTimeFormat('en-US', {
    timeZone: tz,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false,
  });
  const parts = fmt.formatToParts(date);
  const get = (type) => parseInt(parts.find((p) => p.type === type)?.value ?? '0', 10);
  const localMs = Date.UTC(
    get('year'), get('month') - 1, get('day'),
    get('hour') === 24 ? 0 : get('hour'), get('minute'), get('second')
  );
  return localMs - date.getTime();
}

import crypto from 'crypto';

// Alphabet without ambiguous characters (0, O, 1, I) -> exactly 32 characters
const INVITE_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';

/**
 * Normalizes an invite code by trimming, uppercasing, and removing dashes and whitespace.
 */
export function normalizeInviteCode(code: string): string {
  return code.trim().toUpperCase().replace(/[-\s]/g, '');
}

/**
 * Computes the SHA-256 hex digest of a normalized invite code.
 */
export function hashInviteCode(code: string): string {
  const normalized = normalizeInviteCode(code);
  return crypto.createHash('sha256').update(normalized).digest('hex');
}

/**
 * Generates a cryptographically random 12-character invite code
 * formatted as XXXX-XXXX-XXXX using an unambiguous 32-character alphabet.
 */
export function generateRawInviteCode(): string {
  const bytes = crypto.randomBytes(12);
  let chars = '';
  for (let i = 0; i < 12; i++) {
    // 32 is a power of 2, so (byte & 31) has zero modulo bias
    chars += INVITE_ALPHABET[bytes[i] & 31];
  }
  return `${chars.slice(0, 4)}-${chars.slice(4, 8)}-${chars.slice(8, 12)}`;
}

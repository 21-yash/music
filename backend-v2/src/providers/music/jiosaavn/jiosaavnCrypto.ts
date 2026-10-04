
/**
 * JioSaavn media URL decryption.
 *
 * JioSaavn encrypts CDN URLs using DES-ECB with a known static key.
 * This module decrypts those URLs and generates quality variant URLs.
 * Uses crypto-js instead of native Node.js crypto because OpenSSL 3.0 (Node 17+)
 * removed support for legacy DES-ECB ciphers by default, causing ERR_OSSL_EVP_UNSUPPORTED.
 */
import CryptoJS from 'crypto-js';
import { logger } from '../../../utils/logger';

// The key as a WordArray for crypto-js. This is JioSaavn's well-known static DES-ECB key.
const JIOSAAVN_KEY = CryptoJS.enc.Utf8.parse('38346591');

/**
 * Decrypt a JioSaavn encrypted_media_url to get the real CDN link.
 */
export function decryptMediaUrl(encryptedUrl: string): string | null {
  if (!encryptedUrl) return null;

  try {
    const decrypted = CryptoJS.DES.decrypt(
      { ciphertext: CryptoJS.enc.Base64.parse(encryptedUrl) } as CryptoJS.lib.CipherParams,
      JIOSAAVN_KEY,
      {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7,
      }
    );

    return decrypted.toString(CryptoJS.enc.Utf8);
  } catch (error) {
    logger.error({ error }, 'JioSaavn decryption failed');
    return null;
  }
}

/**
 * Build quality variant URLs from a decrypted base URL.
 *
 * JioSaavn encodes quality in the filename — `_96.mp4` is the base,
 * and we can request `_320.mp4` or `_160.mp4` instead.
 */
export function getQualityUrl(
  decryptedUrl: string,
  quality: 'high' | 'medium' | 'low',
): string {
  const qualityMap = {
    high: '_320.mp4',
    medium: '_160.mp4',
    low: '_96.mp4',
  };

  const target = qualityMap[quality];

  return decryptedUrl
    .replace(/_96\.mp4$/, target)
    .replace(/_96_p\.mp4$/, target);
}

/**
 * Get the quality label string for a quality level.
 */
export function getQualityLabel(quality: 'high' | 'medium' | 'low'): string {
  const labels = {
    high: '320kbps',
    medium: '160kbps',
    low: '96kbps',
  };
  return labels[quality];
}

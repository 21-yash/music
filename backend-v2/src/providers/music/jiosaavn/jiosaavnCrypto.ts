import crypto from 'crypto';

/**
 * JioSaavn media URL decryption.
 *
 * JioSaavn encrypts CDN URLs using DES-ECB with a known static key.
 * This module decrypts those URLs and generates quality variant URLs.
 *
 * Uses Node.js native crypto instead of CryptoJS — no extra dependency.
 */

const JIOSAAVN_KEY = Buffer.from('38346591', 'utf8');

/**
 * Decrypt a JioSaavn encrypted_media_url to get the real CDN link.
 */
export function decryptMediaUrl(encryptedUrl: string): string | null {
  if (!encryptedUrl) return null;

  try {
    const decipher = crypto.createDecipheriv('des-ecb', JIOSAAVN_KEY, null);
    decipher.setAutoPadding(true);

    const encrypted = Buffer.from(encryptedUrl, 'base64');
    let decrypted = decipher.update(encrypted);
    decrypted = Buffer.concat([decrypted, decipher.final()]);

    return decrypted.toString('utf8');
  } catch {
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
    .replace(/_96\.mp4/, target)
    .replace(/_96_p\.mp4/, target);
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

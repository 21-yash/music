/**
 * Clean and normalize a song title to its base essence.
 * Removes parentheticals, brackets, and suffixes containing:
 * remix, cover, slowed, reverb, acoustic, lofi, official, video, audio, edit, version, feat, ft, etc.
 */
export function getBaseSongTitle(title: string): string {
  if (!title) return '';

  let cleaned = title
    .replace(/&quot;/g, '"')
    .replace(/&amp;/g, '&')
    .replace(/&#039;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>');

  // Remove content in brackets or parentheses that specify alternate versions, movie sources, or features
  cleaned = cleaned.replace(
    /\s*[\(\[\{].*?(remix|cover|slowed|reverb|acoustic|lofi|edit|version|mix|reprise|unplugged|mashup|live|deluxe|bonus|remaster|karaoke|instrumental|feat|ft\.|featuring|from|soundtrack|ost|official).*?[\)\]\}]/gi,
    ''
  );

  // Remove " - Remix", " - Slowed & Reverb", " - Acoustic", etc. at the end
  cleaned = cleaned.replace(
    /\s*-\s*.*?(remix|cover|slowed|reverb|acoustic|lofi|edit|version|mix|reprise|unplugged|mashup|live|deluxe|karaoke|instrumental).*$/gi,
    ''
  );

  // Remove trailing "feat. ...", "ft. ..."
  cleaned = cleaned.replace(/\s*(feat\.|ft\.|featuring)\s+.*$/gi, '');

  // Lowercase, remove non-alphanumeric chars, trim whitespace
  return cleaned
    .toLowerCase()
    .replace(/[^a-z0-9]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Generate a deduplication fingerprint for a track based on its base title
 * and primary artist token.
 */
export function getSongFingerprint(title: string, primaryArtist?: string): string {
  const baseTitle = getBaseSongTitle(title);
  const baseArtist = (primaryArtist || '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')[0] || '';

  return `${baseTitle}__${baseArtist}`;
}

/**
 * Filter out duplicate songs, alternate covers, remixes, and existing exclusion seeds.
 */
export function deduplicateRelatedSongs<T extends { title: string; artists?: Array<{ name: string }> | string }>(
  candidates: T[],
  seedTitle?: string,
  seedArtist?: string,
  excludedIds?: Set<string>,
  getId?: (item: T) => string
): T[] {
  const seenFingerprints = new Set<string>();

  if (seedTitle) {
    seenFingerprints.add(getSongFingerprint(seedTitle, seedArtist));
  }

  const result: T[] = [];

  for (const item of candidates) {
    if (getId && excludedIds) {
      const id = getId(item);
      if (id && excludedIds.has(id)) {
        continue;
      }
    }

    const artistName = Array.isArray(item.artists)
      ? item.artists[0]?.name || ''
      : typeof item.artists === 'string'
      ? item.artists
      : '';

    const fp = getSongFingerprint(item.title, artistName);
    if (!fp || seenFingerprints.has(fp)) {
      continue;
    }

    seenFingerprints.add(fp);
    result.push(item);
  }

  return result;
}

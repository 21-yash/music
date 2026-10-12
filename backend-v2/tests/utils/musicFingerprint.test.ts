import { describe, it, expect } from 'vitest';
import { getBaseSongTitle, getSongFingerprint, deduplicateRelatedSongs } from '../../src/utils/musicFingerprint';

describe('Music Fingerprint & Anti-Duplicate Engine', () => {
  it('cleans remixes, covers, and brackets from title', () => {
    expect(getBaseSongTitle('Tauba Tauba - Remix')).toBe('tauba tauba');
    expect(getBaseSongTitle('Tauba Tauba (From "Bad Newz")')).toBe('tauba tauba');
    expect(getBaseSongTitle('Tauba Tauba (Official Video)')).toBe('tauba tauba');
    expect(getBaseSongTitle('Tauba Tauba (Slowed + Reverb)')).toBe('tauba tauba');
    expect(getBaseSongTitle('Tauba Tauba [Lofi Mix]')).toBe('tauba tauba');
    expect(getBaseSongTitle('Tauba Tauba (Acoustic Version)')).toBe('tauba tauba');
    expect(getBaseSongTitle('Tauba Tauba feat. Badshah')).toBe('tauba tauba');
  });

  it('generates consistent fingerprints for alternate versions', () => {
    const fp1 = getSongFingerprint('Tauba Tauba', 'Karan Aujla');
    const fp2 = getSongFingerprint('Tauba Tauba - Remix', 'Karan Aujla');
    const fp3 = getSongFingerprint('Tauba Tauba (From "Bad Newz")', 'Karan Aujla');

    expect(fp1).toBe(fp2);
    expect(fp2).toBe(fp3);
  });

  it('eliminates duplicates and remixes from recommendation candidates', () => {
    const seedTitle = 'Tauba Tauba';
    const seedArtist = 'Karan Aujla';

    const candidates = [
      { id: '1', title: 'Tauba Tauba - Remix', artists: [{ name: 'Karan Aujla' }] },
      { id: '2', title: 'Tauba Tauba (Slowed)', artists: [{ name: 'Karan Aujla' }] },
      { id: '3', title: 'Players', artists: [{ name: 'Karan Aujla' }] },
      { id: '4', title: 'Softly', artists: [{ name: 'Karan Aujla' }] },
      { id: '5', title: 'Players - Acoustic', artists: [{ name: 'Karan Aujla' }] }, // duplicate of Players
      { id: '6', title: 'White Brown Black', artists: [{ name: 'Karan Aujla' }] },
    ];

    const deduped = deduplicateRelatedSongs(
      candidates,
      seedTitle,
      seedArtist,
      new Set(['seed-id']),
      (s) => s.id
    );

    expect(deduped.length).toBe(3);
    expect(deduped.map((s) => s.title)).toEqual(['Players', 'Softly', 'White Brown Black']);
  });
});

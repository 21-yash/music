import { describe, it, expect } from 'vitest';
import { parseLrc } from '../../src/services/lyricsService';

describe('Lyrics Service LRC Parser', () => {
  it('correctly parses standard timestamped LRC lines', () => {
    const lrc = `
[00:04.12] First line of the song
[00:08.50] Second line of the song
[01:15.80] Chorus starts now
    `;

    const parsed = parseLrc(lrc);
    expect(parsed.length).toBe(3);

    expect(parsed[0].timeMs).toBe(4120);
    expect(parsed[0].text).toBe('First line of the song');

    expect(parsed[1].timeMs).toBe(8500);
    expect(parsed[1].text).toBe('Second line of the song');

    expect(parsed[2].timeMs).toBe(75800);
    expect(parsed[2].text).toBe('Chorus starts now');
  });

  it('handles empty lines and lines without timestamps', () => {
    const lrc = `
[ar:Artist Name]
[ti:Song Title]

[00:10.00] Actual lyrics
[00:20.00] Another line
    `;

    const parsed = parseLrc(lrc);
    expect(parsed.length).toBe(2);
    expect(parsed[0].text).toBe('Actual lyrics');
    expect(parsed[1].text).toBe('Another line');
  });
});

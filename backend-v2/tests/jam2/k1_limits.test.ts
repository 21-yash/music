import { describe, it, expect, vi, beforeEach, beforeAll, afterAll } from 'vitest';
import { songSchema, clientCmdSchema } from '../../src/jam2/types';
import { jam2Manager } from '../../src/jam2/manager';
import { registerMusicProvider, getSong } from '../../src/services/musicService';
import { JioSaavnProvider } from '../../src/providers/music/jiosaavn/jiosaavnProvider';
import { resolveSongOrFallback, setCustomSongResolver } from '../../src/jam2/songResolver';
import { initializeSocketIO } from '../../src/websocket';
import { createApp } from '../../src/app';
import http from 'http';
import { io as Client, Socket as ClientSocket } from 'socket.io-client';
import { env } from '../../src/config/env';
import { generateAccessToken } from '../../src/utils/tokens';

describe('Task K1: Schema limits, issue path in error, and per-song batch validation', () => {
  let server: http.Server;
  let serverPort: number;

  beforeAll(async () => {
    registerMusicProvider(new JioSaavnProvider(), true);
    const app = createApp();
    server = http.createServer(app);
    initializeSocketIO(server);
    await new Promise<void>((resolve) => {
      server.listen(0, () => {
        const addr = server.address() as any;
        serverPort = addr.port;
        resolve();
      });
    });

    setCustomSongResolver(async (id, providerId) => {
      return {
        id,
        title: `Song ${id}`,
        artists: [{ id: 'a1', name: 'Artist' }],
        duration: 200,
        imageUrl: null,
        year: '2024',
        language: 'en',
        hasLyrics: false,
        playCount: 0,
        label: '',
        streamRef: '',
        providerId: providerId || 'jiosaavn',
      };
    });
  });

  afterAll(async () => {
    setCustomSongResolver(null);
    jam2Manager.stop();
    if (typeof (server as any).closeAllConnections === 'function') {
      (server as any).closeAllConnections();
    }
    await new Promise<void>((resolve) => server.close(() => resolve()));
  });

  beforeEach(() => {
    vi.restoreAllMocks();
    jam2Manager.clearCreateRateLimits();
  });

  it('song with 10 artists accepted through songSchema', () => {
    const tenArtists = Array.from({ length: 10 }, (_, i) => ({
      id: `artist_id_${i}_${'a'.repeat(50)}`,
      name: `Artist Name ${i} Long Descriptive Name`,
      imageUrl: 'https://saavncdn.com/artist.jpg',
    }));

    const song = {
      id: 'song_test_id_10_artists',
      title: 'A Song With Many Artists Collaborating',
      artists: tenArtists,
      duration: 240,
      providerId: 'jiosaavn',
    };

    const parsed = songSchema.safeParse(song);
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.artists.length).toBe(10);
    }
  });

  it('song with 38 artists accepted through songSchema (real Bollywood ensemble)', () => {
    const thirtyEightArtists = Array.from({ length: 38 }, (_, i) => ({
      id: `artist_${i}`,
      name: `Artist ${i}`,
    }));

    const song = {
      id: 'ensemble_song_38',
      title: 'Deewangi Deewangi Ensemble',
      artists: thirtyEightArtists,
      duration: 300,
    };

    const parsed = songSchema.safeParse(song);
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.artists.length).toBe(38);
    }
  });

  it('song with 51 artists rejected through songSchema (> max 50)', () => {
    const fiftyOneArtists = Array.from({ length: 51 }, (_, i) => ({
      id: `artist_${i}`,
      name: `Artist ${i}`,
    }));

    const song = {
      id: 'ensemble_song_51',
      title: 'Too Many Artists',
      artists: fiftyOneArtists,
      duration: 300,
    };

    const parsed = songSchema.safeParse(song);
    expect(parsed.success).toBe(false);
  });

  it('artist id of 250 chars accepted in songSchema', () => {
    const song = {
      id: 'song_test_id_250',
      title: 'Valid Song',
      artists: [{ id: 'x'.repeat(250), name: 'Artist X' }],
      duration: 180,
    };
    const parsed = songSchema.safeParse(song);
    expect(parsed.success).toBe(true);
  });

  it('artist id of 400 chars rejected and clientCmdSchema contains field path', () => {
    const cmd = {
      code: 'ABCD',
      type: 'queueAdd',
      song: {
        id: 'valid_id',
        title: 'Song Title',
        artists: [{ id: 'x'.repeat(400), name: 'Artist Long' }],
        duration: 120,
      },
    };

    const parsed = clientCmdSchema.safeParse(cmd);
    expect(parsed.success).toBe(false);
    if (!parsed.success) {
      const issue = parsed.error.issues[0];
      const path = issue?.path?.join('.');
      expect(path).toBe('song.artists.0.id');
      expect(issue.message).toMatch(/300/);
    }
  });

  it('song.id of 350 chars rejected with path in error message', () => {
    const cmd = {
      code: 'ABCD',
      type: 'queueAdd',
      song: {
        id: 's'.repeat(350),
        title: 'Song Title',
        artists: [{ id: 'art1', name: 'Artist 1' }],
        duration: 120,
      },
    };

    const parsed = clientCmdSchema.safeParse(cmd);
    expect(parsed.success).toBe(false);
    if (!parsed.success) {
      const issue = parsed.error.issues[0];
      const path = issue?.path?.join('.');
      expect(path).toBe('song.id');
      expect(issue.message).toMatch(/300/);
    }
  });

  it('real JioSaavn song g6Ky7cGO passes songSchema, resolution, and room queueAdd', async () => {
    const rawSong = await getSong('g6Ky7cGO', 'jiosaavn');
    expect(rawSong).not.toBeNull();
    if (!rawSong) return;

    const parsed = songSchema.safeParse(rawSong);
    expect(parsed.success).toBe(true);

    const resolved = await resolveSongOrFallback(rawSong);
    expect(resolved).not.toBeNull();

    const room = await jam2Manager.createRoom('user_host_k1', 'Host K1');
    const cmd = {
      code: room.code,
      type: 'queueAdd' as const,
      song: rawSong,
    };

    const cmdParsed = clientCmdSchema.safeParse(cmd);
    expect(cmdParsed.success).toBe(true);

    const execRes = await jam2Manager.executeCommand(room.code, cmd, 'user_host_k1');
    expect(execRes).toHaveProperty('room');
    if ('room' in execRes) {
      expect(execRes.room.nowPlaying.item?.song.id).toBe('g6Ky7cGO');
      expect(execRes.room.nowPlaying.item?.song.artists.length).toBe(10);
    }
  });

  it('socket emit invalid command returns formatted message with field path: Invalid command: song.artists.0.id: ...', async () => {
    const token = generateAccessToken({ userId: 'u_test_k1', username: 'Test User K1' });
    const socket = Client(`http://localhost:${serverPort}`, {
      auth: { apiKey: env.APP_API_KEY, token },
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise<void>((resolve) => socket.on('connect', () => resolve()));

    const invalidCmd = {
      code: 'TEST',
      type: 'queueAdd',
      song: {
        id: 'valid_id',
        title: 'Song Title',
        artists: [{ id: 'x'.repeat(400), name: 'Artist Long' }],
        duration: 120,
      },
    };

    const response = await new Promise<any>((resolve) => {
      socket.emit('jam2:cmd', invalidCmd, (res: any) => resolve(res));
    });

    expect(response.ok).toBe(false);
    expect(response.code).toBe('INVALID');
    expect(response.message).toContain('Invalid command: song.artists.0.id:');
    expect(response.message).toMatch(/300/);

    socket.disconnect();
  });

  it('queueAddMany: batch of 3, 1 has 400-char artist id -> 2 songs added, ack skipped=1', async () => {
    const token = generateAccessToken({ userId: 'u_batch_k1', username: 'Batch User' });
    const socket = Client(`http://localhost:${serverPort}`, {
      auth: { apiKey: env.APP_API_KEY, token },
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise<void>((resolve) => socket.on('connect', () => resolve()));

    // Create room first
    const createRes = await new Promise<any>((resolve) => {
      socket.emit('jam2:create', { protocol: 2 }, (res: any) => resolve(res));
    });
    expect(createRes.ok).toBe(true);
    const code = createRes.room.code;

    const validSong1 = {
      id: 'valid_song_1',
      title: 'Valid Song 1',
      artists: [{ id: 'a1', name: 'Artist 1' }],
      duration: 180,
    };
    const invalidSong = {
      id: 'invalid_song_2',
      title: 'Invalid Song 2',
      artists: [{ id: 'x'.repeat(400), name: 'Artist Over Limit' }],
      duration: 200,
    };
    const validSong2 = {
      id: 'valid_song_3',
      title: 'Valid Song 3',
      artists: [{ id: 'a3', name: 'Artist 3' }],
      duration: 210,
    };

    const batchCmd = {
      code,
      type: 'queueAddMany',
      songs: [validSong1, invalidSong, validSong2],
    };

    const ack = await new Promise<any>((resolve) => {
      socket.emit('jam2:cmd', batchCmd, (res: any) => resolve(res));
    });

    expect(ack.ok).toBe(true);
    expect(ack.added).toBe(2);
    expect(ack.skipped).toBe(1);

    socket.disconnect();
  });
});

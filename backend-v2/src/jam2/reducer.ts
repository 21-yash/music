import { randomUUID } from 'crypto';
import {
  Room,
  QueueItem,
  Jam2Command,
  Jam2Error,
  Jam2Event,
  Song,
  getExpectedPosition,
} from './types';
import { checkCommandPermission } from './permissions';
import { getSongFingerprint } from '../utils/musicFingerprint';

const MAX_QUEUE_SIZE = 50;
const MAX_HISTORY_SIZE = 20;

function isSameSong(a: Song, b: Song): boolean {
  if (a.id === b.id) return true;
  const fpA = getSongFingerprint(a.title, a.artists?.[0]?.name);
  const fpB = getSongFingerprint(b.title, b.artists?.[0]?.name);
  return Boolean(fpA && fpB && fpA === fpB);
}

function cloneRoom(room: Room): Room {
  const members = new Map(
    Array.from(room.members.entries()).map(([k, v]) => [
      k,
      { ...v, perms: { ...v.perms } },
    ]),
  );

  return {
    code: room.code,
    version: room.version,
    hostUserId: room.hostUserId,
    createdAt: room.createdAt,
    members,
    defaults: { ...room.defaults },
    queue: room.queue.map((q) => ({ ...q })),
    history: room.history.map((h) => ({ ...h })),
    nowPlaying: {
      ...room.nowPlaying,
      item: room.nowPlaying.item ? { ...room.nowPlaying.item } : null,
    },
  };
}

export function applyCommand(
  room: Room,
  cmd: Jam2Command,
  actorUserId: string,
  now: number,
): { room: Room; events: Jam2Event[] } | { error: Jam2Error } {
  // Check membership unless it's a join/online event
  if (cmd.type !== 'memberOnline') {
    if (!room.members.has(actorUserId) && actorUserId !== 'system') {
      return { error: { code: 'NOT_MEMBER', message: 'You are not a member of this Jam session.' } };
    }
  }

  // Check permissions
  const permCheck = checkCommandPermission(room, actorUserId, cmd);
  if (!permCheck.allowed) {
    return { error: { code: 'FORBIDDEN', message: permCheck.reason || 'Permission denied.' } };
  }

  const nextRoom = cloneRoom(room);
  const events: Jam2Event[] = [];

  switch (cmd.type) {
    case 'play': {
      if (nextRoom.nowPlaying.status === 'paused') {
        nextRoom.nowPlaying.status = 'playing';
        nextRoom.nowPlaying.anchorServerTs = now;
        nextRoom.version++;
        return { room: nextRoom, events };
      }

      if (nextRoom.nowPlaying.status === 'idle') {
        if (nextRoom.queue.length > 0) {
          const nextItem = nextRoom.queue.shift()!;
          nextRoom.nowPlaying = {
            item: nextItem,
            status: 'playing',
            positionMs: 0,
            anchorServerTs: now,
            trackSeq: nextRoom.nowPlaying.trackSeq + 1,
          };
          nextRoom.version++;
          return { room: nextRoom, events };
        }
      }

      // Already playing: no-op
      return { room: nextRoom, events };
    }

    case 'pause': {
      if (nextRoom.nowPlaying.status === 'playing') {
        const currentPos = getExpectedPosition(nextRoom.nowPlaying, now);
        nextRoom.nowPlaying.status = 'paused';
        nextRoom.nowPlaying.positionMs = currentPos;
        nextRoom.nowPlaying.anchorServerTs = now;
        nextRoom.version++;
      }
      return { room: nextRoom, events };
    }

    case 'seek': {
      if (!nextRoom.nowPlaying.item) {
        return { error: { code: 'INVALID', message: 'Nothing is currently playing.' } };
      }
      const durationMs =
        nextRoom.nowPlaying.item.song.duration > 0
          ? nextRoom.nowPlaying.item.song.duration * 1000
          : Number.MAX_SAFE_INTEGER;
      const clampedPos = Math.max(0, Math.min(cmd.positionMs, durationMs));
      nextRoom.nowPlaying.positionMs = clampedPos;
      nextRoom.nowPlaying.anchorServerTs = now;
      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'next': {
      if (nextRoom.nowPlaying.item) {
        nextRoom.history.push(nextRoom.nowPlaying.item);
        if (nextRoom.history.length > MAX_HISTORY_SIZE) {
          nextRoom.history.splice(0, nextRoom.history.length - MAX_HISTORY_SIZE);
        }
      }

      if (nextRoom.queue.length > 0) {
        const nextItem = nextRoom.queue.shift()!;
        nextRoom.nowPlaying = {
          item: nextItem,
          status: 'playing',
          positionMs: 0,
          anchorServerTs: now,
          trackSeq: nextRoom.nowPlaying.trackSeq + 1,
        };
      } else {
        nextRoom.nowPlaying = {
          item: null,
          status: 'idle',
          positionMs: 0,
          anchorServerTs: now,
          trackSeq: nextRoom.nowPlaying.trackSeq,
        };
      }
      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'prev': {
      const currentPos = getExpectedPosition(nextRoom.nowPlaying, now);
      if (currentPos > 3000 || nextRoom.history.length === 0) {
        // If > 3 seconds in, or no history: seek to beginning
        nextRoom.nowPlaying.positionMs = 0;
        nextRoom.nowPlaying.anchorServerTs = now;
        nextRoom.version++;
        return { room: nextRoom, events };
      }

      // Pop from history
      const popped = nextRoom.history.pop()!;
      if (nextRoom.nowPlaying.item) {
        nextRoom.queue.unshift(nextRoom.nowPlaying.item);
      }

      nextRoom.nowPlaying = {
        item: popped,
        status: 'playing',
        positionMs: 0,
        anchorServerTs: now,
        trackSeq: nextRoom.nowPlaying.trackSeq + 1,
      };
      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'playItem': {
      const idx = nextRoom.queue.findIndex((q) => q.qid === cmd.qid);
      if (idx === -1) {
        return { error: { code: 'NOT_FOUND', message: 'Song not found in queue.' } };
      }

      const targetItem = nextRoom.queue[idx];
      const skippedItems = nextRoom.queue.slice(0, idx);

      if (nextRoom.nowPlaying.item) {
        nextRoom.history.push(nextRoom.nowPlaying.item);
      }
      for (const skipped of skippedItems) {
        nextRoom.history.push(skipped);
      }
      if (nextRoom.history.length > MAX_HISTORY_SIZE) {
        nextRoom.history.splice(0, nextRoom.history.length - MAX_HISTORY_SIZE);
      }

      nextRoom.queue = nextRoom.queue.slice(idx + 1);
      nextRoom.nowPlaying = {
        item: targetItem,
        status: 'playing',
        positionMs: 0,
        anchorServerTs: now,
        trackSeq: nextRoom.nowPlaying.trackSeq + 1,
      };
      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'trackEnded': {
      // Ignore if trackSeq does not match current or status is not playing
      if (
        cmd.trackSeq !== nextRoom.nowPlaying.trackSeq ||
        nextRoom.nowPlaying.status !== 'playing'
      ) {
        return { room: nextRoom, events: [] };
      }

      // Advances exactly like next
      if (nextRoom.nowPlaying.item) {
        nextRoom.history.push(nextRoom.nowPlaying.item);
        if (nextRoom.history.length > MAX_HISTORY_SIZE) {
          nextRoom.history.splice(0, nextRoom.history.length - MAX_HISTORY_SIZE);
        }
      }

      if (nextRoom.queue.length > 0) {
        const nextItem = nextRoom.queue.shift()!;
        nextRoom.nowPlaying = {
          item: nextItem,
          status: 'playing',
          positionMs: 0,
          anchorServerTs: now,
          trackSeq: nextRoom.nowPlaying.trackSeq + 1,
        };
      } else {
        nextRoom.nowPlaying = {
          item: null,
          status: 'idle',
          positionMs: 0,
          anchorServerTs: now,
          trackSeq: nextRoom.nowPlaying.trackSeq,
        };
      }
      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'queueAdd': {
      const song = cmd.song;

      // If the song was queued as a recommendation, remove that recommendation item
      const recIdx = nextRoom.queue.findIndex(
        (q) => q.recommended && isSameSong(q.song, song),
      );
      if (recIdx !== -1) {
        nextRoom.queue.splice(recIdx, 1);
      }

      const newItem: QueueItem = {
        qid: randomUUID(),
        song,
        addedByUserId: actorUserId,
        addedAt: now,
        recommended: false,
      };

      // Auto-start if room is idle
      if (nextRoom.nowPlaying.status === 'idle') {
        nextRoom.nowPlaying = {
          item: newItem,
          status: 'playing',
          positionMs: 0,
          anchorServerTs: now,
          trackSeq: nextRoom.nowPlaying.trackSeq + 1,
        };
        nextRoom.version++;
        return { room: nextRoom, events };
      }

      // Cap at 50 including recommended
      if (nextRoom.queue.length >= MAX_QUEUE_SIZE) {
        // Evict last recommended item if present
        let lastRecIdx = -1;
        for (let i = nextRoom.queue.length - 1; i >= 0; i--) {
          if (nextRoom.queue[i].recommended) {
            lastRecIdx = i;
            break;
          }
        }

        if (lastRecIdx !== -1) {
          nextRoom.queue.splice(lastRecIdx, 1);
        } else {
          return { error: { code: 'QUEUE_FULL', message: 'Queue is full (max 50 songs).' } };
        }
      }

      // Insert: playNext goes to front of user section; else end of user section (before recommended)
      const firstRecIdx = nextRoom.queue.findIndex((q) => q.recommended);
      if (cmd.playNext) {
        nextRoom.queue.unshift(newItem);
      } else {
        if (firstRecIdx === -1) {
          nextRoom.queue.push(newItem);
        } else {
          nextRoom.queue.splice(firstRecIdx, 0, newItem);
        }
      }

      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'queueAddMany': {
      let addedCount = 0;

      for (const song of cmd.songs) {
        // If the song was queued as a recommendation, remove that recommendation item
        const recIdx = nextRoom.queue.findIndex(
          (q) => q.recommended && isSameSong(q.song, song),
        );
        if (recIdx !== -1) {
          nextRoom.queue.splice(recIdx, 1);
        }

        const newItem: QueueItem = {
          qid: randomUUID(),
          song,
          addedByUserId: actorUserId,
          addedAt: now,
          recommended: false,
        };

        // Auto-start if idle
        if (nextRoom.nowPlaying.status === 'idle') {
          nextRoom.nowPlaying = {
            item: newItem,
            status: 'playing',
            positionMs: 0,
            anchorServerTs: now,
            trackSeq: nextRoom.nowPlaying.trackSeq + 1,
          };
          addedCount++;
          continue;
        }

        // Cap at 50
        if (nextRoom.queue.length >= MAX_QUEUE_SIZE) {
          let lastRecIdx = -1;
          for (let i = nextRoom.queue.length - 1; i >= 0; i--) {
            if (nextRoom.queue[i].recommended) {
              lastRecIdx = i;
              break;
            }
          }
          if (lastRecIdx !== -1) {
            nextRoom.queue.splice(lastRecIdx, 1);
          } else {
            // Cannot add further songs
            break;
          }
        }

        const firstRecIdx = nextRoom.queue.findIndex((q) => q.recommended);
        if (firstRecIdx === -1) {
          nextRoom.queue.push(newItem);
        } else {
          nextRoom.queue.splice(firstRecIdx, 0, newItem);
        }
        addedCount++;
      }

      if (addedCount === 0) {
        return { error: { code: 'QUEUE_FULL', message: 'Queue is full (max 50 songs).' } };
      }

      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'promote': {
      const idx = nextRoom.queue.findIndex((q) => q.qid === cmd.qid);
      if (idx === -1) {
        return { error: { code: 'NOT_FOUND', message: 'Song not found in queue.' } };
      }

      const item = nextRoom.queue[idx];
      if (!item.recommended) {
        return { error: { code: 'INVALID', message: 'Song is already in the user queue.' } };
      }

      nextRoom.queue.splice(idx, 1);
      const promoted: QueueItem = {
        ...item,
        recommended: false,
        addedByUserId: actorUserId,
        addedAt: now,
      };

      // Insert at the end of the user section (before any remaining recommended items)
      const firstRecIdx = nextRoom.queue.findIndex((q) => q.recommended);
      if (firstRecIdx === -1) {
        nextRoom.queue.push(promoted);
      } else {
        nextRoom.queue.splice(firstRecIdx, 0, promoted);
      }

      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'queueRemove': {
      const idx = nextRoom.queue.findIndex((q) => q.qid === cmd.qid);
      if (idx === -1) {
        return { error: { code: 'NOT_FOUND', message: 'Song not found in queue.' } };
      }

      nextRoom.queue.splice(idx, 1);
      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'queueMove': {
      const fromIndex = nextRoom.queue.findIndex((q) => q.qid === cmd.qid);
      if (fromIndex === -1) {
        return { error: { code: 'NOT_FOUND', message: 'Song not found in queue.' } };
      }

      const userSectionCount = nextRoom.queue.filter((q) => !q.recommended).length;
      if (fromIndex >= userSectionCount) {
        return { error: { code: 'INVALID', message: 'Cannot reorder recommended songs.' } };
      }
      if (cmd.toIndex < 0 || cmd.toIndex >= userSectionCount) {
        return { error: { code: 'INVALID', message: 'Target index must be within the user-added queue section.' } };
      }

      const [moved] = nextRoom.queue.splice(fromIndex, 1);
      nextRoom.queue.splice(cmd.toIndex, 0, moved);
      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'setMemberPerms': {
      const member = nextRoom.members.get(cmd.userId);
      if (!member) {
        return { error: { code: 'NOT_FOUND', message: 'Member not found.' } };
      }

      member.perms = { ...member.perms, ...cmd.perms };
      nextRoom.version++;
      events.push({ kind: 'perm_changed', userId: cmd.userId, perms: member.perms });
      return { room: nextRoom, events };
    }

    case 'setDefaults': {
      nextRoom.defaults = { ...nextRoom.defaults, ...cmd.perms };
      nextRoom.version++;
      return { room: nextRoom, events };
    }

    case 'kick': {
      if (cmd.userId === nextRoom.hostUserId || cmd.userId === actorUserId) {
        return { error: { code: 'INVALID', message: 'Cannot kick the host or yourself.' } };
      }

      const member = nextRoom.members.get(cmd.userId);
      if (!member) {
        return { error: { code: 'NOT_FOUND', message: 'Member not found.' } };
      }

      nextRoom.members.delete(cmd.userId);
      nextRoom.version++;
      events.push({ kind: 'kicked', userId: cmd.userId, username: member.username });
      return { room: nextRoom, events };
    }

    case 'transferHost': {
      const target = nextRoom.members.get(cmd.userId);
      if (!target) {
        return { error: { code: 'NOT_FOUND', message: 'Member not found.' } };
      }
      if (cmd.userId === nextRoom.hostUserId) {
        return { error: { code: 'INVALID', message: 'This member is already the host.' } };
      }

      nextRoom.hostUserId = cmd.userId;
      nextRoom.version++;
      events.push({ kind: 'host_changed', userId: cmd.userId, username: target.username });
      return { room: nextRoom, events };
    }

    // ─── Internal Commands ─────────────────────────────────────────────
    case 'recsAdded': {
      // Stale track recommendations are ignored
      if (cmd.forTrackSeq !== nextRoom.nowPlaying.trackSeq) {
        return { room: nextRoom, events: [] };
      }

      const existingIds = new Set<string>();
      if (nextRoom.nowPlaying.item) {
        existingIds.add(nextRoom.nowPlaying.item.song.id);
      }
      for (const q of nextRoom.queue) {
        existingIds.add(q.song.id);
      }
      for (const h of nextRoom.history) {
        existingIds.add(h.song.id);
      }

      const seenFp = new Set<string>();
      if (nextRoom.nowPlaying.item) {
        seenFp.add(
          getSongFingerprint(
            nextRoom.nowPlaying.item.song.title,
            nextRoom.nowPlaying.item.song.artists?.[0]?.name,
          ),
        );
      }
      for (const q of nextRoom.queue) {
        seenFp.add(getSongFingerprint(q.song.title, q.song.artists?.[0]?.name));
      }
      for (const h of nextRoom.history) {
        seenFp.add(getSongFingerprint(h.song.title, h.song.artists?.[0]?.name));
      }

      let added = 0;
      for (const song of cmd.songs) {
        if (added >= 10 || nextRoom.queue.length >= MAX_QUEUE_SIZE) break;
        if (existingIds.has(song.id)) continue;

        const fp = getSongFingerprint(song.title, song.artists?.[0]?.name);
        if (fp && seenFp.has(fp)) continue;

        if (fp) seenFp.add(fp);
        existingIds.add(song.id);

        nextRoom.queue.push({
          qid: randomUUID(),
          song,
          addedByUserId: null,
          addedAt: now,
          recommended: true,
        });
        added++;
      }

      if (added > 0) {
        nextRoom.version++;
      }
      return { room: nextRoom, events };
    }

    case 'hostHandover': {
      const newHost = nextRoom.members.get(cmd.newHostUserId);
      if (newHost) {
        nextRoom.hostUserId = cmd.newHostUserId;
        nextRoom.version++;
        events.push({
          kind: 'host_changed',
          userId: cmd.newHostUserId,
          username: newHost.username,
        });
      }
      return { room: nextRoom, events };
    }

    case 'memberOnline': {
      const existing = nextRoom.members.get(cmd.userId);
      if (existing) {
        existing.online = true;
        existing.lastSeenAt = now;
        if (cmd.avatarUrl) existing.avatarUrl = cmd.avatarUrl;
        nextRoom.version++;
      } else {
        nextRoom.members.set(cmd.userId, {
          userId: cmd.userId,
          username: cmd.username,
          avatarUrl: cmd.avatarUrl,
          joinedAt: now,
          online: true,
          lastSeenAt: now,
          perms: { ...nextRoom.defaults },
        });
        nextRoom.version++;
        events.push({ kind: 'member_joined', userId: cmd.userId, username: cmd.username });
      }
      return { room: nextRoom, events };
    }

    case 'memberOffline': {
      const member = nextRoom.members.get(cmd.userId);
      if (member) {
        member.online = false;
        member.lastSeenAt = now;
        nextRoom.version++;
      }
      return { room: nextRoom, events };
    }

    case 'memberLeave': {
      const member = nextRoom.members.get(cmd.userId);
      if (member) {
        nextRoom.members.delete(cmd.userId);
        nextRoom.version++;
        events.push({ kind: 'member_left', userId: cmd.userId, username: member.username });
      }
      return { room: nextRoom, events };
    }

    default:
      return { error: { code: 'INVALID', message: 'Unknown command type' } };
  }
}

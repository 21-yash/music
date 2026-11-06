import { Room, Jam2Command } from './types';

export function checkCommandPermission(
  room: Room,
  actorUserId: string,
  cmd: Jam2Command,
): { allowed: boolean; reason?: string } {
  // System-level commands and timer actions are always allowed
  if (actorUserId === 'system') {
    return { allowed: true };
  }

  // Internal commands are system-level and always allowed
  if (
    cmd.type === 'recsAdded' ||
    cmd.type === 'hostHandover' ||
    cmd.type === 'memberOnline' ||
    cmd.type === 'memberOffline' ||
    cmd.type === 'memberLeave'
  ) {
    return { allowed: true };
  }

  const member = room.members.get(actorUserId);
  if (!member) {
    return { allowed: false, reason: 'You are not a member of this Jam session.' };
  }

  const isHost = room.hostUserId === actorUserId;

  // Host-only commands
  if (
    cmd.type === 'setMemberPerms' ||
    cmd.type === 'setDefaults' ||
    cmd.type === 'kick' ||
    cmd.type === 'transferHost'
  ) {
    if (!isHost) {
      return { allowed: false, reason: 'Only the host can perform this action.' };
    }
    return { allowed: true };
  }

  // Host can perform all room commands
  if (isHost) {
    return { allowed: true };
  }

  // Playback commands
  if (
    cmd.type === 'play' ||
    cmd.type === 'pause' ||
    cmd.type === 'seek' ||
    cmd.type === 'next' ||
    cmd.type === 'prev' ||
    cmd.type === 'playItem'
  ) {
    if (!member.perms.playback) {
      return { allowed: false, reason: 'You do not have permission to control playback.' };
    }
    return { allowed: true };
  }

  // Queue add commands
  if (cmd.type === 'queueAdd' || cmd.type === 'queueAddMany' || cmd.type === 'promote') {
    if (!member.perms.queueAdd) {
      return { allowed: false, reason: 'You do not have permission to add songs to the queue.' };
    }
    return { allowed: true };
  }

  // Track ended report (any member can send)
  if (cmd.type === 'trackEnded') {
    return { allowed: true };
  }

  // Queue move (reorder)
  if (cmd.type === 'queueMove') {
    if (!member.perms.queueManage) {
      return { allowed: false, reason: 'Only the host or members with queue management permission can reorder songs.' };
    }
    return { allowed: true };
  }

  // Queue remove: own item vs recommended vs others'
  if (cmd.type === 'queueRemove') {
    const item = room.queue.find((q) => q.qid === cmd.qid);
    if (!item) {
      // Item not found: let reducer return NOT_FOUND error
      return { allowed: true };
    }

    if (item.recommended) {
      // Dismissing a recommendation requires queueAdd or queueManage
      if (!member.perms.queueAdd && !member.perms.queueManage) {
        return { allowed: false, reason: 'You do not have permission to manage recommended songs.' };
      }
      return { allowed: true };
    }

    if (item.addedByUserId === actorUserId) {
      // User can always remove their own song
      return { allowed: true };
    }

    // Removing someone else's song requires queueManage
    if (!member.perms.queueManage) {
      return { allowed: false, reason: 'Only the host or members with queue management permission can remove others\' songs.' };
    }
    return { allowed: true };
  }

  return { allowed: false, reason: 'Permission denied.' };
}

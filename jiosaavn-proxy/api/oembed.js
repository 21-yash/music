function upgradeImageUrl(url) {
  if (!url) return '';
  return url
    .replace(/([-_/])(?:50x50|150x150|250x250|350x350)\b/g, '$1500x500')
    .replace(/\b(?:50x50|150x150|250x250|350x350)\b/g, '500x500');
}

function getThumbnailUrl(url) {
  if (!url) return '';
  if (url.includes('unsplash.com')) {
    return url.replace(/w=\d+/, 'w=250').replace(/h=\d+/, 'h=250');
  }
  return url
    .replace(/([-_/])(?:50x50|150x150|350x350|500x500)\b/g, '$1250x250')
    .replace(/\b(?:50x50|150x150|350x350|500x500)\b/g, '250x250');
}

export default async function handler(req, res) {
  let { type = 'track', id = '', url = '' } = req.query;

  // Extract type & id from url parameter if passed (e.g. oembed standard: ?url=https://21yash.online/track/xyz)
  if (url) {
    const match = url.match(/\/(track|playlist|song)\/([a-zA-Z0-9_-]+)/i);
    if (match) {
      type = match[1] === 'song' ? 'track' : match[1];
      id = match[2];
    }
  }

  let title = type === 'playlist' ? 'Featured Playlist' : 'Track Preview';
  let author = type === 'playlist' ? 'Synq Playlist' : 'Synq';
  let imageUrl = 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80';

  if (id) {
    try {
      if (type === 'playlist') {
        let resolved = false;
        try {
          const beRes = await fetch(`https://api.21yash.online/api/v1/music/playlists/${encodeURIComponent(id)}`, {
            headers: { 'Accept': 'application/json' },
            signal: AbortSignal.timeout(3500)
          });
          if (beRes.ok) {
            const beJson = await beRes.json();
            if (beJson.success && beJson.data && (beJson.data.title || (beJson.data.songs && beJson.data.songs.length > 0))) {
              title = beJson.data.title || title;
              const count = beJson.data.songCount || (Array.isArray(beJson.data.songs) ? beJson.data.songs.length : 'Multiple');
              author = `Playlist • ${count} songs`;
              if (beJson.data.imageUrl) {
                imageUrl = upgradeImageUrl(beJson.data.imageUrl);
              }
              resolved = true;
            }
          }
        } catch {}

        if (!resolved) {
          const apiUrl = `https://www.jiosaavn.com/api.php?__call=playlist.getDetails&listid=${encodeURIComponent(id)}&_format=json&cc=in&_marker=0`;
          const response = await fetch(apiUrl, {
            headers: { 'User-Agent': 'Mozilla/5.0' },
            signal: AbortSignal.timeout(3500)
          });
          if (response.ok) {
            const data = await response.json();
            if (data && (data.listname || data.title)) {
              title = data.listname || data.title || title;
              const count = data.list_count || (Array.isArray(data.songs) ? data.songs.length : 'Multiple');
              author = `Playlist • ${count} songs`;
              if (data.image) {
                imageUrl = upgradeImageUrl(data.image);
              }
            }
          }
        }
      } else {
        const apiUrl = `https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=${encodeURIComponent(id)}&_format=json`;
        const response = await fetch(apiUrl, {
          headers: { 'User-Agent': 'Mozilla/5.0' },
          signal: AbortSignal.timeout(3500)
        });
        if (response.ok) {
          const raw = await response.json();
          const songData = raw[id] || Object.values(raw)[0];
          if (songData) {
            title = songData.song || songData.title || title;
            author = songData.primary_artists || songData.singers || author;
            if (songData.image) {
              imageUrl = upgradeImageUrl(songData.image);
            }
          }
        }
      }
    } catch {
      // Fallback cleanly to default metadata
    }
  }

  // Clean strings from HTML entities
  title = cleanEntity(title);
  author = cleanEntity(author);

  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 's-maxage=86400, stale-while-revalidate');

  res.status(200).json({
    version: '1.0',
    type: 'link',
    provider_name: 'Synq',
    provider_url: 'https://21yash.online',
    title: title,
    thumbnail_url: getThumbnailUrl(imageUrl),
    thumbnail_width: 250,
    thumbnail_height: 250
  });
}

function cleanEntity(str) {
  if (!str) return '';
  return String(str)
    .replace(/&quot;/g, '"')
    .replace(/&#039;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>');
}

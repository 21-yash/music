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
        const apiUrl = `https://www.jiosaavn.com/api.php?__call=playlist.getDetails&listid=${encodeURIComponent(id)}&_format=json`;
        const response = await fetch(apiUrl, {
          headers: { 'User-Agent': 'Mozilla/5.0' },
          signal: AbortSignal.timeout(3500)
        });
        if (response.ok) {
          const data = await response.json();
          title = data.listname || data.title || title;
          const count = data.list_count || (Array.isArray(data.songs) ? data.songs.length : 'Multiple');
          author = `Playlist • ${count} songs`;
          if (data.image) {
            imageUrl = data.image.replace(/150x150/, '500x500');
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
              imageUrl = songData.image.replace(/150x150/, '500x500');
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
    thumbnail_url: imageUrl,
    thumbnail_width: 500,
    thumbnail_height: 500
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

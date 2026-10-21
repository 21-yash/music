export default async function handler(req, res) {
  let { type = 'track', id = '', title: qTitle, artist: qArtist, img: qImg, songs: qSongs } = req.query;

  // Normalize type
  if (type === 'song') type = 'track';

  let title = qTitle || (type === 'playlist' ? 'Featured Playlist' : 'Track Preview');
  let artist = qArtist || (type === 'playlist' ? 'Curated Collection' : 'MusicSports');
  let album = '';
  let year = '';
  let imageUrl = qImg || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80';
  let audioUrl = '';
  let durationSeconds = 0;
  let playlistSongs = [];
  let playlistCount = 0;
  let creator = 'MusicSports';

  // ─── Server-side metadata resolution from JioSaavn API ───────────
  if (id) {
    try {
      if (type === 'playlist') {
        const apiUrl = `https://www.jiosaavn.com/api.php?__call=playlist.getDetails&listid=${encodeURIComponent(id)}&_format=json`;
        const response = await fetch(apiUrl, {
          headers: { 'User-Agent': 'Mozilla/5.0' },
          signal: AbortSignal.timeout(4000)
        });
        if (response.ok) {
          const data = await response.json();
          title = cleanEntity(data.listname || data.title || title);
          creator = cleanEntity(data.firstname || 'MusicSports');
          artist = creator;
          if (data.image) {
            imageUrl = data.image.replace(/150x150/, '500x500');
          }
          const rawSongs = Array.isArray(data.songs)
            ? data.songs
            : (Array.isArray(data.list) ? data.list : Object.values(data).filter(v => v && typeof v === 'object' && (v.song || v.title)));

          playlistCount = parseInt(data.list_count || rawSongs.length || '0', 10);
          playlistSongs = rawSongs.slice(0, 15).map(s => ({
            id: s.id,
            title: cleanEntity(s.song || s.title || 'Untitled Track'),
            artist: cleanEntity(s.primary_artists || s.singers || 'Unknown Artist'),
            duration: parseInt(s.duration || '0', 10),
            previewUrl: s.media_preview_url ? s.media_preview_url.replace('http://', 'https://') : '',
            image: s.image ? s.image.replace(/150x150/, '250x250') : ''
          }));
        }
      } else {
        const apiUrl = `https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=${encodeURIComponent(id)}&_format=json`;
        const response = await fetch(apiUrl, {
          headers: { 'User-Agent': 'Mozilla/5.0' },
          signal: AbortSignal.timeout(4000)
        });
        if (response.ok) {
          const raw = await response.json();
          const songData = raw[id] || Object.values(raw)[0];
          if (songData) {
            title = cleanEntity(songData.song || songData.title || title);
            artist = cleanEntity(songData.primary_artists || songData.singers || artist);
            album = cleanEntity(songData.album || '');
            year = songData.year || '';
            if (songData.image) {
              imageUrl = songData.image.replace(/150x150/, '500x500');
            }
            if (songData.media_preview_url) {
              audioUrl = songData.media_preview_url.replace('http://', 'https://');
            }
            if (songData.duration) {
              durationSeconds = parseInt(songData.duration, 10) || 0;
            }
          }
        }
      }
    } catch {
      // Fallback cleanly to query params
    }
  }

  // HTML sanitization for safe output
  const cleanTitle = escapeHtml(title);
  const cleanArtist = escapeHtml(artist);
  const cleanAlbum = escapeHtml(album);
  const cleanImage = escapeHtml(imageUrl);
  const cleanAudio = escapeHtml(audioUrl);
  const cleanId = encodeURIComponent(id || 'sample');

  const domain = '21yash.online';
  const canonicalUrl = `https://${domain}/${type}/${cleanId}`;
  const deepLink = `musicsports://${type}/${cleanId}`;

  // Clean Spotify-style description (NO quotes, no spam sentences)
  const metaDescription = type === 'playlist'
    ? `${playlistCount || (playlistSongs.length || 'Curated')} songs • Playlist by ${creator}`
    : `${cleanArtist} • ${cleanAlbum || 'Song'}`;

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>${cleanTitle} · ${cleanArtist} | MusicSports</title>
  <meta name="description" content="${metaDescription}">

  <!-- Open Graph / WhatsApp / Discord / Telegram -->
  <meta property="og:site_name" content="MusicSports">
  <meta property="og:type" content="${type === 'playlist' ? 'music.playlist' : 'music.song'}">
  <meta property="og:title" content="${cleanTitle}">
  <meta property="og:description" content="${metaDescription}">
  <meta property="og:image" content="${cleanImage}">
  <meta property="og:image:width" content="500">
  <meta property="og:image:height" content="500">
  <meta property="og:url" content="${canonicalUrl}">

  <!-- Twitter / Discord Horizontal Compact Card -->
  <meta name="twitter:card" content="summary">
  <meta name="twitter:title" content="${cleanTitle}">
  <meta name="twitter:description" content="${metaDescription}">
  <meta name="twitter:image" content="${cleanImage}">

  <!-- Accent Color (Discord embed border & mobile browser bar) -->
  <meta name="theme-color" content="#EC4899">

  <!-- oEmbed Discovery -->
  <link rel="alternate" type="application/json+oembed" href="https://${domain}/api/oembed?type=${type}&id=${cleanId}" title="${cleanTitle}">

  <!-- Google Fonts: Outfit & Inter -->
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">

  <style>
    :root {
      --bg-dark: #0D0C13;
      --card-bg: #15131C;
      --card-border: rgba(255, 255, 255, 0.08);
      --pink-primary: #EC4899;
      --pink-hover: #DB2777;
      --pink-glow: rgba(236, 72, 153, 0.35);
      --text-white: #FFFFFF;
      --text-gray: #A1A1AA;
      --text-dim: #71717A;
      --item-hover: rgba(255, 255, 255, 0.06);
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      -webkit-tap-highlight-color: transparent;
    }

    body {
      background-color: var(--bg-dark);
      color: var(--text-white);
      font-family: 'Outfit', 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      padding: 24px 16px 48px;
      overflow-x: hidden;
      position: relative;
    }

    /* Ambient Subtle Blur */
    .ambient-glow {
      position: fixed;
      top: 15%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 440px;
      height: 440px;
      background: radial-gradient(circle, var(--pink-glow) 0%, rgba(99, 102, 241, 0.12) 50%, transparent 75%);
      filter: blur(100px);
      z-index: 0;
      pointer-events: none;
    }

    /* Minimal Navbar */
    .nav-container {
      width: 100%;
      max-width: 580px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 20px;
      z-index: 10;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      color: #fff;
    }

    .brand-logo {
      width: 32px;
      height: 32px;
      border-radius: 9px;
      background: linear-gradient(135deg, #EC4899, #6366F1);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 16px;
      box-shadow: 0 4px 12px var(--pink-glow);
    }

    .brand-name {
      font-weight: 700;
      font-size: 17px;
      letter-spacing: -0.3px;
    }

    .brand-name span {
      background: linear-gradient(135deg, #EC4899, #F43F5E);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .nav-app-btn {
      padding: 7px 15px;
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid var(--card-border);
      color: #fff;
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s ease;
    }

    .nav-app-btn:hover {
      background: rgba(255, 255, 255, 0.15);
      border-color: rgba(255, 255, 255, 0.2);
    }

    /* Spotify-Style Player Card */
    .player-card {
      width: 100%;
      max-width: 580px;
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 20px;
      padding: 22px;
      z-index: 10;
      box-shadow: 0 16px 40px rgba(0, 0, 0, 0.55);
      display: flex;
      flex-direction: column;
      gap: 18px;
    }

    /* Horizontal Track Hero (Spotify style) */
    .track-hero {
      display: flex;
      align-items: center;
      gap: 18px;
    }

    .hero-art-wrapper {
      position: relative;
      flex-shrink: 0;
      width: 130px;
      height: 130px;
      border-radius: 14px;
      overflow: hidden;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.08);
    }

    .hero-art {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }

    .hero-meta {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      justify-content: center;
    }

    .type-pill {
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--text-dim);
      margin-bottom: 4px;
    }

    .track-title {
      font-size: 21px;
      font-weight: 700;
      color: #fff;
      line-height: 1.25;
      margin-bottom: 4px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .track-artist {
      font-size: 14px;
      color: var(--text-gray);
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      margin-bottom: 6px;
    }

    .meta-tags {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .preview-pill {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.1);
      color: #E4E4E7;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    /* Scrubber Row */
    .scrubber-section {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .progress-bar {
      width: 100%;
      height: 5px;
      background: rgba(255, 255, 255, 0.12);
      border-radius: 4px;
      cursor: pointer;
      position: relative;
      overflow: hidden;
    }

    .progress-fill {
      height: 100%;
      width: 0%;
      background: linear-gradient(90deg, #EC4899, #F43F5E);
      border-radius: 4px;
      transition: width 0.1s linear;
    }

    .time-row {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--text-dim);
      font-family: 'Inter', sans-serif;
    }

    /* Controls & Equalizer Row */
    .controls-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 4px;
    }

    .left-controls {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .play-btn {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      background: var(--pink-primary);
      border: none;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      box-shadow: 0 4px 16px var(--pink-glow);
      transition: transform 0.18s ease, background-color 0.18s ease;
    }

    .play-btn:hover {
      transform: scale(1.06);
      background: var(--pink-hover);
    }

    .play-btn:active {
      transform: scale(0.96);
    }

    .play-btn svg {
      width: 22px;
      height: 22px;
      fill: currentColor;
    }

    /* Animated Equalizer */
    .eq-bars {
      display: flex;
      align-items: flex-end;
      gap: 3px;
      height: 18px;
    }

    .eq-bar {
      width: 3px;
      background: var(--pink-primary);
      border-radius: 2px;
      height: 4px;
    }

    .eq-bar.active {
      animation: eqDance 0.8s ease-in-out infinite alternate;
    }

    .eq-bar:nth-child(1) { animation-duration: 0.4s; }
    .eq-bar:nth-child(2) { animation-duration: 0.28s; height: 8px; }
    .eq-bar:nth-child(3) { animation-duration: 0.45s; }

    @keyframes eqDance {
      0% { height: 3px; }
      100% { height: 18px; }
    }

    /* CTA Buttons */
    .cta-row {
      display: flex;
      gap: 10px;
    }

    .btn-open-app {
      flex: 1;
      padding: 12px 18px;
      border-radius: 14px;
      background: linear-gradient(135deg, #EC4899, #F43F5E);
      border: none;
      color: #fff;
      font-size: 14px;
      font-weight: 700;
      text-decoration: none;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      box-shadow: 0 4px 16px var(--pink-glow);
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .btn-open-app:hover {
      box-shadow: 0 6px 22px rgba(236, 72, 153, 0.55);
      transform: translateY(-1px);
    }

    .btn-share {
      padding: 12px 16px;
      border-radius: 14px;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid var(--card-border);
      color: #fff;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      transition: all 0.2s ease;
    }

    .btn-share:hover {
      background: rgba(255, 255, 255, 0.14);
    }

    /* Playlist Tracklist (Exact Spotify Style) */
    .tracklist-container {
      margin-top: 10px;
      border-top: 1px solid var(--card-border);
      padding-top: 14px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .tracklist-header {
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: var(--text-dim);
      padding: 4px 10px;
      margin-bottom: 4px;
    }

    .track-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px;
      border-radius: 10px;
      transition: background 0.15s ease;
      cursor: pointer;
      text-decoration: none;
      color: inherit;
    }

    .track-row:hover, .track-row.active {
      background: var(--item-hover);
    }

    .track-left {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 0;
      flex: 1;
    }

    .track-num {
      width: 20px;
      font-size: 13px;
      color: var(--text-dim);
      font-weight: 600;
      text-align: center;
      flex-shrink: 0;
    }

    .track-text {
      min-width: 0;
      flex: 1;
    }

    .row-title {
      font-size: 14px;
      font-weight: 600;
      color: #fff;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .row-artist {
      font-size: 12px;
      color: var(--text-gray);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .track-duration {
      font-size: 12px;
      color: var(--text-dim);
      font-family: 'Inter', sans-serif;
      margin-left: 12px;
      flex-shrink: 0;
    }

    /* Footer Cricket Teaser */
    .cricket-footer {
      width: 100%;
      max-width: 580px;
      margin-top: 18px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 12px 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      z-index: 10;
    }

    .cricket-footer-text {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 13px;
      color: var(--text-gray);
    }

    .cricket-footer-text strong {
      color: #fff;
    }

    /* Toast */
    #toast {
      position: fixed;
      bottom: 24px;
      left: 50%;
      transform: translateX(-50%) translateY(80px);
      background: #27272A;
      color: #fff;
      padding: 10px 20px;
      border-radius: 20px;
      font-size: 13px;
      font-weight: 600;
      border: 1px solid rgba(255, 255, 255, 0.15);
      box-shadow: 0 10px 30px rgba(0,0,0,0.6);
      transition: transform 0.25s ease;
      z-index: 100;
      pointer-events: none;
    }

    #toast.show {
      transform: translateX(-50%) translateY(0);
    }

    @media (max-width: 480px) {
      .hero-art-wrapper {
        width: 100px;
        height: 100px;
      }
      .track-title {
        font-size: 18px;
      }
      .player-card {
        padding: 18px;
      }
    }
  </style>
</head>
<body>
  <div class="ambient-glow"></div>

  <!-- Minimal Brand Header -->
  <header class="nav-container">
    <a href="/" class="brand">
      <div class="brand-logo">M</div>
      <div class="brand-name">Music<span>Sports</span></div>
    </a>
    <a href="${deepLink}" class="nav-app-btn" id="nav-btn">
      <span>Open App</span>
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
    </a>
  </header>

  <!-- Player Preview Card -->
  <main class="player-card">
    <div class="track-hero">
      <div class="hero-art-wrapper">
        <img src="${cleanImage}" alt="${cleanTitle}" class="hero-art" onerror="this.src='https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500'">
      </div>

      <div class="hero-meta">
        <div class="type-pill">${type === 'playlist' ? 'PLAYLIST' : 'SONG'}</div>
        <h1 class="track-title" title="${cleanTitle}">${cleanTitle}</h1>
        <div class="track-artist">${type === 'playlist' ? `By ${creator} • ${playlistCount || playlistSongs.length} songs` : cleanArtist}</div>
        <div class="meta-tags">
          <span class="preview-pill">Preview</span>
          ${cleanAlbum && type !== 'playlist' ? `<span style="font-size: 12px; color: var(--text-dim);">${cleanAlbum}</span>` : ''}
        </div>
      </div>
    </div>

    <!-- Scrubber -->
    <div class="scrubber-section">
      <div class="progress-bar" id="progress-bar">
        <div class="progress-fill" id="progress-fill"></div>
      </div>
      <div class="time-row">
        <span id="current-time">0:00</span>
        <span id="total-time">${durationSeconds > 0 ? formatDuration(durationSeconds) : '0:30'}</span>
      </div>
    </div>

    <!-- Controls Row -->
    <div class="controls-row">
      <div class="left-controls">
        <button class="play-btn" id="play-pause-btn" aria-label="Play Preview">
          <svg id="play-icon" viewBox="0 0 24 24"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>
          <svg id="pause-icon" viewBox="0 0 24 24" style="display:none;"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>
        </button>

        <div class="eq-bars" id="eq-bars">
          <div class="eq-bar"></div>
          <div class="eq-bar"></div>
          <div class="eq-bar"></div>
        </div>
      </div>

      <button class="btn-share" id="share-btn" aria-label="Share">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>
        <span>Share</span>
      </button>
    </div>

    <!-- Playlist Track List (Exact Spotify Preview Style) -->
    ${type === 'playlist' && playlistSongs.length > 0 ? `
    <div class="tracklist-container">
      <div class="tracklist-header">Tracklist Preview</div>
      ${playlistSongs.map((song, i) => `
        <div class="track-row" data-preview="${escapeHtml(song.previewUrl)}" data-id="${escapeHtml(song.id)}">
          <div class="track-left">
            <span class="track-num">${i + 1}</span>
            <div class="track-text">
              <div class="row-title">${song.title}</div>
              <div class="row-artist">${song.artist}</div>
            </div>
          </div>
          <span class="track-duration">${formatDuration(song.duration)}</span>
        </div>
      `).join('')}
    </div>
    ` : ''}

    <!-- Call to Action -->
    <div class="cta-row">
      <a href="${deepLink}" class="btn-open-app" id="open-app-btn">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14.5v-9l6 4.5-6 4.5z"/></svg>
        <span>Open in MusicSports</span>
      </a>
    </div>
  </main>

  <!-- Cricket + Music Teaser Pill -->
  <aside class="cricket-footer">
    <div class="cricket-footer-text">
      <span>🏏</span>
      <div>
        <strong>Dual Engine Experience</strong>
        <div style="font-size: 11px; color: var(--text-dim);">Live ball-by-ball cricket scores synced with high-fi music</div>
      </div>
    </div>
    <a href="${deepLink}" style="color: var(--pink-primary); text-decoration: none; font-size: 12px; font-weight: 700;">Get App →</a>
  </aside>

  <div id="toast">Link copied to clipboard!</div>

  <audio id="audio-player" preload="none">
    ${cleanAudio ? `<source src="${cleanAudio}" type="audio/mp4">` : ''}
  </audio>

  <script>
    const audio = document.getElementById('audio-player');
    const playBtn = document.getElementById('play-pause-btn');
    const playIcon = document.getElementById('play-icon');
    const pauseIcon = document.getElementById('pause-icon');
    const progressBar = document.getElementById('progress-bar');
    const progressFill = document.getElementById('progress-fill');
    const currentTimeEl = document.getElementById('current-time');
    const totalTimeEl = document.getElementById('total-time');
    const eqBars = document.querySelectorAll('.eq-bar');
    const shareBtn = document.getElementById('share-btn');
    const toast = document.getElementById('toast');
    const deepLink = "${deepLink}";

    let isPlaying = false;
    let hasAudio = ${cleanAudio ? 'true' : 'false'};

    function showToast(msg) {
      toast.innerText = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 2200);
    }

    function formatSecs(sec) {
      const m = Math.floor(sec / 60);
      const s = Math.floor(sec % 60);
      return m + ':' + (s < 10 ? '0' : '') + s;
    }

    function setPlayState(playing) {
      isPlaying = playing;
      if (playing) {
        playIcon.style.display = 'none';
        pauseIcon.style.display = 'block';
        eqBars.forEach(b => b.classList.add('active'));
      } else {
        playIcon.style.display = 'block';
        pauseIcon.style.display = 'none';
        eqBars.forEach(b => b.classList.remove('active'));
      }
    }

    playBtn.addEventListener('click', () => {
      if (!hasAudio && !audio.src) {
        window.location.href = deepLink;
        return;
      }
      if (audio.paused) {
        audio.play().then(() => setPlayState(true)).catch(() => {
          window.location.href = deepLink;
        });
      } else {
        audio.pause();
        setPlayState(false);
      }
    });

    audio.addEventListener('timeupdate', () => {
      if (audio.duration) {
        const pct = (audio.currentTime / audio.duration) * 100;
        progressFill.style.width = pct + '%';
        currentTimeEl.innerText = formatSecs(audio.currentTime);
        totalTimeEl.innerText = formatSecs(audio.duration);
      }
    });

    audio.addEventListener('ended', () => {
      setPlayState(false);
      progressFill.style.width = '0%';
      currentTimeEl.innerText = '0:00';
    });

    progressBar.addEventListener('click', (e) => {
      if (!audio.duration) return;
      const rect = progressBar.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const pct = clickX / rect.width;
      audio.currentTime = pct * audio.duration;
    });

    // Playlist interactive row preview
    const rows = document.querySelectorAll('.track-row');
    rows.forEach(row => {
      row.addEventListener('click', () => {
        const previewUrl = row.getAttribute('data-preview');
        if (previewUrl) {
          audio.src = previewUrl;
          hasAudio = true;
          rows.forEach(r => r.classList.remove('active'));
          row.classList.add('active');
          audio.play().then(() => setPlayState(true)).catch(() => {});
        } else {
          window.location.href = deepLink;
        }
      });
    });

    shareBtn.addEventListener('click', () => {
      const shareUrl = "${canonicalUrl}";
      if (navigator.share) {
        navigator.share({
          url: shareUrl
        }).catch(() => {});
      } else {
        navigator.clipboard.writeText(shareUrl).then(() => {
          showToast('Link copied!');
        }).catch(() => {
          showToast('Could not copy link');
        });
      }
    });

    // On Android devices, prompt or redirect to app
    const isAndroid = /Android/i.test(navigator.userAgent);
    if (isAndroid) {
      setTimeout(() => {
        window.location.href = deepLink;
      }, 700);
    }
  </script>
</body>
</html>`;

  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.setHeader('Cache-Control', 's-maxage=86400, stale-while-revalidate');
  res.status(200).send(html);
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

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function formatDuration(totalSeconds) {
  if (!totalSeconds || isNaN(totalSeconds)) return '0:30';
  const m = Math.floor(totalSeconds / 60);
  const s = Math.floor(totalSeconds % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

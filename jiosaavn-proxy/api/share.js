export default async function handler(req, res) {
  let { type = 'track', id = '', title: qTitle, artist: qArtist, img: qImg, songs: qSongs } = req.query;

  // Normalize type
  if (type === 'song') type = 'track';

  let title = qTitle || (type === 'playlist' ? 'Featured Playlist' : (type === 'artist' ? 'Artist Profile' : (type === 'album' ? 'Album Preview' : 'Track Preview')));
  let artist = qArtist || (type === 'playlist' ? 'Curated Collection' : (type === 'artist' ? 'Verified Artist' : 'Synq'));
  let album = '';
  let year = '';
  let imageUrl = qImg || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80';
  let durationSeconds = 0;
  let playlistSongs = [];
  let playlistCount = 0;
  let creator = 'Synq';

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
          creator = cleanEntity(data.firstname || 'Synq');
          artist = creator;
          if (data.image) {
            imageUrl = data.image.replace(/150x150/, '500x500');
          }
          const rawSongs = Array.isArray(data.songs)
            ? data.songs
            : (Array.isArray(data.list) ? data.list : Object.values(data).filter(v => v && typeof v === 'object' && (v.song || v.title)));

          playlistCount = parseInt(data.list_count || rawSongs.length || '0', 10);
          playlistSongs = rawSongs.slice(0, 30).map(s => ({
            id: s.id,
            title: cleanEntity(s.song || s.title || 'Untitled Track'),
            artist: cleanEntity(s.primary_artists || s.singers || 'Unknown Artist'),
            duration: parseInt(s.duration || '0', 10),
            image: s.image ? s.image.replace(/150x150/, '250x250') : ''
          }));
        }
      } else if (type === 'album') {
        const apiUrl = `https://www.jiosaavn.com/api.php?__call=content.getAlbumDetails&albumid=${encodeURIComponent(id)}&_format=json`;
        const response = await fetch(apiUrl, {
          headers: { 'User-Agent': 'Mozilla/5.0' },
          signal: AbortSignal.timeout(4000)
        });
        if (response.ok) {
          const data = await response.json();
          title = cleanEntity(data.title || data.name || title);
          artist = cleanEntity(data.primary_artists || data.subtitle || 'Various Artists');
          album = title;
          year = data.year || '';
          if (data.image) {
            imageUrl = data.image.replace(/150x150/, '500x500');
          }
          const rawSongs = Array.isArray(data.songs) ? data.songs : (Array.isArray(data.list) ? data.list : []);
          playlistCount = rawSongs.length;
          playlistSongs = rawSongs.slice(0, 30).map(s => ({
            id: s.id,
            title: cleanEntity(s.song || s.title || 'Untitled Track'),
            artist: cleanEntity(s.primary_artists || s.singers || artist),
            duration: parseInt(s.duration || '0', 10),
            image: s.image ? s.image.replace(/150x150/, '250x250') : ''
          }));
        }
      } else if (type === 'artist') {
        const apiUrl = `https://www.jiosaavn.com/api.php?__call=artist.getArtistPageDetails&artistId=${encodeURIComponent(id)}&n_song=20&_format=json`;
        const response = await fetch(apiUrl, {
          headers: { 'User-Agent': 'Mozilla/5.0' },
          signal: AbortSignal.timeout(4000)
        });
        if (response.ok) {
          const data = await response.json();
          title = cleanEntity(data.name || title);
          artist = 'Verified Artist';
          if (data.image) {
            imageUrl = data.image.replace(/150x150/, '500x500');
          }
          const rawSongs = (data.topSongs && data.topSongs.songs) ? data.topSongs.songs : (Array.isArray(data.topSongs) ? data.topSongs : []);
          playlistCount = rawSongs.length;
          playlistSongs = rawSongs.slice(0, 20).map(s => ({
            id: s.id,
            title: cleanEntity(s.song || s.title || 'Untitled Track'),
            artist: cleanEntity(s.primary_artists || s.singers || title),
            duration: parseInt(s.duration || '0', 10),
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
  const cleanId = encodeURIComponent(id || 'sample');

  const domain = '21yash.online';
  const canonicalUrl = `https://${domain}/${type}/${cleanId}`;
  const deepLink = `synq://${type}/${cleanId}`;

  // Clean description for social previews
  const metaDescription = type === 'playlist'
    ? `${playlistCount || (playlistSongs.length || 'Curated')} songs • Playlist by ${creator}`
    : (type === 'album'
        ? `${cleanArtist} • Album (${playlistCount || playlistSongs.length} songs)`
        : (type === 'artist'
            ? `Listen to ${cleanTitle} on synq • Verified Artist`
            : `${cleanArtist} • ${cleanAlbum || 'Song'}`));

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>${cleanTitle} | Synq</title>
  <meta name="description" content="${escapeHtml(metaDescription)}">

  <!-- Open Graph -->
  <meta property="og:site_name" content="Synq">
  <meta property="og:type" content="${type === 'playlist' ? 'music.playlist' : (type === 'album' ? 'music.album' : (type === 'artist' ? 'profile' : 'music.song'))}">
  <meta property="og:title" content="${cleanTitle}">
  <meta property="og:description" content="${escapeHtml(metaDescription)}">
  <meta property="og:image" content="${cleanImage}">
  <meta property="og:image:width" content="500">
  <meta property="og:image:height" content="500">
  <meta property="og:url" content="${canonicalUrl}">

  <!-- Twitter / Discord Horizontal Compact Card -->
  <meta name="twitter:card" content="summary">
  <meta name="twitter:title" content="${cleanTitle}">
  <meta name="twitter:description" content="${escapeHtml(metaDescription)}">
  <meta name="twitter:image" content="${cleanImage}">

  <!-- oEmbed Discovery -->
  <link rel="alternate" type="application/json+oembed" href="https://${domain}/api/oembed?type=${type}&id=${cleanId}" title="${cleanTitle}">

  <!-- Google Fonts: Outfit & Inter -->
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">

  <style>
    :root {
      /* App Theme Tokens directly from FullPlayerSheet.kt */
      --bg-dark: #131016;
      --card-bg: #1B1722;
      --card-surface: #221D2C;
      --card-border: rgba(255, 255, 255, 0.08);
      --card-border-hover: rgba(255, 42, 95, 0.35);

      /* Brand Pink Palette (from FullPlayerSheet.kt) */
      --brand-pink: #FF2A5F;
      --pink-accent: #FF3366;
      --pink-light: #FF5E8A;
      --pink-dark: #E11D48;
      --pink-glow: rgba(255, 42, 95, 0.35);

      --text-white: #FFFFFF;
      --text-gray: rgba(255, 255, 255, 0.65);
      --text-dim: rgba(255, 255, 255, 0.4);
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
      background-image: 
        radial-gradient(ellipse 65% 50% at 50% -10%, rgba(255, 42, 95, 0.14), transparent),
        radial-gradient(circle 380px at 85% 35%, rgba(255, 42, 95, 0.08), transparent);
      background-repeat: no-repeat;
    }

    /* Ambient Subtle Blur */
    .ambient-glow {
      position: fixed;
      top: 15%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 440px;
      height: 440px;
      background: radial-gradient(circle, var(--pink-glow) 0%, rgba(225, 29, 72, 0.08) 50%, transparent 75%);
      filter: blur(120px);
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
      width: 36px;
      height: 36px;
      border-radius: 11px;
      background: linear-gradient(135deg, var(--brand-pink), var(--pink-dark));
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 18px;
      box-shadow: 0 4px 14px var(--pink-glow);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #fff;
    }

    .brand-name {
      font-weight: 800;
      font-size: 18px;
      letter-spacing: -0.3px;
    }

    .brand-name span {
      color: var(--brand-pink);
    }

    .nav-app-btn {
      padding: 7px 16px;
      border-radius: 18px;
      background: rgba(255, 255, 255, 0.06);
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
      background: rgba(255, 255, 255, 0.12);
      border-color: rgba(255, 255, 255, 0.2);
    }

    /* Player Card */
    .player-card {
      width: 100%;
      max-width: 580px;
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 24px;
      padding: 24px;
      z-index: 10;
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.6);
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    /* Horizontal Track Hero */
    .track-hero {
      display: flex;
      align-items: center;
      gap: 20px;
    }

    .hero-art-wrapper {
      position: relative;
      flex-shrink: 0;
      width: 130px;
      height: 130px;
      border-radius: 18px;
      overflow: hidden;
      box-shadow: 0 10px 28px rgba(0, 0, 0, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.1);
    }

    .hero-art-wrapper.is-artist {
      border-radius: 50%;
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
      color: var(--brand-pink);
      margin-bottom: 4px;
    }

    .track-title {
      font-size: 21px;
      font-weight: 800;
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
      margin-bottom: 8px;
    }

    .meta-tags {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }

    .preview-pill {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 8px;
      background: rgba(255, 42, 95, 0.14);
      color: var(--pink-light);
      border: 1px solid rgba(255, 42, 95, 0.28);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .meta-info-tag {
      font-size: 12px;
      color: var(--text-dim);
    }

    /* Scrubber / Progress Visualizer (Static representation pointing to App) */
    .scrubber-section {
      display: flex;
      flex-direction: column;
      gap: 6px;
      cursor: pointer;
    }

    .progress-bar {
      width: 100%;
      height: 6px;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 4px;
      overflow: hidden;
    }

    .progress-fill {
      height: 100%;
      width: 45%;
      background: linear-gradient(90deg, var(--brand-pink), var(--pink-light));
      border-radius: 4px;
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
      padding-top: 2px;
    }

    .left-controls {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    /* Big Pink Play Button modeled after FullPlayerSheet.kt */
    .play-btn {
      width: 52px;
      height: 52px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--brand-pink) 0%, var(--pink-light) 50%, var(--pink-dark) 100%);
      border: none;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      box-shadow: 0 6px 20px var(--pink-glow);
      transition: transform 0.18s ease, box-shadow 0.18s ease;
    }

    .play-btn:hover {
      transform: scale(1.06);
      box-shadow: 0 10px 26px rgba(255, 42, 95, 0.6);
    }

    .play-btn:active {
      transform: scale(0.96);
    }

    .play-btn svg {
      width: 24px;
      height: 24px;
      fill: currentColor;
      margin-left: 2px;
    }

    /* Animated Equalizer */
    .eq-bars {
      display: flex;
      align-items: flex-end;
      gap: 3px;
      height: 20px;
    }

    .eq-bar {
      width: 3.5px;
      background: var(--brand-pink);
      border-radius: 2px;
      height: 4px;
      animation: eqDance 0.8s ease-in-out infinite alternate;
    }

    .eq-bar:nth-child(1) { height: 6px; animation-duration: 0.38s; }
    .eq-bar:nth-child(2) { height: 16px; animation-duration: 0.5s; }
    .eq-bar:nth-child(3) { height: 10px; animation-duration: 0.3s; }
    .eq-bar:nth-child(4) { height: 18px; animation-duration: 0.42s; }

    @keyframes eqDance {
      0% { height: 4px; }
      100% { height: 20px; }
    }

    .app-only-pill {
      font-size: 12px;
      color: var(--text-gray);
      display: flex;
      align-items: center;
      gap: 6px;
    }

    /* CTA Buttons */
    .cta-row {
      display: flex;
      gap: 12px;
    }

    .btn-open-app {
      flex: 1;
      padding: 13px 20px;
      border-radius: 14px;
      background: linear-gradient(135deg, var(--brand-pink) 0%, var(--pink-light) 50%, var(--pink-dark) 100%);
      border: none;
      color: #fff;
      font-size: 15px;
      font-weight: 700;
      text-decoration: none;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      box-shadow: 0 6px 20px var(--pink-glow);
      cursor: pointer;
      transition: all 0.2s ease;
    }

    .btn-open-app:hover {
      box-shadow: 0 10px 28px rgba(255, 42, 95, 0.6);
      transform: translateY(-2px);
    }

    .btn-share {
      padding: 13px 18px;
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
      gap: 8px;
      transition: all 0.2s ease;
    }

    .btn-share:hover {
      background: rgba(255, 255, 255, 0.14);
    }

    /* Playlist Tracklist */
    .tracklist-container {
      margin-top: 10px;
      border-top: 1px solid var(--card-border);
      padding-top: 16px;
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
      display: flex;
      justify-content: space-between;
    }

    .track-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      border-radius: 12px;
      transition: background 0.15s ease;
      cursor: pointer;
      text-decoration: none;
      color: inherit;
    }

    .track-row:hover {
      background: var(--item-hover);
    }

    .track-left {
      display: flex;
      align-items: center;
      gap: 14px;
      min-width: 0;
      flex: 1;
    }

    .track-num {
      width: 22px;
      font-size: 13px;
      color: var(--text-dim);
      font-weight: 700;
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
      border-radius: 18px;
      padding: 14px 18px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      z-index: 10;
    }

    .cricket-footer-text {
      display: flex;
      align-items: center;
      gap: 12px;
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
      background: #231E2C;
      color: #fff;
      padding: 10px 22px;
      border-radius: 100px;
      font-size: 13px;
      font-weight: 600;
      border: 1px solid rgba(255, 42, 95, 0.35);
      box-shadow: 0 10px 30px rgba(0,0,0,0.7);
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

  <!-- Player Preview Card (No Web Audio - Directs to App) -->
  <main class="player-card">
    <div class="track-hero">
      <div class="hero-art-wrapper ${type === 'artist' ? 'is-artist' : ''}">
        <img src="${cleanImage}" alt="${cleanTitle}" class="hero-art" onerror="this.src='https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500'">
      </div>

      <div class="hero-meta">
        <div class="type-pill">${type === 'playlist' ? 'PLAYLIST' : (type === 'artist' ? 'ARTIST' : (type === 'album' ? 'ALBUM' : 'SONG'))}</div>
        <h1 class="track-title" title="${cleanTitle}">${cleanTitle}</h1>
        <div class="track-artist">${type === 'playlist' ? `Curated by ${creator} • ${playlistCount || playlistSongs.length} songs` : (type === 'album' ? `${cleanArtist} • ${year ? year + ' • ' : ''}${playlistCount || playlistSongs.length} songs` : (type === 'artist' ? `Verified Artist • ${playlistCount || playlistSongs.length} popular tracks` : cleanArtist))}</div>
        <div class="meta-tags">
          <span class="preview-pill">320 KBPS LOSSLESS</span>
          ${cleanAlbum && type !== 'playlist' && type !== 'artist' ? `<span class="meta-info-tag">${cleanAlbum}${year ? ' • ' + year : ''}</span>` : ''}
        </div>
      </div>
    </div>

    <!-- Scrubber Row (Metadata representation) -->
    <div class="scrubber-section" onclick="triggerAppAction()">
      <div class="progress-bar">
        <div class="progress-fill"></div>
      </div>
      <div class="time-row">
        <span>0:00</span>
        <span>${durationSeconds > 0 ? formatDuration(durationSeconds) : '4:15'}</span>
      </div>
    </div>

    <!-- Controls Row -->
    <div class="controls-row">
      <div class="left-controls">
        <button class="play-btn" id="play-pause-btn" onclick="triggerAppAction()" aria-label="Listen in App">
          <svg viewBox="0 0 24 24"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>
        </button>

        <div class="eq-bars">
          <div class="eq-bar"></div>
          <div class="eq-bar"></div>
          <div class="eq-bar"></div>
          <div class="eq-bar"></div>
        </div>

        <span class="app-only-pill">Lossless audio in app</span>
      </div>

      <button class="btn-share" id="share-btn" aria-label="Share">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>
        <span>Share</span>
      </button>
    </div>

    <!-- Track List (Playlist / Album / Artist) -->
    ${playlistSongs.length > 0 ? `
    <div class="tracklist-container">
      <div class="tracklist-header">
        <span>${type === 'artist' ? 'Popular Tracks' : (type === 'album' ? 'Album Tracks' : 'Tracklist')} (${playlistSongs.length} Tracks)</span>
        <span>Duration</span>
      </div>
      ${playlistSongs.map((song, i) => `
        <div class="track-row" onclick="triggerSongDeepLink('${escapeHtml(song.id)}')">
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
        <span>${type === 'artist' ? 'Open Artist in Synq' : (type === 'album' ? 'Open Album in Synq' : (type === 'playlist' ? 'Open Playlist in Synq' : 'Listen in Synq App'))}</span>
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
    <a href="synq://sports" style="color: var(--brand-pink); text-decoration: none; font-size: 12px; font-weight: 700;">View Live →</a>
  </aside>

  <div id="toast">Link copied to clipboard!</div>

  <script>
    const deepLink = "${deepLink}";
    const canonicalUrl = "${canonicalUrl}";
    const shareBtn = document.getElementById('share-btn');
    const toast = document.getElementById('toast');

    function showToast(msg) {
      toast.innerText = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 2400);
    }

    function triggerAppAction() {
      showToast('Opening in Synq App...');
      window.location.href = deepLink;
    }

    function triggerSongDeepLink(songId) {
      if (songId) {
        window.location.href = "synq://track/" + songId;
      } else {
        triggerAppAction();
      }
    }

    shareBtn.addEventListener('click', () => {
      if (navigator.share) {
        navigator.share({
          title: "${cleanTitle}",
          text: "Listen to ${cleanTitle} on Synq",
          url: canonicalUrl
        }).catch(() => {});
      } else {
        navigator.clipboard.writeText(canonicalUrl).then(() => {
          showToast('Share link copied!');
        }).catch(() => {
          showToast('Could not copy link');
        });
      }
    });

    // On Android devices, attempt opening app directly
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
  if (!totalSeconds || isNaN(totalSeconds)) return '3:45';
  const m = Math.floor(totalSeconds / 60);
  const s = Math.floor(totalSeconds % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

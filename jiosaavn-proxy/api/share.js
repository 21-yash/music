export default async function handler(req, res) {
  const { type = 'track', id = '', title: qTitle, artist: qArtist, img: qImg, songs: qSongs } = req.query;

  let title = qTitle || (type === 'playlist' ? 'Featured Playlist' : 'Track Preview');
  let artist = qArtist || (type === 'playlist' ? 'Curated Collection' : 'MusicSports');
  let imageUrl = qImg || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80';
  let audioUrl = '';
  let durationSeconds = 0;

  // If track ID provided and metadata missing or audio stream needed, try resolving details from JioSaavn
  if (id && (!qTitle || !qImg)) {
    try {
      const apiUrl = `https://www.jiosaavn.com/api.php?__call=song.getDetails&pids=${encodeURIComponent(id)}&_format=json&_marker=0&ctx=android`;
      const response = await fetch(apiUrl, {
        headers: {
          'User-Agent': 'JioSaavn/7.39.2 (Android; 13; en)',
          'Accept': 'application/json'
        },
        signal: AbortSignal.timeout(4000)
      });
      if (response.ok) {
        const raw = await response.json();
        const songData = raw[id] || Object.values(raw)[0];
        if (songData) {
          title = songData.song || songData.title || title;
          artist = songData.primary_artists || songData.singers || artist;
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
    } catch {
      // Fallback cleanly to query parameters
    }
  }

  // Sanitize strings for HTML insertion
  const cleanTitle = escapeHtml(title);
  const cleanArtist = escapeHtml(artist);
  const cleanImage = escapeHtml(imageUrl);
  const cleanAudio = escapeHtml(audioUrl);
  const cleanId = encodeURIComponent(id || 'sample');
  const deepLink = `musicsports://${type}/${cleanId}`;
  const shareUrl = `https://${req.headers.host || 'music-jiosaavn-proxy.vercel.app'}/${type}/${cleanId}?title=${encodeURIComponent(title)}&artist=${encodeURIComponent(artist)}&img=${encodeURIComponent(imageUrl)}`;

  const pageTitle = `${cleanTitle} • ${cleanArtist} | MusicSportsApp`;
  const pageDescription = type === 'playlist'
    ? `Listen to "${cleanTitle}" playlist on MusicSportsApp. Stream uninterrupted music while tracking live ball-by-ball cricket scores.`
    : `Listen to "${cleanTitle}" by ${cleanArtist} on MusicSportsApp. Stream in crystal clear audio with live synced lyrics and match scores.`;

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>${pageTitle}</title>
  <meta name="description" content="${pageDescription}">

  <!-- Open Graph / WhatsApp / Facebook -->
  <meta property="og:type" content="${type === 'playlist' ? 'music.playlist' : 'music.song'}">
  <meta property="og:title" content="${cleanTitle} • ${cleanArtist}">
  <meta property="og:description" content="${pageDescription}">
  <meta property="og:image" content="${cleanImage}">
  <meta property="og:url" content="${shareUrl}">
  <meta property="og:site_name" content="MusicSportsApp">

  <!-- Twitter / X Card -->
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="${cleanTitle} • ${cleanArtist}">
  <meta name="twitter:description" content="${pageDescription}">
  <meta name="twitter:image" content="${cleanImage}">

  <!-- Theme color & fonts -->
  <meta name="theme-color" content="#0B0910">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">

  <style>
    :root {
      --bg-primary: #09070E;
      --bg-card: rgba(22, 17, 32, 0.75);
      --bg-glass: rgba(255, 255, 255, 0.05);
      --pink-primary: #EC4899;
      --pink-glow: rgba(236, 72, 153, 0.45);
      --indigo-accent: #6366F1;
      --text-main: #FFFFFF;
      --text-muted: #94A3B8;
      --border-glass: rgba(255, 255, 255, 0.12);
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      -webkit-tap-highlight-color: transparent;
    }

    body {
      background-color: var(--bg-primary);
      color: var(--text-main);
      font-family: 'Outfit', 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      overflow-x: hidden;
      position: relative;
      padding: 20px 16px 40px;
    }

    /* Ambient dynamic glow */
    .ambient-glow {
      position: fixed;
      top: 15%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 380px;
      height: 380px;
      background: radial-gradient(circle, var(--pink-glow) 0%, rgba(99, 102, 241, 0.2) 50%, transparent 70%);
      filter: blur(80px);
      z-index: 0;
      pointer-events: none;
    }

    /* Header Nav */
    header {
      width: 100%;
      max-width: 520px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 6px 20px;
      z-index: 10;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      color: #fff;
    }

    .brand-icon {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: linear-gradient(135deg, #EC4899, #6366F1);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 800;
      font-size: 18px;
      box-shadow: 0 4px 14px rgba(236, 72, 153, 0.4);
    }

    .brand-text {
      font-weight: 700;
      font-size: 18px;
      letter-spacing: -0.3px;
    }

    .brand-text span {
      background: linear-gradient(135deg, #EC4899, #A855F7);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .pill-download-btn {
      padding: 8px 16px;
      border-radius: 20px;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid var(--border-glass);
      color: #fff;
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.2s ease;
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }

    .pill-download-btn:hover {
      background: rgba(255, 255, 255, 0.16);
      border-color: rgba(255, 255, 255, 0.25);
    }

    /* Main Player Card */
    main {
      width: 100%;
      max-width: 440px;
      z-index: 10;
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    .player-card {
      width: 100%;
      background: var(--bg-card);
      backdrop-filter: blur(24px);
      -webkit-backdrop-filter: blur(24px);
      border: 1px solid var(--border-glass);
      border-radius: 28px;
      padding: 24px;
      display: flex;
      flex-direction: column;
      align-items: center;
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5), 0 0 40px rgba(236, 72, 153, 0.15);
      position: relative;
    }

    /* Cover Artwork Container with Vinyl Effect */
    .artwork-stage {
      position: relative;
      width: 250px;
      height: 250px;
      margin: 10px 0 22px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .vinyl-disc {
      position: absolute;
      width: 230px;
      height: 230px;
      border-radius: 50%;
      background: radial-gradient(circle, #2d2638 15%, #0f0c14 30%, #1c1724 45%, #0a080e 60%, #15111d 80%, #000 100%);
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.8);
      right: -15px;
      transition: transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
      z-index: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .vinyl-disc.playing {
      transform: translateX(45px);
      animation: spin 12s linear infinite;
    }

    .vinyl-center {
      width: 70px;
      height: 70px;
      border-radius: 50%;
      background: url("${cleanImage}") center/cover;
      border: 3px solid #111;
      box-shadow: inset 0 0 10px rgba(0,0,0,0.8);
    }

    @keyframes spin {
      100% { transform: translateX(45px) rotate(360deg); }
    }

    .cover-img {
      position: relative;
      width: 240px;
      height: 240px;
      border-radius: 18px;
      object-fit: cover;
      box-shadow: 0 14px 34px rgba(0, 0, 0, 0.7);
      z-index: 2;
      border: 1px solid rgba(255, 255, 255, 0.1);
    }

    /* Track Meta */
    .track-info {
      width: 100%;
      text-align: center;
      margin-bottom: 20px;
    }

    .track-title {
      font-size: 22px;
      font-weight: 700;
      color: #fff;
      line-height: 1.25;
      margin-bottom: 6px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .track-artist {
      font-size: 15px;
      color: var(--text-muted);
      font-weight: 500;
      display: inline-block;
      padding: 3px 12px;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 12px;
    }

    /* Scrubber / Progress Bar */
    .scrubber-container {
      width: 100%;
      margin-bottom: 18px;
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
      background: linear-gradient(90deg, #6366F1, #EC4899);
      border-radius: 4px;
      transition: width 0.1s linear;
    }

    .time-row {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--text-muted);
      font-family: 'Inter', sans-serif;
      margin-top: 6px;
    }

    /* Audio Controls */
    .controls-row {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 20px;
      width: 100%;
      margin-bottom: 22px;
    }

    .play-btn {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: linear-gradient(135deg, #EC4899, #E11D48);
      border: none;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      box-shadow: 0 8px 24px var(--pink-glow);
      transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
    }

    .play-btn:hover {
      transform: scale(1.08);
      box-shadow: 0 12px 30px rgba(236, 72, 153, 0.65);
    }

    .play-btn:active {
      transform: scale(0.95);
    }

    .play-btn svg {
      width: 28px;
      height: 28px;
      fill: currentColor;
    }

    /* Animated Equalizer Bars */
    .equalizer-bars {
      display: flex;
      align-items: flex-end;
      gap: 3px;
      height: 20px;
    }

    .eq-bar {
      width: 4px;
      background: #EC4899;
      border-radius: 2px;
      height: 6px;
    }

    .eq-bar.active {
      animation: eqBounce 0.8s ease-in-out infinite alternate;
    }

    .eq-bar:nth-child(1) { animation-duration: 0.42s; }
    .eq-bar:nth-child(2) { animation-duration: 0.31s; height: 10px; }
    .eq-bar:nth-child(3) { animation-duration: 0.48s; }
    .eq-bar:nth-child(4) { animation-duration: 0.36s; height: 14px; }

    @keyframes eqBounce {
      0% { height: 4px; }
      100% { height: 20px; }
    }

    /* App CTA Buttons */
    .cta-group {
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .open-app-btn {
      width: 100%;
      padding: 15px 20px;
      border-radius: 18px;
      background: linear-gradient(135deg, #EC4899 0%, #6366F1 100%);
      border: none;
      color: #fff;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
      text-decoration: none;
      box-shadow: 0 8px 25px rgba(236, 72, 153, 0.4);
      transition: all 0.2s ease;
    }

    .open-app-btn:hover {
      box-shadow: 0 12px 32px rgba(236, 72, 153, 0.6);
      transform: translateY(-2px);
    }

    .share-pill-btn {
      width: 100%;
      padding: 12px;
      border-radius: 14px;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid var(--border-glass);
      color: var(--text-main);
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.2s ease;
    }

    .share-pill-btn:hover {
      background: rgba(255, 255, 255, 0.12);
    }

    /* Dual Feature Banner (Music + Cricket) */
    .dual-feature-card {
      margin-top: 24px;
      width: 100%;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--border-glass);
      border-radius: 20px;
      padding: 16px;
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .feature-badge {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: rgba(99, 102, 241, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;
    }

    .feature-text h4 {
      font-size: 14px;
      font-weight: 700;
      margin-bottom: 2px;
    }

    .feature-text p {
      font-size: 12px;
      color: var(--text-muted);
      line-height: 1.35;
    }

    /* Toast Notification */
    #toast {
      position: fixed;
      bottom: 24px;
      left: 50%;
      transform: translateX(-50%) translateY(80px);
      background: #1E1B29;
      color: #fff;
      padding: 12px 24px;
      border-radius: 24px;
      font-size: 14px;
      font-weight: 600;
      border: 1px solid var(--border-glass);
      box-shadow: 0 10px 30px rgba(0,0,0,0.6);
      transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
      z-index: 100;
      pointer-events: none;
    }

    #toast.show {
      transform: translateX(-50%) translateY(0);
    }
  </style>
</head>
<body>
  <div class="ambient-glow"></div>

  <header>
    <a href="/" class="brand" id="brand-link">
      <div class="brand-icon">M</div>
      <div class="brand-text">Music<span>Sports</span></div>
    </a>
    <a href="${deepLink}" class="pill-download-btn" id="nav-open-app">
      <span>Get App</span>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
    </a>
  </header>

  <main>
    <div class="player-card">
      <div class="artwork-stage">
        <div class="vinyl-disc" id="vinyl-disc">
          <div class="vinyl-center"></div>
        </div>
        <img src="${cleanImage}" alt="${cleanTitle}" class="cover-img" id="track-cover" onerror="this.src='https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600'">
      </div>

      <div class="track-info">
        <h1 class="track-title" id="track-title">${cleanTitle}</h1>
        <div class="track-artist" id="track-artist">${cleanArtist}</div>
      </div>

      <!-- Scrubber -->
      <div class="scrubber-container">
        <div class="progress-bar" id="progress-bar">
          <div class="progress-fill" id="progress-fill"></div>
        </div>
        <div class="time-row">
          <span id="current-time">0:00</span>
          <span id="total-time">${durationSeconds > 0 ? formatTime(durationSeconds) : 'Preview'}</span>
        </div>
      </div>

      <!-- Controls -->
      <div class="controls-row">
        <div class="equalizer-bars" id="eq-bars">
          <div class="eq-bar"></div>
          <div class="eq-bar"></div>
          <div class="eq-bar"></div>
          <div class="eq-bar"></div>
        </div>

        <button class="play-btn" id="play-pause-btn" aria-label="Play/Pause">
          <svg id="play-icon" viewBox="0 0 24 24"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>
          <svg id="pause-icon" viewBox="0 0 24 24" style="display:none;"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>
        </button>

        <div style="width: 20px;"></div>
      </div>

      <!-- Action Buttons -->
      <div class="cta-group">
        <a href="${deepLink}" class="open-app-btn" id="cta-open-app">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14.5v-9l6 4.5-6 4.5z"/></svg>
          <span>Listen in MusicSportsApp</span>
        </a>

        <button class="share-pill-btn" id="share-btn">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>
          <span>Share Track</span>
        </button>
      </div>
    </div>

    <!-- Dual Cricket & Music Banner -->
    <div class="dual-feature-card">
      <div class="feature-badge">🏏</div>
      <div class="feature-text">
        <h4>Music + Live Cricket Scores</h4>
        <p>Listen to your favorite songs while ball-by-ball IPL & cricket scores update in realtime.</p>
      </div>
    </div>
  </main>

  <div id="toast">Link copied to clipboard!</div>

  <audio id="audio-element" preload="none">
    ${cleanAudio ? `<source src="${cleanAudio}" type="audio/mp4">` : ''}
  </audio>

  <script>
    const audio = document.getElementById('audio-element');
    const playBtn = document.getElementById('play-pause-btn');
    const playIcon = document.getElementById('play-icon');
    const pauseIcon = document.getElementById('pause-icon');
    const vinyl = document.getElementById('vinyl-disc');
    const progressBar = document.getElementById('progress-bar');
    const progressFill = document.getElementById('progress-fill');
    const currentTimeEl = document.getElementById('current-time');
    const totalTimeEl = document.getElementById('total-time');
    const eqBars = document.querySelectorAll('.eq-bar');
    const shareBtn = document.getElementById('share-btn');
    const toast = document.getElementById('toast');

    let isPlaying = false;
    let hasAudio = ${cleanAudio ? 'true' : 'false'};

    function showToast(msg) {
      toast.innerText = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 2500);
    }

    function formatSecs(sec) {
      const m = Math.floor(sec / 60);
      const s = Math.floor(sec % 60);
      return m + ':' + (s < 10 ? '0' : '') + s;
    }

    function setPlayingState(playing) {
      isPlaying = playing;
      if (playing) {
        playIcon.style.display = 'none';
        pauseIcon.style.display = 'block';
        vinyl.classList.add('playing');
        eqBars.forEach(b => b.classList.add('active'));
      } else {
        playIcon.style.display = 'block';
        pauseIcon.style.display = 'none';
        vinyl.classList.remove('playing');
        eqBars.forEach(b => b.classList.remove('active'));
      }
    }

    playBtn.addEventListener('click', () => {
      if (!hasAudio) {
        // No preview stream: open deep link into app
        window.location.href = "${deepLink}";
        return;
      }

      if (audio.paused) {
        audio.play().then(() => {
          setPlayingState(true);
        }).catch(() => {
          // Fallback to app on autoplay block
          window.location.href = "${deepLink}";
        });
      } else {
        audio.pause();
        setPlayingState(false);
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
      setPlayingState(false);
      progressFill.style.width = '0%';
    });

    progressBar.addEventListener('click', (e) => {
      if (!hasAudio || !audio.duration) return;
      const rect = progressBar.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const pct = clickX / rect.width;
      audio.currentTime = pct * audio.duration;
    });

    shareBtn.addEventListener('click', () => {
      if (navigator.share) {
        navigator.share({
          title: "${cleanTitle}",
          text: "Listen to ${cleanTitle} by ${cleanArtist} on MusicSportsApp",
          url: window.location.href
        }).catch(() => {});
      } else {
        navigator.clipboard.writeText(window.location.href).then(() => {
          showToast('Link copied to clipboard!');
        }).catch(() => {
          showToast('Could not copy link');
        });
      }
    });

    // Auto-launch deep link on Android devices
    const isAndroid = /Android/i.test(navigator.userAgent);
    if (isAndroid) {
      setTimeout(() => {
        window.location.href = "${deepLink}";
      }, 500);
    }
  </script>
</body>
</html>`;

  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.status(200).send(html);
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

function formatTime(totalSeconds) {
  const m = Math.floor(totalSeconds / 60);
  const s = Math.floor(totalSeconds % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

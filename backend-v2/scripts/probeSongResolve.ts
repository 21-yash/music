import { YouTubeProvider } from '../src/providers/music/youtube/youtubeProvider';
import { resolveSong } from '../src/jam2/songResolver';
import { registerMusicProvider } from '../src/services/musicService';

async function main() {
  const videoId = process.argv[2] || 'dQw4w9WgXcQ';
  const provider = new YouTubeProvider();
  registerMusicProvider(provider, false);

  console.log(`Probing YouTube song ID: ${videoId}`);
  try {
    const rawSong = await provider.getSong(videoId);
    console.log('provider.getSong result:', JSON.stringify(rawSong, null, 2));
  } catch (err) {
    console.error('provider.getSong error:', err);
  }

  try {
    const resolved = await resolveSong(videoId, 'youtube');
    console.log('resolveSong result:', JSON.stringify(resolved, null, 2));
  } catch (err) {
    console.error('resolveSong error:', err);
  }
}

main().catch(console.error);

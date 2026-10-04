import { YouTubeProvider } from './src/providers/music/youtube/youtubeProvider';

async function test() {
    const yt = new YouTubeProvider();
    const result = await yt.search('test', 1, 2);
    console.log(JSON.stringify(result, null, 2));
}

test().catch(console.error);

import YTMusic from 'ytmusic-api';
async function test() {
    const yt = new YTMusic();
    await yt.initialize();
    const results = await yt.searchSongs('test');
    console.log(JSON.stringify(results[0], null, 2));
}
test().catch(console.error);

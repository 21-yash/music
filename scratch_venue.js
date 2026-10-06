const https = require('https');
https.get('https://m.cricbuzz.com/live-cricket-scores/155510/indu19-vs-ausu19-2nd-unofficial-test-australia-u19-tour-of-india-2026', (res) => {
  let data = '';
  res.on('data', d => data += d);
  res.on('end', () => {
    const matches = data.match(/self\.__next_f\.push\(\[1,"(.*?)"\]\)/g);
    if (matches) {
      for (const match of matches) {
        if (match.includes('venueInfo')) {
          const str = match.substring('self.__next_f.push([1,"'.length, match.length - 3);
          try {
            const dec = JSON.parse('"' + str + '"');
            if(dec.venueInfo) console.log("top level venueInfo");
            else console.log(Object.keys(dec));
          } catch(e) {}
        }
      }
    }
  });
});

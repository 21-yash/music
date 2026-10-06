const https = require('https');
https.get('https://m.cricbuzz.com/live-cricket-scores/155510', (res) => {
  let data = '';
  res.on('data', d => data += d);
  res.on('end', () => {
    const matches = data.match(/self\.__next_f\.push\(\[1,"(.*?)"\]\)/g);
    if (matches) {
      matches.forEach(m => {
        if (m.includes('venueInfo')) {
          const str = m.substring('self.__next_f.push([1,"'.length, m.length - 3);
          try {
            const dec = JSON.parse('"' + str + '"');
            console.log("KEYS:", Object.keys(dec));
            console.log("VENUEINFO:", dec.venueInfo);
            if(dec.matchHeader) console.log("MATCHHEADER VENUEINFO:", dec.matchHeader.venueInfo);
            if(dec.commentaryPageData) console.log("COMMENTARY VENUEINFO:", dec.commentaryPageData.venueInfo);
          } catch(e) {}
        }
      });
    }
  });
});

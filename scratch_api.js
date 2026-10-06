const http = require('http');
http.get('http://localhost:3000/api/sports/cricket/match/155510', (res) => {
  let data = '';
  res.on('data', d => data += d);
  res.on('end', () => {
    try {
      const json = JSON.parse(data);
      console.log(json.data.summary.venue);
    } catch(e) { console.error(e) }
  });
}).on('error', e => console.error('Error fetching backend:', e.message));

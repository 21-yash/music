export default async function handler(req, res) {
  // Construct the target JioSaavn URL
  const targetUrl = new URL('https://www.jiosaavn.com/api.php');
  
  // Forward all query parameters from the request
  for (const [key, value] of Object.entries(req.query)) {
    targetUrl.searchParams.append(key, value);
  }

  // Official Android app headers to bypass web-restrictions
  const headers = {
    'User-Agent': 'JioSaavn/7.39.2 (Android; 13; en)',
    'app_version': '7.39.2',
    'api_version': '4',
    'readable_version': '7.39.2',
    'network_type': 'WIFI',
    'Accept': 'application/json'
  };

  try {
    const response = await fetch(targetUrl.toString(), { headers });
    
    // We use .text() instead of .json() because JioSaavn sometimes 
    // returns improperly formatted JSON or extra characters
    const data = await response.text();
    
    // Add CORS headers so Render backend can fetch it safely if needed
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Content-Type', response.headers.get('content-type') || 'application/json');
    
    res.status(response.status).send(data);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
}

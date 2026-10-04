export default async function handler(req, res) {
  // ─── API key verification ─────────────────────────────────────
  // Only our backend should be able to use this proxy.
  const proxyKey = req.headers['x-proxy-key'];
  const expectedKey = process.env.PROXY_API_KEY;

  if (!expectedKey || proxyKey !== expectedKey) {
    res.status(403).json({ error: 'Forbidden' });
    return;
  }

  // ─── CORS — restrict to our backend only ──────────────────────
  const allowedOrigin = process.env.ALLOWED_ORIGIN || '';
  res.setHeader('Access-Control-Allow-Origin', allowedOrigin);

  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'x-proxy-key');
    res.status(204).end();
    return;
  }

  // ─── Construct target URL ─────────────────────────────────────
  const targetUrl = new URL('https://www.jiosaavn.com/api.php');

  // Forward all query parameters, ensuring each value is a string
  for (const [key, value] of Object.entries(req.query)) {
    // Vercel parses duplicate keys as arrays — only use first value
    const stringValue = Array.isArray(value) ? value[0] : value;
    if (typeof stringValue === 'string') {
      targetUrl.searchParams.append(key, stringValue);
    }
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

    res.setHeader('Content-Type', response.headers.get('content-type') || 'application/json');
    res.status(response.status).send(data);
  } catch {
    // Generic error — never leak internal details
    res.status(502).json({ error: 'Upstream request failed' });
  }
}

const fs = require('fs');
const html = fs.readFileSync('cricbuzz_out.html', 'utf8');
const matches = html.match(/self\.__next_f\.push\(\[1,"(.*?)"\]\)/g);
if (matches) {
  for (const match of matches) {
    if (match.includes('commentaryPageData')) {
      const str = match.substring('self.__next_f.push([1,"'.length, match.length - 3);
      try {
        const dec = JSON.parse('"' + str + '"');
        const pos = dec.indexOf('"matchHeader":{');
        if (pos > -1) {
           let end = pos + '"matchHeader":{'.length;
           let braces = 1;
           while(braces > 0 && end < dec.length) {
              if (dec[end] === '{') braces++;
              else if (dec[end] === '}') braces--;
              end++;
           }
           const hdrStr = dec.substring(pos + '"matchHeader":'.length, end);
           const hdrobj = JSON.parse(hdrStr);
           console.log("venue:", JSON.stringify(hdrobj.venue, null, 2));
           if (!hdrobj.venue) {
               console.log("Keys: ", Object.keys(hdrobj));
           }
        }
      } catch(e) { console.error(e) }
    }
  }
}

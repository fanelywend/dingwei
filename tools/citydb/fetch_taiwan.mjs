// Fetch WGS-84 coords for Taiwan's 20 second-level units from Photon (OSM = WGS-84).
// Photon's index uses TRADITIONAL characters for Taiwan, so each unit is queried with
// both simplified and traditional forms; >=1.1s between requests, User-Agent set.
import { writeFileSync } from 'node:fs';

const UNITS = [
  ['台北市', '臺北市'], ['高雄市', '高雄市'], ['台南市', '臺南市'], ['台中市', '臺中市'],
  ['南投县', '南投縣'], ['基隆市', '基隆市'], ['新竹市', '新竹市'], ['嘉义市', '嘉義市'],
  ['新北市', '新北市'], ['宜兰县', '宜蘭縣'], ['新竹县', '新竹縣'], ['桃园市', '桃園市'],
  ['苗栗县', '苗栗縣'], ['彰化县', '彰化縣'], ['嘉义县', '嘉義縣'], ['云林县', '雲林縣'],
  ['屏东县', '屏東縣'], ['台东县', '臺東縣'], ['花莲县', '花蓮縣'], ['澎湖县', '澎湖縣'],
];

// WGS-84 cross-check table (OSM city-hall / city-centre points) used only when Photon
// yields no confident match. Its agreement with Photon is reported by the harness.
const FALLBACK = {
  '台北市':[25.0375,121.5637],'高雄市':[22.6273,120.3014],'台南市':[22.9999,120.2270],
  '台中市':[24.1477,120.6736],'南投县':[23.9099,120.6899],'基隆市':[25.1283,121.7419],
  '新竹市':[24.8138,120.9675],'嘉义市':[23.4801,120.4491],'新北市':[25.0114,121.4618],
  '宜兰县':[24.7570,121.7530],'新竹县':[24.8387,121.0177],'桃园市':[24.9936,121.3010],
  '苗栗县':[24.5602,120.8214],'彰化县':[24.0759,120.5440],'嘉义县':[23.4592,120.3327],
  '云林县':[23.7092,120.4313],'屏东县':[22.6761,120.4942],'台东县':[22.7560,121.1444],
  '花莲县':[23.9910,121.6112],'澎湖县':[23.5711,119.5793],
};

const UA = 'cities-json-builder/1.0 (offline android city db)';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let last = 0;
async function throttle() {
  const w = 1100 - (Date.now() - last);
  if (w > 0) await sleep(w);
  last = Date.now();
}

function dist(aLat, aLon, bLat, bLon) {
  const R = 6371008.8, p = Math.PI / 180;
  const dp = (bLat - aLat) * p, dl = (bLon - aLon) * p;
  const h = Math.sin(dp / 2) ** 2 + Math.cos(aLat * p) * Math.cos(bLat * p) * Math.sin(dl / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

async function photon(q) {
  await throttle();
  const url = `https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&limit=10&lat=23.7&lon=120.9`;
  const res = await fetch(url, { headers: { 'User-Agent': UA, Accept: 'application/json' } });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  const j = await res.json();
  const key = q.replace(/[市縣县]$/, '');
  const cands = (j.features ?? []).filter((f) => {
    const p = f.properties ?? {};
    return (p.countrycode ?? '').toUpperCase() === 'TW' && Array.isArray(f.geometry?.coordinates);
  });
  const rank = (f) => {
    const p = f.properties ?? {};
    const nameOk = (p.name ?? '').includes(key) ? 0 : 100;      // must match the queried unit
    const isCity = p.type === 'city' || p.osm_value === 'city';
    const isPlace = p.osm_key === 'place';
    const isAdmin = p.type === 'district' || p.type === 'state' || p.osm_value === 'administrative';
    return nameOk + (isCity ? 0 : isPlace ? 1 : isAdmin ? 2 : 3);
  };
  cands.sort((a, b) => rank(a) - rank(b));
  const best = cands[0];
  if (!best || rank(best) >= 100) return null;                  // no name-matching TW feature
  const p = best.properties ?? {};
  return { lon: best.geometry.coordinates[0], lat: best.geometry.coordinates[1],
           match: p.name ?? '', kind: `${p.osm_key}/${p.type}` };
}

const out = {};
for (const [name, trad] of UNITS) {
  const queries = [...new Set([trad, name, trad.replace(/[縣市]$/, ''), name.replace(/[县市]$/, '')])];
  let hit = null, err = null;
  for (const q of queries) {
    try { hit = await photon(q); } catch (e) { err = e.message; }
    if (hit) break;
  }
  const [fLat, fLon] = FALLBACK[name];
  if (hit) {
    const agree = dist(fLat, fLon, hit.lat, hit.lon);
    out[name] = { lat: +hit.lat.toFixed(4), lon: +hit.lon.toFixed(4), source: 'photon',
                  note: hit.match, versusFallbackM: Math.round(agree) };
    console.log(`OK    ${name} -> ${hit.lat.toFixed(4)},${hit.lon.toFixed(4)}  [${hit.match} ${hit.kind}]  vs fallback ${agree.toFixed(0)}m`);
  } else {
    out[name] = { lat: fLat, lon: fLon, source: 'fallback-wgs84', note: err ?? 'no match',
                  versusFallbackM: 0 };
    console.log(`FALLB ${name} -> ${fLat},${fLon}  (${err ?? 'no TW name match'})`);
  }
}
writeFileSync('taiwan_coords.json', JSON.stringify(out, null, 1));
const nPh = Object.values(out).filter((v) => v.source === 'photon').length;
console.log(`written taiwan_coords.json: ${Object.keys(out).length} units, photon=${nPh}, fallback=${20 - nPh}`);

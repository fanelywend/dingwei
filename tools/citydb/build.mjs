// Build the offline China city database.
// Source: AreaCity-JsSpider-StatsGov ok_geo.csv (34 provinces + second-level units).
// ok_geo's `geo` column is GCJ-02 (per that project's README), so every coordinate taken
// from it is converted GCJ-02 -> WGS-84. Taiwan's 20 second-level units have no geo in
// ok_geo and come from Photon/OSM (already WGS-84), so they are written unconverted.
import { createReadStream, writeFileSync, mkdirSync, readFileSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { pinyin } from 'pinyin-pro';
import { gcj02ToWgs84 } from './transform.mjs';

const CSV = 'ok_geo.csv';
const OUT = '/home/orlando/dingwei/app/src/main/assets/cities.json';
const MUNICIPALITIES = new Set(['11', '12', '31', '50']);   // 北京 天津 上海 重庆
const TAIWAN_PID = '71';
const CHONGQING_PID = '50';

const taiwan = JSON.parse(readFileSync('taiwan_coords.json', 'utf8'));

const round4 = (n) => Math.round(n * 1e4) / 1e4;

function py(name) {
  const arr = pinyin(name, { toneType: 'none', type: 'array', v: false });
  const full = arr.join('').toLowerCase().replace(/[^a-z]/g, '');
  const initials = arr.map((s) => s[0]).join('').toLowerCase().replace(/[^a-z]/g, '');
  return { pinyin: full, py: initials };
}

function parseGeo(geo) {
  const s = (geo ?? '').trim();
  if (!s || s === 'EMPTY') return null;
  const parts = s.split(/\s+/);
  const lng = Number(parts[0]), lat = Number(parts[1]);
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null;
  return [lng, lat]; // [lng, lat]
}

// ---- parse the CSV (one record per line; the huge polygon column is skipped) ----
const RE = /^(\d+),(\d+),(\d+),"((?:[^"\\]|\\.)*)","((?:[^"\\]|\\.)*)","((?:[^"\\]|\\.)*)",/;
const provinces = [];   // {id,name,geo}
const cities = [];      // {pid,name,geo}
const skippedNoGeo = [];

const rl = createInterface({ input: createReadStream(CSV, { encoding: 'utf8' }), crlfDelay: Infinity });
let first = true, lineNo = 0, unmatched = 0;
for await (const rawLine of rl) {
  lineNo++;
  let line = rawLine;
  if (first) { line = line.replace(/^\uFEFF/, ''); first = false; if (line.startsWith('id,')) continue; }
  const m = line.match(RE);
  if (!m) { unmatched++; continue; }
  const [, id, pid, deep, name, , geoRaw] = m;
  const geo = parseGeo(geoRaw.replace(/""/g, '"'));
  if (deep === '0') provinces.push({ id, name, geo });
  else if (deep === '1') cities.push({ pid, name, geo });
}
console.log(`parsed ${lineNo} lines: ${provinces.length} provinces, ${cities.length} second-level units, ${unmatched} unmatched`);

// ---- assemble output records (WGS-84) ----
const records = [];
let convertedCount = 0, taiwanCount = 0, chongqingMerged = 0;

for (const p of provinces) {
  if (!p.geo) { skippedNoGeo.push(`province ${p.name}`); continue; }
  const [wLng, wLat] = gcj02ToWgs84(p.geo[0], p.geo[1]);
  convertedCount++;
  const { pinyin: pyFull, py: pyAbbr } = py(p.name);
  records.push({ name: p.name, province: p.name, lat: round4(wLat), lon: round4(wLng),
                 pinyin: pyFull, py: pyAbbr, level: 'province' });
}

for (const c of cities) {
  const provinceName = provinces.find((p) => p.id === c.pid)?.name ?? '';
  let lat, lon;
  if (c.pid === CHONGQING_PID) { chongqingMerged++; continue; }   // 重庆城区/重庆郊县 -> single 重庆市
  if (c.pid === TAIWAN_PID) {
    const t = taiwan[c.name];
    if (!t) { skippedNoGeo.push(`taiwan unit ${c.name}`); continue; }
    lat = t.lat; lon = t.lon; taiwanCount++;                       // Photon/OSM: already WGS-84
  } else {
    if (!c.geo) { skippedNoGeo.push(`city ${c.name} (${provinceName})`); continue; }
    const [wLng, wLat] = gcj02ToWgs84(c.geo[0], c.geo[1]);
    lat = round4(wLat); lon = round4(wLng); convertedCount++;
  }
  const { pinyin: pyFull, py: pyAbbr } = py(c.name);
  records.push({ name: c.name, province: provinceName, lat, lon,
                 pinyin: pyFull, py: pyAbbr, level: 'city' });
}

// 直辖市: add the municipality itself as a city-level record (from its province point)
for (const pid of MUNICIPALITIES) {
  const p = provinces.find((x) => x.id === pid);
  if (!p?.geo) continue;
  const [wLng, wLat] = gcj02ToWgs84(p.geo[0], p.geo[1]);
  const { pinyin: pyFull, py: pyAbbr } = py(p.name);
  records.push({ name: p.name, province: p.name, lat: round4(wLat), lon: round4(wLng),
                 pinyin: pyFull, py: pyAbbr, level: 'city' });
}

mkdirSync('/home/orlando/dingwei/app/src/main/assets', { recursive: true });
const json = JSON.stringify(records);
writeFileSync(OUT, json, 'utf8');

const nProv = records.filter((r) => r.level === 'province').length;
const nCity = records.filter((r) => r.level === 'city').length;
console.log(`\nWROTE ${OUT}`);
console.log(`total records: ${records.length} (province=${nProv}, city=${nCity})`);
console.log(`coords converted GCJ-02->WGS-84: ${convertedCount}; from Photon/OSM (WGS-84 raw): ${taiwanCount}`);
console.log(`chongqing pseudo-entries merged: ${chongqingMerged}`);
console.log(`file size: ${Buffer.byteLength(json)} bytes (${(Buffer.byteLength(json) / 1024).toFixed(1)} KiB)`);
if (skippedNoGeo.length) console.log('skipped (no coordinate):', skippedNoGeo.join(', '));
const dupNames = records.map((r) => r.name).filter((n, i, a) => a.indexOf(n) !== i);
console.log('duplicate names:', dupNames.length ? [...new Set(dupNames)].join(', ') : 'none');

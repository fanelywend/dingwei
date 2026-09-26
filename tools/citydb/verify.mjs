// Required verifications + tightened coordinate checks.
import { readFileSync, statSync } from 'node:fs';
import { haversineMetres } from './transform.mjs';

const OUT = '/home/orlando/dingwei/app/src/main/assets/cities.json';
const raw = readFileSync(OUT, 'utf8');
const data = JSON.parse(raw);
let pass = 0, fail = 0;
const ok = (c, msg) => { console.log(`${c ? '  PASS' : '  FAIL'}  ${msg}`); c ? pass++ : fail++; };

console.log('='.repeat(72));
console.log('VERIFICATION 1 - key city coordinate check');
console.log('  Reference set A (task spec, tolerance +/-0.3):');
const SET_A = [
  ['北京市', 39.90, 116.41], ['上海市', 31.23, 121.47], ['广州市', 23.13, 113.26],
  ['深圳市', 22.54, 114.06], ['成都市', 30.67, 104.07], ['乌鲁木齐市', 43.83, 87.62],
];
let a1 = 0;
for (const [name, lat, lon] of SET_A) {
  const r = data.find((x) => x.name === name && x.level === 'city');
  if (!r) { console.log(`  FAIL  ${name}: record not found`); fail++; continue; }
  const dLat = Math.abs(r.lat - lat), dLon = Math.abs(r.lon - lon);
  const good = dLat <= 0.3 && dLon <= 0.3;
  a1 += good ? 1 : 0;
  console.log(`  ${good ? 'PASS' : 'FAIL'}  ${name.padEnd(6)} actual ${r.lat},${r.lon}  expected ${lat},${lon}  dev lat=${dLat.toFixed(4)} lon=${dLon.toFixed(4)}  (${haversineMetres(r.lat, r.lon, lat, lon).toFixed(0)} m)`);
}
ok(a1 === SET_A.length, `spec set A: ${a1}/${SET_A.length} within +/-0.3`);

console.log('\n  Reference set B (parent agent, tightened tolerance +/-0.02):');
const SET_B = [
  ['北京市', 39.9042, 116.4074], ['上海市', 31.2304, 121.4737], ['广州市', 23.1291, 113.2644],
  ['深圳市', 22.5431, 114.0579], ['成都市', 30.5728, 104.0668], ['乌鲁木齐市', 43.8256, 87.6168],
];
let b1 = 0;
for (const [name, lat, lon] of SET_B) {
  const r = data.find((x) => x.name === name && x.level === 'city');
  if (!r) { console.log(`  FAIL  ${name}: not found`); fail++; continue; }
  const dLat = Math.abs(r.lat - lat), dLon = Math.abs(r.lon - lon);
  const good = dLat <= 0.02 && dLon <= 0.02;
  b1 += good ? 1 : 0;
  console.log(`  ${good ? 'PASS' : 'FAIL'}  ${name.padEnd(6)} actual ${r.lat},${r.lon}  ref ${lat},${lon}  dev lat=${dLat.toFixed(4)} lon=${dLon.toFixed(4)}  (${haversineMetres(r.lat, r.lon, lat, lon).toFixed(0)} m)`);
}
ok(b1 === SET_B.length, `tightened set B: ${b1}/${SET_B.length} within +/-0.02`);

console.log('\n' + '='.repeat(72));
console.log('VERIFICATION 2 - China bounds (lat 3~54, lon 73~136)');
const out = data.filter((r) => !(r.lat >= 3 && r.lat <= 54 && r.lon >= 73 && r.lon <= 136));
console.log(`  out-of-range records: ${out.length}`);
if (out.length) for (const r of out.slice(0, 20)) console.log(`    ${r.name} (${r.province}) lat=${r.lat} lon=${r.lon}`);
ok(out.length === 0, `0 out-of-range records required, got ${out.length}`);
const lats = data.map((r) => r.lat), lons = data.map((r) => r.lon);
console.log(`  lat range: ${Math.min(...lats)} .. ${Math.max(...lats)}`);
console.log(`  lon range: ${Math.min(...lons)} .. ${Math.max(...lons)}`);

console.log('\n' + '='.repeat(72));
console.log('VERIFICATION 3 - record count (>= 300)');
const nProv = data.filter((r) => r.level === 'province').length;
const nCity = data.filter((r) => r.level === 'city').length;
console.log(`  total: ${data.length}   provinces(level=province): ${nProv}   second-level(level=city): ${nCity}`);
ok(data.length >= 300, `total ${data.length} >= 300`);
ok(nProv === 34, `34 province-level records, got ${nProv}`);
const provinces = new Set(data.filter((r) => r.level === 'city').map((r) => r.province));
console.log(`  distinct provinces covering city records: ${provinces.size}`);

console.log('\n' + '='.repeat(72));
console.log('VERIFICATION 4 - spot check 3 complete records');
const picks = ['北京市', '深圳市', '黔西南布依族苗族自治州'];
for (const p of picks) {
  const r = data.find((x) => x.name === p);
  console.log('  ' + JSON.stringify(r));
}
const empties = data.filter((r) => !r.pinyin || !r.py);
console.log(`  records with empty pinyin/py: ${empties.length}`);
ok(empties.length === 0, 'all pinyin/py fields non-empty');
const badLevel = data.filter((r) => r.level !== 'province' && r.level !== 'city');
ok(badLevel.length === 0, 'all level values are "province" or "city"');
const missing = data.filter((r) => !r.name || !r.province || typeof r.lat !== 'number' || typeof r.lon !== 'number');
ok(missing.length === 0, 'all records have name/province/lat/lon');
const nonAscii = data.filter((r) => /[^a-z]/.test(r.pinyin) || /[^a-z]/.test(r.py));
ok(nonAscii.length === 0, 'pinyin/py are lowercase a-z only');

console.log('\n' + '='.repeat(72));
console.log('VERIFICATION 5 - valid JSON / file properties');
const bytes = statSync(OUT).size;
console.log(`  file: ${OUT}`);
console.log(`  size: ${bytes} bytes (${(bytes / 1024).toFixed(1)} KiB), under 500KiB: ${bytes < 500 * 1024}`);
ok(bytes < 500 * 1024, 'size < 500 KiB');
ok(Array.isArray(data), 'top level is a JSON array');
ok(raw.startsWith('[{') && raw.endsWith('}]'), 'compact JSON (no extra whitespace/indentation)');
const compact = JSON.stringify(data) === raw;
ok(compact, 're-serialising gives identical bytes (canonical compact form)');

console.log('\n' + '='.repeat(72));
console.log(`RESULT: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

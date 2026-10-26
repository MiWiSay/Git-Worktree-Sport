import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const descriptor = fs.readFileSync(path.join(root, 'src/main/resources/META-INF/plugin.xml'), 'utf8').replace(/\r\n/g, '\n');
const embedded = descriptor.match(/<description><!\[CDATA\[\s*([\s\S]*?)\s*\]\]><\/description>/)?.[1];
assert(embedded?.trim(), 'Plugin descriptor contains an Overview description');
assert(!descriptor.includes('example.invalid'), 'Placeholder vendor contact removed');
assert(descriptor.includes('<id>com.miwi.talkinas.gitworktree</id>'), 'Existing installed plugin identity remains stable');
assert(descriptor.includes('<version>1.0.4</version>'), 'Plugin descriptor carries release version 1.0.4');
const overviewRoot = path.join(root, 'docs/overview');
const descriptionPath = path.join(overviewRoot, 'description.html');
const guidePath = path.join(overviewRoot, 'index.html');
let overviewChecksAvailable = false;
if (fs.existsSync(descriptionPath)) {
  overviewChecksAvailable = true;
  const description = fs.readFileSync(descriptionPath, 'utf8').replace(/\r\n/g, '\n');
  assert.equal(embedded.trim(), description.trim(), 'Packaged description matches maintained English source');
  assert(!/[\u3400-\u9fff]/.test(description), 'Default Overview does not concatenate localized descriptions');
  assert(!/<(?:script|iframe|select|button)\b/i.test(description), 'Marketplace description uses static HTML');
  assert(!/file:|localhost|127\.0\.0\.1|C:\\|D:\\|\.\.\/|example\.invalid/.test(description), 'No machine-specific paths in listing copy');
}
if (fs.existsSync(guidePath)) {
  overviewChecksAvailable = true;
  const guide = fs.readFileSync(guidePath, 'utf8');
  const refs = [...guide.matchAll(/<img\b[^>]*src="([^"]+)"/g)].map(match => match[1]);
  assert.equal(refs.length, 8, 'Seven screenshots and the approved banner are illustrated');
  for (const ref of refs) {
    assert(!path.isAbsolute(ref) && !ref.includes('..'), 'Guide image is portable');
    const asset = path.join(path.dirname(guidePath), ref);
    assert(fs.statSync(asset).size > 1000, `Image is present: ${ref}`);
    assert(fs.readFileSync(asset).subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10])), `Valid PNG signature: ${ref}`);
  }
  for (const [, target] of guide.matchAll(/href="#([^\"]+)"/g)) {
    assert(guide.includes(`id="${target}"`), `Guide navigation anchor exists: ${target}`);
  }
  assert(!guide.includes('banner-candidate'), 'Unapproved AI candidate is not integrated');
}
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'src/main/resources/i18n/languages.json'), 'utf8'));
assert.equal(Object.keys(manifest).length, 10, 'Documentation pack count matches runtime catalog');
console.log(overviewChecksAvailable
  ? 'PASS: plugin descriptor, English Overview sources, seven portable screenshots, approved banner, navigation and language count'
  : 'PASS: plugin descriptor and language count; local-only Overview sources are absent and their checks were skipped');

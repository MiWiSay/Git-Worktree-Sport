import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const descriptor = fs.readFileSync(path.join(root, 'src/main/resources/META-INF/plugin.xml'), 'utf8');
const deps = [...descriptor.matchAll(/<depends(?:\s[^>]*)?>([^<]+)<\/depends>/g)].map(m => m[1]);
assert.deepEqual(deps, ['com.intellij.modules.platform', 'Git4Idea'],
  'Installation requirements remain platform/Git based, not a product-name whitelist');
const codeRoot = path.join(root, 'src/main/java');
function sources(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory()
    ? sources(path.join(dir, e.name)) : e.name.endsWith('.java') ? [path.join(dir, e.name)] : []);
}
for (const file of sources(codeRoot)) {
  const code = fs.readFileSync(file, 'utf8');
  assert(!/scheduleAtFixedRate\s*\(/.test(code), `${file}: no fixed-rate IDE scheduling`);
  assert(!/PlatformUtils\.(?:isIdea|isPyCharm|isWebStorm|isDataGrip|isAndroidStudio)/.test(code),
    `${file}: no product-specific gate`);
  assert(!/getProductCode\s*\(/.test(code), `${file}: no product-code whitelist`);
}
const settings = fs.readFileSync(path.join(codeRoot, 'com/miwi/gitworktree/ui/WorktreeSettingsDialog.java'), 'utf8');
for (const mask of ['CTRL_DOWN_MASK', 'META_DOWN_MASK']) {
  assert(settings.includes(`KeyEvent.VK_F, InputEvent.${mask}`), `Find supports ${mask}`);
}
assert(!settings.includes('showMainProtectedActionsCheckBox'), 'Retired setting is not shown');
const panel = fs.readFileSync(path.join(codeRoot, 'com/miwi/gitworktree/ui/WorktreesPanel.java'), 'utf8');
assert(panel.indexOf('add(menu, "branch.createFrom"') < panel.indexOf('add(menu, "action.openWorktree"'),
  'Create-from-branch is the first worktree row action');
assert(panel.includes('!entry.detached()') && panel.includes('new GitLocalBranch(entry.branch())'),
  'Derived creation uses the selected row branch, not the current IDE branch or detached placeholder');
const form = fs.readFileSync(path.join(codeRoot, 'com/miwi/gitworktree/ui/CreateWorktreeForm.java'), 'utf8');
assert(!form.includes('I18n.text("create.mode")'), 'Removed mode heading has no UI consumer');
assert(form.includes('startBranch.setEditable(false)'), 'Default start branch cannot be edited as arbitrary text');
console.log('PASS: platform/Git dependency contract, no product gates, fixed-delay scheduling and cross-OS Find bindings');

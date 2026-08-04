import { readFileSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const resDir = resolve(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'mipmap-anydpi-v26');
const files = ['ic_launcher.xml', 'ic_launcher_round.xml'];

// capacitor-assets always wraps BOTH the background and foreground adaptive
// icon layers in a 16.7% <inset>, hardcoded in its own template with no flag
// to turn it off (see node_modules/@capacitor/assets/dist/platforms/android/index.js).
// That's correct for the foreground (Android's safe-zone convention), but
// wrong for the background: an inset background leaves a transparent ring
// between its edge and the mask Android/the launcher actually crops to,
// which shows through as a thin white border once the icon is installed.
// The background layer is a flat colour and is meant to bleed to the full
// 108dp canvas, so this rewrites it to a plain, uninset drawable reference
// after every `capacitor-assets generate --android` run.
const insetBackground = /<background>\s*<inset android:drawable="@mipmap\/ic_launcher_background" android:inset="[^"]*"\s*\/>\s*<\/background>/;
const flatBackground = '<background android:drawable="@mipmap/ic_launcher_background"/>';

for (const file of files) {
  const path = resolve(resDir, file);
  const xml = readFileSync(path, 'utf8');
  if (!insetBackground.test(xml)) {
    console.error(`${file}: expected inset <background> block not found — capacitor-assets output may have changed, check by hand.`);
    process.exit(1);
  }
  writeFileSync(path, xml.replace(insetBackground, flatBackground));
  console.log(`Fixed adaptive icon background inset in ${file}`);
}

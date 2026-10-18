import fs from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';

const res = path.resolve('android/app/src/main/res');
const iconSvg = fs.readFileSync(path.resolve('public/aph-mark.svg'));

const densitySizes = {
  mdpi: 48,
  hdpi: 72,
  xhdpi: 96,
  xxhdpi: 144,
  xxxhdpi: 192,
};

const foregroundSvg = (size) => Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 108 108">
  <path d="M35 59h38M54 40v38" stroke="#fff" stroke-width="8" stroke-linecap="round"/>
  <g transform="rotate(36 70 38)">
    <rect x="64" y="22" width="13" height="33" rx="6.5" fill="#f7e7bd"/>
    <path d="M64 38.5h13V49a6.5 6.5 0 0 1-13 0z" fill="#d9b56d"/>
  </g>
</svg>`);

const roundSvg = (size) => Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 128 128">
  <circle cx="64" cy="64" r="64" fill="#0b5c63"/>
  <path d="M32 68h64M64 36v64" stroke="#fff" stroke-width="12" stroke-linecap="round"/>
  <path d="M88 24c11 0 20 9 20 20s-9 20-20 20H72V24h16z" fill="#d9b56d"/>
  <path d="M72 24v40H56V44c0-11 9-20 16-20z" fill="#f7e7bd"/>
</svg>`);

for (const [density, size] of Object.entries(densitySizes)) {
  const folder = path.join(res, `mipmap-${density}`);
  await sharp(iconSvg).resize(size, size).png().toFile(path.join(folder, 'ic_launcher.png'));
  await sharp(roundSvg(size)).png().toFile(path.join(folder, 'ic_launcher_round.png'));
  const foregroundSize = Math.round(size * 2.25);
  await sharp(foregroundSvg(foregroundSize)).png().toFile(path.join(folder, 'ic_launcher_foreground.png'));
}

const splashFiles = [];
for (const directory of fs.readdirSync(res).filter((name) => name.startsWith('drawable'))) {
  const file = path.join(res, directory, 'splash.png');
  if (fs.existsSync(file)) splashFiles.push(file);
}

for (const file of splashFiles) {
  const metadata = await sharp(file).metadata();
  const width = metadata.width;
  const height = metadata.height;
  if (!width || !height) continue;
  const min = Math.min(width, height);
  const mark = Math.round(min * 0.27);
  const titleSize = Math.max(18, Math.round(min * 0.075));
  const subtitleSize = Math.max(7, Math.round(min * 0.021));
  const centerY = Math.round(height * 0.45);
  const splash = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
    <rect width="100%" height="100%" fill="#0b5c63"/>
    <circle cx="${width / 2}" cy="${centerY}" r="${mark * 0.83}" fill="none" stroke="#fff" stroke-opacity=".06" stroke-width="2"/>
    <g transform="translate(${(width - mark) / 2} ${centerY - mark / 2}) scale(${mark / 128})">
      <rect width="128" height="128" rx="34" fill="#fff" fill-opacity=".10" stroke="#fff" stroke-opacity=".16"/>
      <path d="M32 68h64M64 36v64" stroke="#fff" stroke-width="12" stroke-linecap="round"/>
      <path d="M88 24c11 0 20 9 20 20s-9 20-20 20H72V24h16z" fill="#d9b56d"/>
      <path d="M72 24v40H56V44c0-11 9-20 16-20z" fill="#f7e7bd"/>
    </g>
    <text x="50%" y="${centerY + mark * 0.77}" text-anchor="middle" fill="#fff" font-family="sans-serif" font-size="${titleSize}" font-weight="700" letter-spacing="${Math.max(1, titleSize * 0.05)}">APH</text>
    <text x="50%" y="${centerY + mark * 1.02}" text-anchor="middle" fill="#d9b56d" font-family="sans-serif" font-size="${subtitleSize}" font-weight="700" letter-spacing="${Math.max(1, subtitleSize * 0.12)}">ABDULMAJEEDPHARMA</text>
  </svg>`);
  await sharp(splash).png().toFile(`${file}.new`);
  fs.renameSync(`${file}.new`, file);
}

console.log('Generated APH Android launcher and splash assets.');

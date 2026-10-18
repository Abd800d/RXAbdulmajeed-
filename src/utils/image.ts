export async function optimizeImage(file: File, maxDimension = 1280, quality = 0.84): Promise<string> {
  const source = await createImageBitmap(file);
  const scale = Math.min(1, maxDimension / Math.max(source.width, source.height));
  const width = Math.max(1, Math.round(source.width * scale));
  const height = Math.max(1, Math.round(source.height * scale));
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('Image processing is unavailable.');
  context.drawImage(source, 0, 0, width, height);
  source.close();
  const usePng = file.type === 'image/png' && file.size < 600_000;
  return canvas.toDataURL(usePng ? 'image/png' : 'image/jpeg', usePng ? undefined : quality);
}

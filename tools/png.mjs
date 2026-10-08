/**
 * A PNG, decoded to RGBA pixels. No dependencies -- `node:zlib` -- and only what Chrome's `Page.captureScreenshot`
 * writes: 8-bit RGB or RGBA, non-interlaced. Anything else is refused by name rather than decoded wrongly.
 *
 * Moved here from the scratch comparer that proved P6's refactor moved no pixel (350 screenshots, decoded and compared
 * pixel for pixel, with a swap canary that it caught), when `surfaces.mjs` needed pixels too: axe cannot compute the
 * backdrop of text on a gradient, an image or under an overlap, and a node it cannot compute is measured here instead.
 */
import { inflateSync } from 'node:zlib';

export function decodePng(buffer) {
  const SIGNATURE = '89504e470d0a1a0a';
  if (buffer.subarray(0, 8).toString('hex') !== SIGNATURE) throw new Error('not a PNG');
  let offset = 8;
  let width = 0;
  let height = 0;
  let bitDepth = 0;
  let colourType = 0;
  let interlace = 0;
  const idat = [];
  while (offset < buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const type = buffer.toString('ascii', offset + 4, offset + 8);
    const data = buffer.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      bitDepth = data[8];
      colourType = data[9];
      interlace = data[12];
    } else if (type === 'IDAT') {
      idat.push(data);
    } else if (type === 'IEND') {
      break;
    }
    offset += 12 + length;
  }
  if (bitDepth !== 8 || interlace !== 0 || (colourType !== 6 && colourType !== 2)) {
    throw new Error(`unsupported PNG: depth ${bitDepth}, colour type ${colourType}, interlace ${interlace}`);
  }
  const channels = colourType === 6 ? 4 : 3;
  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * channels;
  const out = Buffer.alloc(width * height * 4);
  const previous = Buffer.alloc(stride);
  const current = Buffer.alloc(stride);
  for (let y = 0; y < height; y += 1) {
    const rowStart = y * (stride + 1);
    const filter = raw[rowStart];
    for (let x = 0; x < stride; x += 1) {
      const byte = raw[rowStart + 1 + x];
      const left = x >= channels ? current[x - channels] : 0;
      const up = previous[x];
      const upLeft = x >= channels ? previous[x - channels] : 0;
      let value;
      switch (filter) {
        case 0: value = byte; break;
        case 1: value = byte + left; break;
        case 2: value = byte + up; break;
        case 3: value = byte + ((left + up) >> 1); break;
        case 4: {
          const p = left + up - upLeft;
          const pa = Math.abs(p - left);
          const pb = Math.abs(p - up);
          const pc = Math.abs(p - upLeft);
          value = byte + (pa <= pb && pa <= pc ? left : pb <= pc ? up : upLeft);
          break;
        }
        default: throw new Error(`bad filter ${filter} on row ${y}`);
      }
      current[x] = value & 0xff;
    }
    for (let x = 0; x < width; x += 1) {
      const src = x * channels;
      const dst = (y * width + x) * 4;
      out[dst] = current[src];
      out[dst + 1] = current[src + 1];
      out[dst + 2] = current[src + 2];
      out[dst + 3] = channels === 4 ? current[src + 3] : 255;
    }
    current.copy(previous);
  }
  return { width, height, pixels: out };
}

const lin = (c) => {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
};
/** `lin` for every whole channel value, computed by `lin` itself -- so a lookup is the same float, not an approximation. */
const LIN = Array.from({ length: 256 }, (_, c) => lin(c));

/** WCAG relative luminance of an sRGB colour, 0–255 channels. */
export function luminance([r, g, b]) {
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

/** WCAG contrast ratio between two luminances. */
export const contrastRatio = (a, b) => (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);

/** A pixel whose channels moved by less than this in total, between the two photographs, was not touched by a glyph. */
const GLYPH_DELTA = 6;

/**
 * Text contrast measured on the PIXELS BEHIND THE GLYPHS. `shown` is a node's box photographed as it is; `hidden` is the
 * same box with its glyphs hidden (probe.mjs `hideText`). Every pixel the glyphs changed is a place a letter sits, and
 * `hidden` holds what is behind it there. The text colour is composited over each of those backdrop pixels by its alpha,
 * and the ratio reported is a LOW PERCENTILE over them -- the part of a gradient the text is hardest to read on, not its
 * average. Returns null when the two photographs do not match in size.
 *
 * 🔴 NEVER GUESS WHICH PIXELS ARE BACKGROUND -- AND THE WHOLE BOX IS NOT THE BACKGROUND EITHER.
 *   ① It once took one photograph WITH the text and called every pixel far from the text colour background. A small
 *     label with no padding is mostly anti-aliased glyph edges, so the third sweep read the membership card's plainly
 *     readable labels at 1.4:1 (the `__pix_tight` canary, 2.19 on that sampler).
 *   ② It then took the box with the text hidden as ALL backdrop, and read the card's status chip at 3:1: its gold ring,
 *     an inset box-shadow along the edge, lies under no letter but filled the low percentile (the `__pix_ring` canary).
 * 🔑 A GLYPH THAT CHANGES NO PIXEL IS INVISIBLE, and reads 1:1 -- never "nothing to measure" (the `__pix_invisible` canary).
 */
export function pixelContrast(shown, hidden, [fr, fg, fb, fa = 1], percentile = 0.1) {
  const { width, height, pixels } = hidden;
  if (shown.width !== width || shown.height !== height) return null;
  const ratios = [];
  // Opaque text composites to itself over every backdrop, so its luminance is the same for every pixel.
  const opaqueText = fa === 1 ? luminance([fr, fg, fb]) : null;
  for (let i = 0; i < width * height * 4; i += 4) {
    const r = pixels[i];
    const g = pixels[i + 1];
    const b = pixels[i + 2];
    if (Math.abs(shown.pixels[i] - r) + Math.abs(shown.pixels[i + 1] - g) + Math.abs(shown.pixels[i + 2] - b) < GLYPH_DELTA) continue;
    const text = opaqueText ?? luminance([fr * fa + r * (1 - fa), fg * fa + g * (1 - fa), fb * fa + b * (1 - fa)]);
    ratios.push(contrastRatio(text, 0.2126 * LIN[r] + 0.7152 * LIN[g] + 0.0722 * LIN[b]));
  }
  if (!ratios.length) return { ratio: 1, samples: 0, invisible: true };
  ratios.sort((x, y) => x - y);
  return { ratio: Number(ratios[Math.floor((ratios.length - 1) * percentile)].toFixed(2)), samples: ratios.length };
}

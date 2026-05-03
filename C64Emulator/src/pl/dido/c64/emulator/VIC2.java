package pl.dido.c64.emulator;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import pl.dido.pal.FastPALcodec;

public class VIC2 {

	public enum MODE {
		STANDARD_CHAR_MODE, MULTICOLOR_CHAR_MODE, STANDARD_BITMAP_MODE, MULTICOLOR_BITMAP_MODE, EXTENDED_COLOR_MODE
	};

	// --- IRQ bits (D019/D01A)
	private static final int IRQ_RASTER = 0x01; // bit0
	private static final int IRQ_LINE = 0x80;   // bit7 w D019 (status/pending)

	public static boolean IRQ;

	private static int beam, oldBeam; // raster
	private static int line;

	private static int index;
	private static int bit;

	private static int screen_address; // text screen start offset
	private static int chargen_address; // chargen offset
	private static int graphics_address; // graphic screen offset

	private static final int w_width = 403;
	private static final int w_height = 284;

	private static final int r_width = 504;
	private static final int r_height = 312;

	private static final int window_size = w_width * w_height - 1;
	private static final int raster_size = r_width * r_height - 1;

	// screen pixels
	private static int pixels[];
	public static boolean bad_line; // bad line marker

	public static int offset; // VIC bank
	private static int vicCycle;

	private static int oy, om, och, ocode;
	private static int code, pen;

	// multicolor text
	private static boolean hiresChar, cc; // hires char in multicolor text mode - cc - color index calculated
	private static int ci, cti; // color index, color table index

	// hires
	private static int bc = 0;
	private static final int colorTable[] = new int[4];

	private static Thread screen;

	// screen pointers
	private static int code_ptr[] = { 0, 40, 80, 120, 160, 200, 240, 280, 320, 360, 400, 440, 480, 520, 560, 600, 640,
			680, 720, 760, 800, 840, 880, 920, 960 };

	// C64 default colors
	private final static int colors[] = new int[] { 0, 0xffffff, 0x813338, 0x75cec8, 0x8e3c97, 0x56ac4d, 0x2e2c9b,
			0xedf171, 0x8e5029, 0x553800, 0xc46c71, 0x4a4a4a, 0x7b7b7b, 0xa9ff9f, 0x706deb, 0xb2b2b2 };

	// =========================
	// SPRITES (VIC->raster mapping)
	// =========================

	// Obszar centrum (tak jak u Ciebie)
	private static final int CENTER_X1 = 90;
	private static final int CENTER_X2 = 409;
	private static final int CENTER_Y1 = 56;
	private static final int CENTER_Y2 = 255;

	// --- BADLINE timing helpers ---
	private static final int VIC_CYCLES_PER_LINE = 63; // PAL
	private static final int BADLINE_CYCLE_START = 15; // inclusive
	private static final int BADLINE_CYCLE_END = 54;   // inclusive (40 cykli)

	private static final int BADLINE_Y_MIN = CENTER_Y1;
	private static final int BADLINE_Y_MAX = CENTER_Y2;

	/**
	 * Mapowanie VIC sprite coords -> Twoje współrzędne rastra.
	 */
	private static final int VIC_SPR_X0_RASTER = 66;
	private static final int VIC_SPR_Y0_RASTER = 6;

	// --- Sprite regs (ports[] index == $D000..)
	private static final int SPR_XY = 0x00; // D000..D00F
	private static final int SPR_XMSB = 0x10; // D010
	private static final int SPR_EN = 0x15; // D015
	private static final int SPR_YEXP = 0x17; // D017
	private static final int SPR_PRIO = 0x1B; // D01B
	private static final int SPR_MCEN = 0x1C; // D01C
	private static final int SPR_XEXP = 0x1D; // D01D

	private static final int SPR_MC1 = 0x25; // D025
	private static final int SPR_MC2 = 0x26; // D026
	private static final int SPR_COL0 = 0x27; // D027..D02E

	private static final int SPR_PTR_BASE = 0x03F8; // screen + 3F8..3FF
	private static final int SPR_W = 24;
	private static final int SPR_H = 21;

	// Per-line sprite cache
	private static int cachedLine = -1;
	private static int lineSpriteCount = 0;

	private static final int[] lsMask = new int[8];
	private static final int[] lsSXr = new int[8];
	private static final boolean[] lsEX = new boolean[8];
	private static final boolean[] lsMC = new boolean[8];
	private static final boolean[] lsPR = new boolean[8];

	private static final int[] lsCol = new int[8];
	private static final int[] lsB0 = new int[8];
	private static final int[] lsB1 = new int[8];
	private static final int[] lsB2 = new int[8];

	private static int lsMC1, lsMC2;
	private static int spriteHitMask;

	private static boolean isBadlineRaster(final int rasterLine) {
		final int d011 = Memory.ports[0x11] & 0xFF;
		final boolean den = (d011 & 0x10) != 0; // DEN
		
		final int yscroll = d011 & 0x07;
		final boolean inWindow = (rasterLine >= BADLINE_Y_MIN && rasterLine <= BADLINE_Y_MAX);
		
		return den && inWindow && ((rasterLine & 0x07) == yscroll);
	}

	private static final boolean isBgTransparent(final int bgRgb) {
		final int globalBg = colors[Memory.ports[0x21] & 0x0F]; // $D021
		
		return bgRgb == globalBg;
	}

	private static void buildSpriteLineCache(final int currentLine) {
		if (cachedLine == currentLine)
			return;

		cachedLine = currentLine;
		lineSpriteCount = 0;

		final int en = Memory.ports[SPR_EN] & 0xFF;
		if (en == 0)
			return;

		final int xmsb = Memory.ports[SPR_XMSB] & 0xFF;
		final int xexp = Memory.ports[SPR_XEXP] & 0xFF;
		
		final int yexp = Memory.ports[SPR_YEXP] & 0xFF;
		final int mcen = Memory.ports[SPR_MCEN] & 0xFF;
		final int prio = Memory.ports[SPR_PRIO] & 0xFF;

		lsMC1 = colors[Memory.ports[SPR_MC1] & 0x0F];
		lsMC2 = colors[Memory.ports[SPR_MC2] & 0x0F];

		for (int i = 7; i >= 0; i--) {
			final int mask = 1 << i;
			
			if ((en & mask) == 0)
				continue;

			int sx = Memory.ports[SPR_XY + i * 2] & 0xFF;
			final int sy = Memory.ports[SPR_XY + i * 2 + 1] & 0xFF;
			
			if ((xmsb & mask) != 0)
				sx |= 0x100;

			final int sxRaster = sx + VIC_SPR_X0_RASTER;
			final int syRaster = sy + VIC_SPR_Y0_RASTER;

			final boolean ey = (yexp & mask) != 0;
			int ly = currentLine - syRaster;
			if (ey)
				ly >>= 1;

			if (ly < 0 || ly >= SPR_H)
				continue;

			final int ptr = Memory.fetchVIC2(screen_address + SPR_PTR_BASE + i) & 0xFF;
			final int spriteBase = offset + (ptr << 6);
			final int rowAddr = spriteBase + ly * 3;

			final int idx = lineSpriteCount++;
			lsMask[idx] = mask;
			lsSXr[idx] = sxRaster;
			
			lsEX[idx] = (xexp & mask) != 0;
			lsMC[idx] = (mcen & mask) != 0;
			lsPR[idx] = (prio & mask) != 0;

			lsCol[idx] = colors[Memory.ports[SPR_COL0 + i] & 0x0F];
			lsB0[idx] = Memory.fetchVIC2(rowAddr) & 0xFF;
			
			lsB1[idx] = Memory.fetchVIC2(rowAddr + 1) & 0xFF;
			lsB2[idx] = Memory.fetchVIC2(rowAddr + 2) & 0xFF;

			if (lineSpriteCount == 8)
				break;
		}
	}

	private static int spriteOverlayFast(final int column, final int currentLine, final int bgRgb) {
		if (lineSpriteCount == 0)
			return -1;

		spriteHitMask = 0;

		for (int n = 0; n < lineSpriteCount; n++) {
			if (lsPR[n] && !isBgTransparent(bgRgb))
				continue;

			int lx = column - lsSXr[n];
			if (lsEX[n])
				lx >>= 1;

			if (lx < 0 || lx >= SPR_W)
				continue;

			final int mask = lsMask[n];

			if (!lsMC[n]) {
				final int byteVal = (lx < 8) ? lsB0[n] : (lx < 16) ? lsB1[n] : lsB2[n];
				final int bitMask = 1 << (7 - (lx & 7));
				
				if ((byteVal & bitMask) == 0)
					continue;

				if (spriteHitMask != 0)
					Memory.ram[0xD01E] |= (spriteHitMask | mask);
				
				spriteHitMask |= mask;

				if (!isBgTransparent(bgRgb))
					Memory.ram[0xD01F] |= mask;

				return lsCol[n];
			} else {
				final int pair = lx >> 1;
				final int v = (pair < 4) ? lsB0[n] : (pair < 8) ? lsB1[n] : lsB2[n];
				
				final int shift = 6 - 2 * (pair & 3);
				final int two = (v >> shift) & 0x03;
				
				if (two == 0)
					continue;

				if (spriteHitMask != 0)
					Memory.ram[0xD01E] |= (spriteHitMask | mask);
				spriteHitMask |= mask;

				if (!isBgTransparent(bgRgb))
					Memory.ram[0xD01F] |= mask;

				if (two == 1)
					return lsMC1;
				
				if (two == 2)
					return lsCol[n];
				
				return lsMC2;
			}
		}
		return -1;
	}

	public static final void initialize(final Canvas canvas) {
		final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		final GraphicsDevice device = env.getDefaultScreenDevice();
		
		final GraphicsConfiguration config = device.getDefaultConfiguration();
		final BufferedImage image = config.createCompatibleImage(w_width, w_height);

		pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		reset();

		screen = new CRTThread(canvas, image);
		screen.start();
	}
	
	public static final void clock(final int cycles) {
		vicCycle += cycles;

		Memory.ram[0xd011] = Memory.ports[0x11];
		Memory.ram[0xd016] = Memory.ports[0x16];

		final int ack = Memory.ports[0x19] & 0x0f;
		if (ack != 0) {
			Memory.ram[0xD019] &= ~ack;
			Memory.ports[0x19] = 0;
		}

		Memory.ram[0xd01a] = Memory.ports[0x1a] & 0x0f;

		if (vicCycle >= VIC_CYCLES_PER_LINE) {
			vicCycle -= VIC_CYCLES_PER_LINE;
			
			line = (line + 1) % 312; // PAL
			
			final int lo = line & 0xff;
			final int hi = (line & 0x100) >> 1;
			
			Memory.ram[0xd012] = lo;
			Memory.ram[0xd011] = Memory.ram[0xd011] & 0x7f | hi;
			
			if (Memory.ports[0x12] == lo && (Memory.ports[0x11] & 0x80) == hi)
				Memory.ram[0xd019] |= IRQ_RASTER;
		}
		
		bad_line = isBadlineRaster(line) && vicCycle >= BADLINE_CYCLE_START && vicCycle <= BADLINE_CYCLE_END;
		
		final int sources = Memory.ram[0xd019] & 0x0f;
		final int mask = Memory.ram[0xd01a] & 0x0f;

		final boolean pending = (sources & mask) != 0;
		Memory.ram[0xd019] = sources | (pending ? IRQ_LINE : 0);
		IRQ = pending;

		Memory.ram[0xd020] = Memory.ports[0x20];
		Memory.ram[0xd021] = Memory.ports[0x21];

		final int bank = Memory.ports[0x18];
		if (Memory.ram[0xd018] != bank) {
			
			Memory.ram[0xd018] = bank;
			chargen_address = ((bank & 0x0E) << 10) + offset;
			
			screen_address = ((bank & 0xF0) << 6) + offset;
			graphics_address = ((bank & 0x08) == 0) ? offset : offset + 0x2000;
		}

		updateScreen(cycles);
		oldBeam = beam;
	}

	public static final void updateScreen(final int cycles) {
		switch (screenMode()) {
		case STANDARD_CHAR_MODE:
			updateScreenStardardCharacter(cycles);
			break;
		case MULTICOLOR_CHAR_MODE:
			updateScreenMulticolorCharater(cycles);
			break;
		case STANDARD_BITMAP_MODE:
			updateScreenStardardBitmap(cycles);
			break;
		case MULTICOLOR_BITMAP_MODE:
			updateScreenMulticolorBitmap(cycles);
			break;
		default:
			throw new RuntimeException("Screen mode not implemented!!");
		}
	}

	public static final void updateScreenStardardCharacter(final int cycles) {
		beam += cycles << 3;
		
		if (beam > raster_size)
			beam -= raster_size;

		int position = oldBeam;
		
		final int fc = colors[Memory.ports[0x20] & 0xf];
		final int bcLocal = colors[Memory.ports[0x21] & 0xf];

		do {
			final int renderLine = position / r_width;
			if (renderLine > 13 && renderLine < 298) {
				
				final int column = position % r_width;
				final int py = renderLine - CENTER_Y1;
				final int row = py & 0b111;

				if (column > 49 && column < 453) {
					if (renderLine >= CENTER_Y1 && renderLine <= CENTER_Y2 && column >= CENTER_X1 && column <= CENTER_X2) {

						buildSpriteLineCache(renderLine);
						final int m = code_ptr[py >> 3] + ((column - CENTER_X1) >> 3);

						if (m != om) {
							code = Memory.fetchVIC2(screen_address + m);
							pen = colors[(Memory.ram[0xd800 + m]) & 0xf];
						}

						final int ch = (code == ocode && oy == py) ? och
								: Memory.fetchVIC2(chargen_address + ((code << 3) + row));

						oy = py;
						och = ch;
						
						om = m;
						ocode = code;

						final int bg = (ch & bit) != 0 ? pen : bcLocal;
						final int spr = spriteOverlayFast(column, renderLine, bg);
						
						pixels[index] = (spr != -1) ? spr : bg;
						bit = bit == 1 ? 128 : bit >> 1;
					} else {
						pixels[index] = fc;
					}

					if (index < window_size)
						index++;
					else
						index = 0;
				}
			}

			if (position < raster_size)
				position++;
			else
				position = 0;

		} while (position != beam);

		oldBeam = beam;
	}

	public static final void updateScreenStardardBitmap(final int cycles) {
		beam += cycles << 3;
		
		if (beam > raster_size)
			beam -= raster_size;

		int position = oldBeam;
		final int fc = colors[Memory.ports[0x20] & 0xf];

		do {
			final int renderLine = position / r_width;
			if (renderLine > 13 && renderLine < 298) {
				
				final int column = position % r_width;
				final int py = renderLine - CENTER_Y1;
				final int row = py & 0b111;

				if (column > 49 && column < 453) {
					if (renderLine >= CENTER_Y1 && renderLine <= CENTER_Y2 && column >= CENTER_X1 && column <= CENTER_X2) {

						buildSpriteLineCache(renderLine);

						final int px = column - CENTER_X1;
						final int m = code_ptr[py >> 3] + (px >> 3);

						if (m != om) {
							code = Memory.fetchVIC2(screen_address + m);
							bc = colors[code & 0xf];
							pen = colors[(code & 0xf0) >> 4];
						}

						final int ch = (om != m || py != oy) ? Memory.fetchVIC2(graphics_address + ((m << 3) + row)) : och;

						oy = py;
						och = ch;
						
						om = m;
						ocode = code;

						final int bg = (ch & bit) != 0 ? pen : bc;
						final int spr = spriteOverlayFast(column, renderLine, bg);

						pixels[index] = (spr != -1) ? spr : bg;
						bit = bit == 1 ? 128 : bit >> 1;
					} else {
						pixels[index] = fc;
					}

					if (index < window_size)
						index++;
					else
						index = 0;
				}
			}

			if (position < raster_size)
				position++;
			else
				position = 0;

		} while (position != beam);

		oldBeam = beam;
	}

	public static final void updateScreenMulticolorCharater(final int cycles) {
		beam += cycles << 3;
		
		if (beam > raster_size)
			beam -= raster_size;

		int position = oldBeam;
		final int fc = colors[Memory.ports[0x20] & 0xf];
		final int bcLocal = colors[Memory.ports[0x21] & 0xf];

		final int fc1 = colors[Memory.ports[0x22] & 0xf];
		final int fc2 = colors[Memory.ports[0x23] & 0xf];

		colorTable[0] = bcLocal;
		colorTable[1] = fc1;
		colorTable[2] = fc2;

		do {
			final int renderLine = position / r_width;
			if (renderLine > 13 && renderLine < 298) {
				
				final int column = position % r_width;
				final int py = renderLine - CENTER_Y1;
				final int row = py & 0b111;

				if (column > 49 && column < 453) {
					if (renderLine >= CENTER_Y1 && renderLine <= CENTER_Y2 && column >= CENTER_X1 && column <= CENTER_X2) {

						buildSpriteLineCache(renderLine);
						final int m = code_ptr[py >> 3] + ((column - CENTER_X1) >> 3);
						
						if (m != om) {
							code = Memory.fetchVIC2(screen_address + m);
							cti = Memory.ram[0xd800 + m] & 0xf;

							pen = colors[cti];
							hiresChar = (cti & 8) == 0;

							colorTable[3] = colors[cti & 0b111];
							ci = 0;
							cc = false;
						}

						final int ch = (code == ocode && oy == py) ? och
								: Memory.fetchVIC2(chargen_address + ((code << 3) + row));

						oy = py;
						och = ch;
						om = m;
						ocode = code;

						if (hiresChar) {
							final int bg = (ch & bit) == 0 ? bcLocal : pen;
							final int spr = spriteOverlayFast(column, renderLine, bg);
							
							pixels[index] = (spr != -1) ? spr : bg;
						} else {
							ci = (ci << 1) | ((ch & bit) == 0 ? 0 : 1);
							if (cc) {
								final int cl = colorTable[ci];

								final int spr1 = spriteOverlayFast(column - 1, renderLine, cl);
								pixels[index - 1] = (spr1 != -1) ? spr1 : cl;

								final int spr2 = spriteOverlayFast(column, renderLine, cl);
								pixels[index] = (spr2 != -1) ? spr2 : cl;

								ci = 0;
							}
							cc = !cc;
						}

						bit = bit == 1 ? 128 : bit >> 1;
					} else {
						pixels[index] = fc;
					}

					if (index < window_size)
						index++;
					else
						index = 0;
				}
			}

			if (position < raster_size)
				position++;
			else
				position = 0;

		} while (position != beam);

		oldBeam = beam;
	}

	public static final void updateScreenMulticolorBitmap(final int cycles) {
		beam += cycles << 3;
		
		if (beam > raster_size)
			beam -= raster_size;

		int position = oldBeam;

		colorTable[0] = colors[Memory.ports[0x21] & 0xf];
		final int fc = colors[Memory.ports[0x20] & 0xf];

		do {
			final int renderLine = position / r_width;
			if (renderLine > 13 && renderLine < 298) {
				
				final int column = position % r_width;
				final int py = renderLine - CENTER_Y1;
				final int row = py & 0b111;

				if (column > 49 && column < 453) {
					if (renderLine >= CENTER_Y1 && renderLine <= CENTER_Y2 && column >= CENTER_X1 && column <= CENTER_X2) {
						buildSpriteLineCache(renderLine);
						
						final int m = code_ptr[py >> 3] + ((column - CENTER_X1) >> 3);
						if (m != om) {
							code = Memory.fetchVIC2(screen_address + m);
							colorTable[1] = colors[(code & 0xf0) >> 4];
							colorTable[2] = colors[code & 0xf];
							colorTable[3] = colors[Memory.ram[0xd800 + m] & 0xf];

							ci = 0;
							cc = false;
						}

						final int ch = (om != m || py != oy) ? Memory.fetchVIC2(graphics_address + ((m << 3) + row)) : och;

						oy = py;
						och = ch;
						
						om = m;
						ocode = code;

						ci = (ci << 1) | ((ch & bit) == 0 ? 0 : 1);
						if (cc) {
							final int cl = colorTable[ci];

							final int spr1 = spriteOverlayFast(column - 1, renderLine, cl);
							pixels[index - 1] = (spr1 != -1) ? spr1 : cl;

							final int spr2 = spriteOverlayFast(column, renderLine, cl);
							pixels[index] = (spr2 != -1) ? spr2 : cl;

							ci = 0;
						}

						cc = !cc;
						bit = bit == 1 ? 128 : bit >> 1;
					} else {
						pixels[index] = fc;
					}

					if (index < window_size)
						index++;
					else
						index = 0;
				}
			}

			if (position < raster_size)
				position++;
			else
				position = 0;

		} while (position != beam);

		oldBeam = beam;
	}

	public static final MODE screenMode() {
		switch ((Memory.ports[0x11] & 96) | (Memory.ports[0x16] & 16)) {
		case 0:
			return MODE.STANDARD_CHAR_MODE;
		case 16:
			return MODE.MULTICOLOR_CHAR_MODE;
		case 32:
			return MODE.STANDARD_BITMAP_MODE;
		case 48:
			return MODE.MULTICOLOR_BITMAP_MODE;
		case 64:
			return MODE.EXTENDED_COLOR_MODE;
		default:
			return MODE.STANDARD_CHAR_MODE;
		}
	}

	public static void reset() {
		IRQ = false;

		beam = 0;
		oldBeam = 0;
		
		vicCycle = 0;
		line = 0;

		index = 0;
		bit = 128;

		screen_address = 0;
		chargen_address = 0;
		graphics_address = 0;

		bad_line = false;
		offset = 0;

		oy = -1;
		om = -1;

		och = -1;
		ocode = -1;

		code = 0;
		pen = 0;

		hiresChar = false;
		cc = false;

		ci = 0;
		cti = 0;

		bc = 0;

		cachedLine = -1;
		lineSpriteCount = 0;
		spriteHitMask = 0;

		for (int i = 0; i < 0x2e; i++)
			Memory.ports[i] = 0;

		Memory.ports[0x11] = 0x1b;
		Memory.ports[0x16] = 0x08;
		Memory.ports[0x18] = 0x20;
	}
}

class ScreenThread extends Thread {
	private Canvas canvas;
	private boolean running = true;
	private BufferedImage image;

	public ScreenThread(final Canvas canvas, final BufferedImage image) {
		this.canvas = canvas;
		this.image = image;
	}

	public void run() {
		final BufferStrategy bufferStrategy = canvas.getBufferStrategy();
		final Graphics gfx = bufferStrategy.getDrawGraphics();

		final int cw = canvas.getWidth();
		final int ch = canvas.getHeight();

		final int iw = image.getWidth();
		final int ih = image.getHeight();

		try {
			while (running) {
				long t = System.currentTimeMillis();

				gfx.drawImage(image, 0, 0, cw, ch, 0, 0, iw, ih, null);
				bufferStrategy.show();

				final long d = System.currentTimeMillis();
				t = 20 - (d - t);
				
				t = t > 0 ? t : 20 - t;
				Thread.sleep(t);
			}
		} catch (final InterruptedException e) {
			running = false;
		} finally {
			gfx.dispose();
		}
	}
}

class CRTThread extends Thread {
	private Canvas canvas;
	private boolean running = true;
	private BufferedImage image;

	public CRTThread(final Canvas canvas, final BufferedImage image) {
		this.canvas = canvas;
		this.image = image;
	}

	public void run() {
		final BufferStrategy bufferStrategy = canvas.getBufferStrategy();
		final Graphics gfx = bufferStrategy.getDrawGraphics();

		final int iw = 720;
		final int ih = 576;

		final BufferedImage crt = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
		final int src[] = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		final int dst[] = ((DataBufferInt) crt.getRaster().getDataBuffer()).getData();

		FastPALcodec.init(image.getWidth(), image.getHeight(), src, dst);

		try {
			while (running) {
				long t = System.currentTimeMillis();

				FastPALcodec.encodeYC();
				FastPALcodec.decodeYC();

				final int cw = canvas.getWidth();
				final int ch = canvas.getHeight();
				
				// perfect PAL: zachowaj aspect sygnału 720x576 (5:4)
				final double targetAspect = (double) iw / (double) ih; // 1.25
				final double canvasAspect = (double) cw / (double) ch;

				int dw, dh, dx, dy;

				if (canvasAspect > targetAspect) {
				    // canvas za szeroki -> pasy po bokach
				    dh = ch;
				    dw = (int) Math.round(ch * targetAspect);
				    
				    dx = (cw - dw) / 2;
				    dy = 0;
				} else {
				    // canvas za wysoki -> pasy góra/dół
				    dw = cw;
				    dh = (int) Math.round(cw / targetAspect);
				    
				    dx = 0;
				    dy = (ch - dh) / 2;
				}
				
				gfx.drawImage(crt, dx, dy, dw, dh, 0, 0, iw, ih, null);
				bufferStrategy.show();

				final long d = System.currentTimeMillis();
				t = 20 - (d - t);
				t = t > 0 ? t : 20 - t;

				Thread.sleep(t);
			}
		} catch (final InterruptedException e) {
			running = false;
		} finally {
			gfx.dispose();
		}
	}
}
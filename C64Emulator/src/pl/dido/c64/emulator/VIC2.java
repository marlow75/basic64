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

	enum MODE {
		STANDARD_CHAR_MODE, MULTICOLOR_CHAR_MODE, STANDARD_BITMAP_MODE, MULTICOLOR_BITMAP_MODE, EXTENDED_COLOR_MODE
	};

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
		final int lo = line & 0xff;
		final int hi = (line & 0x100) >> 1;

		// Control registers 1 & 2
		Memory.ram[0xd011] = Memory.ports[0x11];
		Memory.ram[0xd016] = Memory.ports[0x16];

		// store current line
		Memory.ram[0xd012] = lo;
		Memory.ram[0xd011] = Memory.ram[0xd011] & 127 | hi;

		// acknowledge interrupt source (latch) 7 bit is IRQ inverted line
		if (Memory.ports[0x19] != 0) {
			Memory.ram[0xd019] &= ~Memory.ports[0x19];
			Memory.ports[0x19] = 0;
		}

		Memory.ram[0xd01a] = Memory.ports[0x1a]; // interrupt mask

		if (Memory.ports[0x12] == lo && (Memory.ports[0x11] & 128) == hi)
			Memory.ram[0xd019] |= 1 | 128; // raster interrupt

		// copy PORTS -> RAM
		Memory.ram[0xd020] = Memory.ports[0x20];
		Memory.ram[0xd021] = Memory.ports[0x21];

		// configure offset chargen and screen
		final int bank = Memory.ports[0x18];
		if (Memory.ram[0xd018] != bank) {
			Memory.ram[0xd018] = bank;

			// TODO: change to shifting
			chargen_address = ((bank & 0b1110) >> 1) * 2048 + offset;
			screen_address = ((bank & 0b1110000) >> 4) * 1024 + offset;
			graphics_address = (bank & 0b1000) == 0 ? offset : offset + 0x2000;
		}

		updateScreen(cycles);
		oldBeam = beam;

		// interrupts occured?
		IRQ = (Memory.ram[0xd019] & Memory.ram[0xd01a]) != 0;
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

	// standard text mode 40x25 characters
	public static final void updateScreenStardardCharacter(final int cycles) {
		beam += cycles << 3; // new beam position, 1 system tick = 8 bits on the screen

		if (beam > raster_size) // beam outside the screen?
			beam -= raster_size; // move to the start and skip a little

		int position = oldBeam;

		// draw image starting at old beam position to a new one calculated on cpu
		// cycles
		final int fc = colors[Memory.ports[0x20] & 0xf]; // only 15 colors, 4 bit nibble
		final int bc = colors[Memory.ports[0x21] & 0xf];

		do {
			bad_line = false;
			line = position / r_width; // current raster line

			if (line > 13 && line < 298) { // VBLANK
				// final int column = position - line_ptr[line];
				final int column = position % r_width; // current column

				if (column > 49 && column < 453) { // HBLANK
					if (line >= 56 && line <= 255 && column >= 90 && column <= 409) { // center frame
						// draw screen character (single row)
						final int py = line - 56;
						final int m = code_ptr[py >> 3] + ((column - 90) >> 3); // 40x25 screen position

						if (m != om) { // new position or same character?
							code = Memory.fetchVIC2(screen_address + m); // fetch character screen code
							pen = colors[(Memory.ram[0xd800 + m]) & 0xf]; // fetch character color
						}

						final int row = py & 0b111; // row in char set definition
						bad_line = (row == 0); // every first row is the bad line

						// fetch character definition or use previous code
						final int ch = (code == ocode && oy == py) ? och
								: Memory.fetchVIC2(chargen_address + ((code << 3) + row));
						oy = py;
						och = ch;
						om = m;
						ocode = code;

						// draw character on screen bit by bit
						pixels[index] = (ch & bit) != 0 ? pen : bc; // foreground / background
						bit = bit == 1 ? 128 : bit >> 1;
					} else
						pixels[index] = fc; // frame color

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

	// standard hires mode 320x200 pixels
	public static final void updateScreenStardardBitmap(final int cycles) {
		beam += cycles << 3; // new beam position, 1 system tick = 8 bits on the screen

		if (beam > raster_size) // beam outside the screen?
			beam -= raster_size; // move to the start and skip a little

		int position = oldBeam;

		// draw image starting at old beam position to a new one calculated on cpu
		// cycles
		final int fc = colors[Memory.ports[0x20] & 0xf]; // only 15 colors, 4 bit nibble

		do {
			bad_line = false;
			line = position / r_width; // current raster line

			if (line > 13 && line < 298) { // VBLANK
				// final int column = position - line_ptr[line];
				final int column = position % r_width; // current column

				if (column > 49 && column < 453) { // HBLANK
					if (line >= 56 && line <= 255 && column >= 90 && column <= 409) { // center frame
						// draw screen character (single row)
						final int py = line - 56;
						final int px = column - 90;

						final int m = code_ptr[py >> 3] + (px >> 3); // 40x25 color attributes

						if (m != om) { // new position or same character?
							code = Memory.fetchVIC2(screen_address + m); // fetch color from text screen mode

							bc = colors[code & 0xf]; // pen low nibble
							pen = colors[(code & 0xf0) >> 4]; // background upper nibble
						}

						final int row = py & 0b111; // row in char set definition
						bad_line = (row == 0); // every first row is the bad line

						// fetch 8x8 definition or use previous code
						final int ch = (om != m || py != oy) ? Memory.fetchVIC2(graphics_address + ((m << 3) + row))
								: och;

						oy = py;
						och = ch;
						om = m;
						ocode = code;

						// draw character on screen bit by bit
						pixels[index] = (ch & bit) != 0 ? pen : bc; // foreground / background
						bit = bit == 1 ? 128 : bit >> 1;
					} else
						pixels[index] = fc; // frame color

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

	// multicolor text mode 40x25 characters
	public static final void updateScreenMulticolorCharater(final int cycles) {
		beam += cycles << 3; // new beam position, 1 system tick = 8 bits on the screen

		if (beam > raster_size) // beam outside the screen?
			beam -= raster_size; // move to the start and skip a little

		int position = oldBeam;

		// draw image starting at old beam position to a new one calculated on cpu
		// cycles
		final int fc = colors[Memory.ports[0x20] & 0xf]; // only 15 colors, 4 bit nibble
		final int bc = colors[Memory.ports[0x21] & 0xf];

		final int fc1 = colors[Memory.ports[0x22] & 0xf]; // 01
		final int fc2 = colors[Memory.ports[0x23] & 0xf]; // 10

		colorTable[0] = bc;
		colorTable[1] = fc1;
		colorTable[2] = fc2;

		do {
			bad_line = false;
			line = position / r_width; // current raster line

			if (line > 13 && line < 298) { // VBLANK
				// final int column = position - line_ptr[line];
				final int column = position % r_width; // current column

				if (column > 49 && column < 453) { // HBLANK
					if (line >= 56 && line <= 255 && column >= 90 && column <= 409) { // center frame
						// draw screen character (single row)
						final int py = line - 56;
						final int m = code_ptr[py >> 3] + ((column - 90) >> 3); // 40x25 screen position

						if (m != om) { // new position or same character?
							code = Memory.fetchVIC2(screen_address + m); // fetch character screen code
							cti = Memory.ram[0xd800 + m] & 0xf;

							pen = colors[cti]; // fetch character color
							hiresChar = (cti & 8) == 0; // display character in hires mode ?

							colorTable[3] = colors[cti & 0b111];
							ci = 0;
							cc = false;
						}

						final int row = py & 0b111; // row in char set definition
						bad_line = (row == 0); // every first row is the bad line

						// fetch character definition or use previous code
						final int ch = (code == ocode && oy == py) ? och
								: Memory.fetchVIC2(chargen_address + ((code << 3) + row));

						oy = py;
						och = ch;
						om = m;
						ocode = code;

						// draw character on screen bit by bit
						if (hiresChar)
							pixels[index] = (ch & bit) == 0 ? bc : pen; // foreground / background
						else {
							ci = (ci << 1) | ((ch & bit) == 0 ? 0 : 1);
							if (cc) {
								final int cl = colorTable[ci];

								pixels[index - 1] = cl; // two character bits per one pixel
								pixels[index] = cl;

								ci = 0;
							}

							cc = !cc;
						}

						bit = bit == 1 ? 128 : bit >> 1;
					} else
						pixels[index] = fc; // frame color

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

	// multicolor bitmap mode 160x200 characters
	public static final void updateScreenMulticolorBitmap(final int cycles) {
		beam += cycles << 3; // new beam position, 1 system tick = 8 bits on the screen

		if (beam > raster_size) // beam outside the screen?
			beam -= raster_size; // move to the start and skip a little

		int position = oldBeam;

		// draw image starting at old beam position to a new one calculated on cpu
		// cycles
		colorTable[0] = colors[Memory.ports[0x21] & 0xf];
		final int fc = colors[Memory.ports[0x20] & 0xf]; // only 15 colors, 4 bit nibble

		do {
			bad_line = false;
			line = position / r_width; // current raster line

			if (line > 13 && line < 298) { // VBLANK
				// final int column = position - line_ptr[line];
				final int column = position % r_width; // current column

				if (column > 49 && column < 453) { // HBLANK
					if (line >= 56 && line <= 255 && column >= 90 && column <= 409) { // center frame
						// draw screen character (single row)
						final int py = line - 56;
						final int m = code_ptr[py >> 3] + ((column - 90) >> 3); // 40x25 screen position

						if (m != om) { // new position or same character?
							code = Memory.fetchVIC2(screen_address + m); // fetch character screen code

							colorTable[1] = colors[(code & 0xf0) >> 4];
							colorTable[2] = colors[code & 0xf];
							colorTable[3] = colors[Memory.ram[0xd800 + m] & 0xf]; // fetch character color

							ci = 0;
							cc = false;
						}

						final int row = py & 0b111; // row in char set definition
						bad_line = (row == 0); // every first row is the bad line

						// fetch character definition or use previous code
						final int ch = (om != m || py != oy) ? Memory.fetchVIC2(graphics_address + ((m << 3) + row))
								: och;

						oy = py;
						och = ch;
						om = m;
						ocode = code;

						// draw character on screen bit by bit
						ci = (ci << 1) | ((ch & bit) == 0 ? 0 : 1);
						if (cc) {
							final int cl = colorTable[ci];

							pixels[index - 1] = cl; // two character bits per one pixel
							pixels[index] = cl;

							ci = 0;
						}

						cc = !cc;
						bit = bit == 1 ? 128 : bit >> 1;
					} else
						pixels[index] = fc; // frame color

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
		oldBeam = 0; // raster
		line = 0;

		index = 0;
		bit = 128;

		screen_address = 0; // text screen start offset
		chargen_address = 0; // chargen offset
		graphics_address = 0; // graphic screen offset

		bad_line = false; // bad line marker
		offset = 0; // VIC bank

		oy = -1;
		om = -1;
		och = -1;
		ocode = -1;
		code = 0;
		pen = 0;

		// multicolor text
		hiresChar = false;
		cc = false; // hires char in multicolor text mode - cc - color index calculated
		ci = 0;
		cti = 0; // color index, color table index

		// hires
		bc = 0;

		for (int i = 0; i < 0x2e; i++)
			Memory.ports[i] = 0;

		Memory.ports[0x11] = 0x1b;
		Memory.ports[0x16] = 0xc8;
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
			// draw image 50 times per second (PAL)
			while (running) {
				long t = System.currentTimeMillis();

				gfx.drawImage(image, 0, 0, cw, ch, 0, 0, iw, ih, null);
				bufferStrategy.show();

				final long d = System.currentTimeMillis();

				t = 20 - (d - t); // drawing image took
				t = t > 0 ? t : 20 - t; // skip frame
				Thread.sleep(t); // 20ms - t
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

		final int cw = canvas.getWidth();
		final int ch = canvas.getHeight();
		
		final int iw = 720;
		final int ih = 576;

		final BufferedImage crt = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
		final int src[] = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		final int dst[] = ((DataBufferInt) crt.getRaster().getDataBuffer()).getData();
		
		FastPALcodec.init(image.getWidth(), image.getHeight(), src, dst);
		
		try {
			// draw image 50 times per second (PAL)
			while (running) {
				long t = System.currentTimeMillis();

				FastPALcodec.encodeYC();
				FastPALcodec.decodeYC();

				gfx.drawImage(crt, 0, 0, cw, ch, 0, 0, iw, ih, null);
				bufferStrategy.show();

				final long d = System.currentTimeMillis();

				t = 20 - (d - t); // drawing image took
				t = t > 0 ? t : 20 - t; // skip frame
				Thread.sleep(t); // 20ms - t
			}
		} catch (final InterruptedException e) {
			running = false;
		} finally {
			gfx.dispose();
		}
	}
}
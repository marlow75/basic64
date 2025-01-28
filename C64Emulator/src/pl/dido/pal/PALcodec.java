package pl.dido.pal;

public class PALcodec {
	private static float luminance[];
	private static float chrominance[];

	private final static float SCANLINE_TIME = 0.000064f; // microseconds
	private final static float FRAME_TIME = 0.020f; 

	private final static float LINE_SYNC = 0.0000047f;
	private final static float BACK_PORCH = 0.0000057f;
	private final static float FRONT_PORCH = 0.00000161f;
	
	private final static float SUBCARRIER_FREQ = 4.43361875e6f;
	private final static int FRAME_SIZE = 720 * 610;

	private static float dotX;
	private static float dotY;

	private static int width;
	private static float dotClock;

	private static int data[];
	private static int src[];
	
	private final static float scale(final float data) {
		return data / 255;
	}

	private final static float oscilator0(final float a, final float t) {
		return a * (float) Math.sin(2 * Math.PI * SUBCARRIER_FREQ * t);
	}

	private final static float oscilator90(final float a, final float t) {
		return a * (float) Math.cos(2 * Math.PI * SUBCARRIER_FREQ * t);
	}

	private static final float getLuminance(final float r, final float g, final float b) {
		return 0.299f * r + 0.587f * g + 0.114f * b;
	}

	public static final void init(final int width, final int height, final int src[], final int data[]) {
		dotX = width / 720f;
		dotY = height / 610f;

		PALcodec.width = width;
		PALcodec.src = src;
		PALcodec.data = data;
		
		luminance = new float[FRAME_SIZE];
		chrominance = new float[FRAME_SIZE];

		dotClock = (SCANLINE_TIME - (FRONT_PORCH + LINE_SYNC + BACK_PORCH)) / 720f;
	}

	public static void encodeYC() {
		float t = FRAME_TIME + 5 * SCANLINE_TIME; // skip 5 scan line

		final int len = src.length;
		int index = 0;

		for (int y0 = 0; y0 < 610; y0 += 2) {
			final int a = (int) (y0 * dotY) * width;
			final int even = -1 * (2 * (y0 % 2) - 1);
			
			t += LINE_SYNC + BACK_PORCH;

			for (int x0 = 0; x0 < 720; x0++) {
				final int p = a + (int) (x0 * dotX);
				final int d = src[p];

				final float r;
				final float g;
				final float b;

				if (p < len - 3) {
					r = scale((d >> 16) & 0xff);
					g = scale((d >> 8) & 0xff);
					b = scale(d & 0xff);
				} else {
					r = 0f;
					g = 0f;
					b = 0f;
				}

				// get luminance component
				final float y = getLuminance(r, g, b);
				luminance[index] = y;

				final float u = 0.492f * (b - y);
				final float v = 0.877f * (r - y);

				// get chrominance component
				// even = PAL switching
				chrominance[index] = oscilator0(u, t) + even * oscilator90(v, t);

				t += dotClock;
				index++;
			}
			
			t += FRONT_PORCH;
		}

		t += 7 * SCANLINE_TIME;

		for (int y0 = 1; y0 < 610; y0 += 2) {
			final int a = (int) (y0 * dotY) * width;
			final int even = -1 * (2 * (y0 % 2) - 1);
			
			t += LINE_SYNC + BACK_PORCH;

			for (int x0 = 0; x0 < 720; x0++) {
				final int p = a + (int) (x0 * dotX);
				final int d = src[p];

				final float r;
				final float g;
				final float b;

				if (p < len - 3) {
					r = scale((d >> 16) & 0xff);
					g = scale((d >> 8) & 0xff);
					b = scale(d & 0xff);
				} else {
					r = 0f;
					g = 0f;
					b = 0f;
				}

				// get luminance component
				final float y = getLuminance(r, g, b);
				luminance[index] = y;

				final float u = 0.492f * (b - y);
				final float v = 0.877f * (r - y);

				// get chrominance component
				// even = PAL switching
				chrominance[index] = oscilator0(u, t) + even * oscilator90(v, t);

				t += dotClock;
				index++;
			}
			
			t += FRONT_PORCH;
		}
	}

	public static void decodeYC() {
		float t = FRAME_TIME + 5 * SCANLINE_TIME; // skip 5 scan lines
		int index = 0;

		for (int y0 = 0; y0 < 610; y0 += 2) {
			final int even = -1 * (2 * (y0 % 2) - 1);
			t += LINE_SYNC + BACK_PORCH;
			
			final int a1 = y0 * 720;

			for (int x0 = 0; x0 < 720; x0++) {
				final float c = 2f * chrominance[index];

				final float v = even * oscilator90(c, t);
				final float u = oscilator0(c, t);

				final float y = luminance[index];

				final float b = u / 0.492f + y;
				final float r = v / 0.877f + y;

				final float g = (y - 0.299f * r - 0.114f * b) / 0.587f;
				final int a2 = a1 + x0;

				data[a2]  = ((int) (saturate(r) * 255)) << 16;
				data[a2] |= ((int) (saturate(g) * 255)) << 8;
				data[a2] |= ((int) (saturate(b) * 255));

				t += dotClock;
				index++;
			}
			
			t += FRONT_PORCH;
		}

		t += 7 * SCANLINE_TIME;

		for (int y0 = 1; y0 < 610; y0 += 2) {
			final int even = -1 * (2 * (y0 % 2) - 1);
			t += LINE_SYNC + BACK_PORCH;

			final int a = y0 * 720;

			for (int x0 = 0; x0 < 720; x0++) {
				final float c = 2f * chrominance[index];

				final float v = even * oscilator90(c, t);
				final float u = oscilator0(c, t);

				final float y = luminance[index];

				final float b = u / 0.492f + y;
				final float r = v / 0.877f + y;

				final float g = (y - 0.299f * r - 0.114f * b) / 0.587f;
				final int p = a + x0;

				data[p]  = (int)(saturate(r) * 255) << 16;
				data[p] |= (int)(saturate(g) * 255) << 8;
				data[p] |= (int)(saturate(b) * 255);

				t += dotClock;
				index++;
			}
			
			t += FRONT_PORCH;
		}
	}

	private final static float saturate(final float data) {
		return data < 0 ? 0 : data > 1 ? 1 : data;
	}
}
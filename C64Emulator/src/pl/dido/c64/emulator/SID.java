package pl.dido.c64.emulator;

public final class SID {

	// =========================================================
	// Memory integration (zgodnie z Memory.java)
	// =========================================================
	private static final int SID_BASE = 0xD400;
	private static final int SID_END = 0xD41F;
	private static final int IO_BASE = 0xD000;

	// =========================================================
	// Audio timing
	// =========================================================
	private static final int SAMPLE_RATE = 44100;
	private static final double CYCLES_PER_SAMPLE = C64.CLOCK_HZ / SAMPLE_RATE;
	private static double cycleAcc = 0.0;

	// =========================================================
	// FIFO mono s16
	// =========================================================
	private static final int FIFO_SIZE = 1 << 15;
	private static final int FIFO_MASK = FIFO_SIZE - 1;
	private static final short[] fifo = new short[FIFO_SIZE];
	private static int fifoR = 0, fifoW = 0, fifoCount = 0;

	// =========================================================
	// SID internals
	// =========================================================
	private static final int[] reg = new int[32];
	private static int lastWrittenByte = 0;

	private static int volume = 0;
	private static final Voice[] voice = { new Voice(), new Voice(), new Voice() };

	// Envelope tables
	private static final int[] egTable = new int[16];
	private static final int[] EG_DR_SHIFT = { 5, 5, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3,
			3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1,
			1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	private static int noiseSeed = 1;

	// constants
	private static final int WAVE_NONE = 0;
	private static final int WAVE_TRI = 1;
	private static final int WAVE_SAW = 2;
	private static final int WAVE_RECT = 4;
	private static final int WAVE_NOISE = 8;

	private static final int EG_IDLE = 0;
	private static final int EG_ATTACK = 1;
	private static final int EG_DECAY = 2;
	private static final int EG_RELEASE = 3;

	private static final int FILT_NONE = 0;
	private static final int FILT_LP = 1;
	private static final int FILT_BP = 2;
	private static final int FILT_LPBP = 3;
	private static final int FILT_HP = 4;
	private static final int FILT_NOTCH = 5;
	private static final int FILT_HPBP = 6;
	private static final int FILT_ALL = 7;

	// Filter
	private static int fType = FILT_NONE, fFreq = 0, fRes = 0;
	private static float fAmpl = 0f, d1 = 0f, d2 = 0f, g1 = 0f, g2 = 0f;
	private static float xn1 = 0f, xn2 = 0f, yn1 = 0f, yn2 = 0f;

	static {
		final int sidCyclesInt = (int) CYCLES_PER_SAMPLE;
		final int[] div = { 9, 32, 63, 95, 149, 220, 267, 313, 392, 977, 1954, 3126, 3906, 11720, 19531, 31251 };

		for (int i = 0; i < 16; i++)
			egTable[i] = (sidCyclesInt << 16) / div[i];

		voice[0].modTo = voice[1];
		voice[1].modTo = voice[2];
		voice[2].modTo = voice[0];

		reset();
	}

	// =========================================================
	// Public API
	// =========================================================
	public static void reset() {
		for (int i = 0; i < 32; i++) {
			reg[i] = 0;

			Memory.ports[(SID_BASE - IO_BASE) + i] = 0;
			Memory.ram[SID_BASE + i] = 0;
		}

		lastWrittenByte = 0;
		volume = 0;

		for (final Voice v : voice)
			v.reset();

		fType = FILT_NONE;
		fFreq = 0;

		fRes = 0;
		fAmpl = 0f;

		d1 = d2 = g1 = g2 = 0f;
		xn1 = xn2 = yn1 = yn2 = 0f;

		cycleAcc = 0.0;
		fifoR = fifoW = fifoCount = 0;
	}

	/**
	 * Odczyt SID zgodnie z Twoim modelem: wartoœæ odk³adamy do RAM, bo
	 * Memory.fetch() czyta ram[].
	 */
	public static int read(final int address) {
		if (address < SID_BASE || address > SID_END)
			return 0xFF;

		final int adr = (address - SID_BASE) & 0x1F;
		int val;

		switch (adr) {
		case 0x19:
		case 0x1A:
			val = 0xFF;
			lastWrittenByte = 0;

			break;
		case 0x1B: // OSC3
			final int osc3 = oscSample(voice[2]);
			val = (osc3 >> 8) & 0xFF;
			lastWrittenByte = 0;

			break;
		case 0x1C: // ENV3
			val = (voice[2].egLevel >> 16) & 0xFF;
			lastWrittenByte = 0;

			break;
		default:
			val = lastWrittenByte & 0xFF;
			lastWrittenByte = 0;

			break;
		}

		Memory.ram[address] = val;
		return val;
	}

	/**
	 * Mo¿esz opcjonalnie wo³aæ bezpoœrednio przy zapisie CPU do $D400-$D41F. (Nadal
	 * aktualizuje te¿ ports latch).
	 */
	public static void store(final int address, final int data) {
		writeRegInternal(address, data);
	}

	/**
	 * Wymagane: konsumuje cykle i nic nie zwraca. Generowane sample trafiaj¹ do
	 * FIFO.
	 */
	public static void clock(final int cpuCycles) {
		if (cpuCycles <= 0)
			return;

		cycleAcc += cpuCycles;
		while (cycleAcc >= CYCLES_PER_SAMPLE) {
			cycleAcc -= CYCLES_PER_SAMPLE;

			pushSample(renderOneMonoSample());
		}
	}

	public static int availableSamples() {
		return fifoCount;
	}

	public static int popSamples(final short[] out, final int off, final int len) {
		final int n = Math.min(len, fifoCount);

		for (int i = 0; i < n; i++) {
			out[off + i] = fifo[fifoR];
			fifoR = (fifoR + 1) & FIFO_MASK;
		}

		fifoCount -= n;
		return n;
	}

	// =========================================================
	// Core internals
	// =========================================================
	private static void writeRegInternal(final int adr, final int value) {
		reg[adr] = value;
		lastWrittenByte = value;

		final int vi = adr / 7;
		final Voice v = (vi < 3) ? voice[vi] : null;

		switch (adr) {
		case 0:
		case 7:
		case 14:
		case 1:
		case 8:
		case 15:
			if (v != null) {
				final int base = vi * 7;

				v.freq = (reg[base] & 0xFF) | ((reg[base + 1] & 0xFF) << 8);
				v.add = (int) (v.freq * CYCLES_PER_SAMPLE);
			}

			break;
		case 2:
		case 9:
		case 16:
		case 3:
		case 10:
		case 17:
			if (v != null) {
				final int base = vi * 7;
				v.pw = (reg[base + 2] & 0xFF) | ((reg[base + 3] & 0x0F) << 8);
			}

			break;
		case 4:
		case 11:
		case 18:
			if (v != null) {
				v.wave = (value >> 4) & 0x0F;

				final boolean newGate = (value & 0x01) != 0;
				if (newGate != v.gate) {

					if (newGate)
						v.egState = EG_ATTACK;
					else if (v.egState != EG_IDLE)
						v.egState = EG_RELEASE;
				}

				v.gate = newGate;

				v.sync = (value & 0x02) != 0;
				v.test = (value & 0x08) != 0;

				if (v.test)
					v.count = 0;
			}

			break;
		case 5:
		case 12:
		case 19:
			if (v != null) {
				v.aAdd = egTable[(value >> 4) & 0x0F];
				v.dSub = egTable[value & 0x0F];
			}

			break;
		case 6:
		case 13:
		case 20:
			if (v != null) {
				v.sLevel = ((value >> 4) & 0x0F) * 0x111111;
				v.rSub = egTable[value & 0x0F];
			}

			break;
		case 22:
			fFreq = value;
			calcFilter();

			break;
		case 23:
			voice[0].filter = (value & 0x01) != 0;
			voice[1].filter = (value & 0x02) != 0;

			voice[2].filter = (value & 0x04) != 0;
			fRes = (value >> 4) & 0x0F;

			calcFilter();
			break;
		case 24:
			volume = value & 0x0F;
			voice[2].mute = (value & 0x80) != 0;

			final int newType = (value >> 4) & 0x07;
			if (newType != fType) {

				fType = newType;
				xn1 = xn2 = yn1 = yn2 = 0f;
				calcFilter();
			}

			break;
		}
	}

	private static short renderOneMonoSample() {
		int sum = 0;// SAMPLE_TAB[volume & 0x0F] << 8;
		int sumFilt = 0;

		for (int i = 0; i < 3; i++) {
			final Voice v = voice[i];

			// envelope
			switch (v.egState) {
			case EG_ATTACK:
				v.egLevel += v.aAdd;

				if (v.egLevel > 0xFFFFFF) {
					v.egLevel = 0xFFFFFF;
					v.egState = EG_DECAY;
				}

				break;
			case EG_DECAY:
				if (v.egLevel <= v.sLevel || v.egLevel > 0xFFFFFF)
					v.egLevel = v.sLevel;
				else {
					int idx = (v.egLevel >> 16) & 0xFF;
					if (idx >= EG_DR_SHIFT.length)
						idx = EG_DR_SHIFT.length - 1;

					v.egLevel -= (v.dSub >> EG_DR_SHIFT[idx]);

					if (v.egLevel <= v.sLevel || v.egLevel > 0xFFFFFF)
						v.egLevel = v.sLevel;
				}

				break;

			case EG_RELEASE:
				int idx = (v.egLevel >> 16) & 0xFF;

				if (idx >= EG_DR_SHIFT.length)
					idx = EG_DR_SHIFT.length - 1;

				v.egLevel -= (v.rSub >> EG_DR_SHIFT[idx]);

				if (v.egLevel <= 0 || v.egLevel > 0xFFFFFF) {
					v.egLevel = 0;
					v.egState = EG_IDLE;
				}

				break;

			default:
				// EG_IDLE: zostaw bie¿¹cy poziom (po RELEASE i tak dojdzie do 0)

				break;
			}

			final int envelope = (v.egLevel * (volume & 0x0F)) >> 20;

			// phase/sync/test
			if (!v.test)
				v.count += v.add;

			if (v.sync && v.count >= 0x1000000)
				v.modTo.count = 0;

			v.count &= 0xFFFFFF;
			final int osc = oscSample(v);

			final int signed = (short) (osc ^ 0x8000);
			final int s = signed * envelope;

			if (v.filter)
				sumFilt += s;
			else 
			if (!v.mute)
				sum += s;
		}

		// filter
		if (fType != FILT_NONE) {
			final float xn = sumFilt * fAmpl;
			float yn = xn + d1 * xn1 + d2 * xn2 - g1 * yn1 - g2 * yn2;

			// delikatne t³umienie pêtli (anti-runaway)
			yn *= 0.985f;

			// twardy limiter stanu filtra
			if (yn > 200000f)
				yn = 200000f;

			if (yn < -200000f)
				yn = -200000f;

			sum += (int) yn;

			xn2 = xn1;
			xn1 = xn;

			yn2 = yn1;
			yn1 = yn;
		}

		sum >>= 10;
		if (sum > 32767)
			sum = 32767;
		else if (sum < -32768)
			sum = -32768;

		return (short) sum;
	}

	private static int oscSample(final Voice v) {
		final int w = v.wave & 0x0F;

		if (w == 0)
			return 0x8000;

		int tri = 0, saw = 0, pulse = 0, noise = 0;

		if ((w & WAVE_TRI) != 0) {
			final int p = (v.count >> 11) & 0x1FFF;
			final int t = (p < 0x1000) ? p : (0x1FFF - p);
			
			tri = (t << 4) | (t >> 8);
		}

		if ((w & WAVE_SAW) != 0)
			saw = (v.count >> 8) & 0xFFFF;

		if ((w & WAVE_RECT) != 0)
			pulse = (v.count > ((v.pw & 0x0FFF) << 12)) ? 0xFFFF : 0x0000;

		if ((w & WAVE_NOISE) != 0) {
			// nadal uproszczony noise, ale bez "wyrównywania g³oœnoœci"
			if (v.count >= 0x100000) {
				v.noise = (noiseRand() << 8) & 0xFFFF;
				v.count &= 0x0FFFFF;
			}

			noise = v.noise;
		}

		// Kombinacje jak w SID-charakterze: AND
		boolean started = false;
		int out = 0xFFFF;

		if ((w & WAVE_TRI) != 0) {
			out = tri;
			started = true;
		}
		
		if ((w & WAVE_SAW) != 0) {
			out = started ? (out & saw) : saw;
			started = true;
		}
		
		if ((w & WAVE_RECT) != 0) {
			out = started ? (out & pulse) : pulse;
			started = true;
		}
		
		if ((w & WAVE_NOISE) != 0) {
			out = started ? (out & noise) : noise;
			started = true;
		}

		// Bez bias/normalizacji – bardziej SID-like
		return out & 0xFFFF;
	}

	private static void calcFilter() {
		if (fType == FILT_NONE) {
			fAmpl = 0f;
			d1 = d2 = g1 = g2 = 0f;
			return;
		}

		final float fr = (fType == FILT_LP || fType == FILT_LPBP) ? calcResLP(fFreq) : calcResHP(fFreq);
		float arg = fr / (SAMPLE_RATE * 0.5f);

		if (arg > 0.99f)
			arg = 0.99f;

		if (arg < 0.01f)
			arg = 0.01f;

		g2 = 0.55f + 1.2f * arg * arg - 1.2f * arg + fRes * 0.0133333333f;
		g1 = (float) (-2.0 * Math.sqrt(g2) * Math.cos(Math.PI * arg));

		if (fType == FILT_LPBP || fType == FILT_HPBP)
			g2 += 0.1f;

		if (Math.abs(g1) >= g2 + 1.0f)
			g1 = (g1 > 0f) ? (g2 + 0.99f) : -(g2 + 0.99f);

		switch (fType) {
		case FILT_LP:
		case FILT_LPBP:
			d1 = 2f;
			d2 = 1f;

			fAmpl = 0.25f * (1f + g1 + g2);
			break;
		case FILT_HP:
		case FILT_HPBP:
			d1 = -2f;
			d2 = 1f;

			fAmpl = 0.25f * (1f - g1 + g2);
			break;
		case FILT_BP:
			d1 = 0f;
			d2 = -1f;

			fAmpl = 0.2f;
			break;
		case FILT_NOTCH:
			d1 = (float) (-2.0 * Math.cos(Math.PI * arg));
			d2 = 1f;

			fAmpl = 0.5f;
			break;
		case FILT_ALL:
			d1 = (float) (-4.0 * Math.cos(Math.PI * arg));

			d2 = 4f;
			fAmpl = 0.25f;
			break;
		}
	}

	private static final float calcResLP(final float f) {
		return (227.755f - 1.7635f * f - 0.0176385f * f * f + 0.00333484f * f * f * f);
	}

	private static final float calcResHP(final float f) {
		return (366.374f - 14.0052f * f + 0.603212f * f * f - 0.000880196f * f * f * f);
	}

	private static final int noiseRand() {
		noiseSeed = noiseSeed * 1103515245 + 12345;
		return (noiseSeed >>> 16) & 0xFF;
	}

	private static final void pushSample(short s) {
		if (fifoCount == FIFO_SIZE) {
			fifoR = (fifoR + 1) & FIFO_MASK; // drop oldest
			fifoCount--;
		}

		fifo[fifoW] = s;
		fifoW = (fifoW + 1) & FIFO_MASK;
		
		fifoCount++;
	}

	private static final class Voice {
		int wave = WAVE_NONE;
		int egState = EG_IDLE;
		Voice modTo;

		int count = 0; // 24-bit
		int add = 0;
		int freq = 0;
		int pw = 0; // 12-bit

		int aAdd = 0, dSub = 0, sLevel = 0, rSub = 0, egLevel = 0;
		int noise = 0;

		boolean gate = false, test = false, filter = false, sync = false, mute = false;

		void reset() {
			wave = WAVE_NONE;
			egState = EG_IDLE;

			count = add = freq = pw = 0;
			aAdd = dSub = sLevel = rSub = egLevel = 0;

			noise = 0;
			gate = test = filter = sync = mute = false;
		}
	}
}
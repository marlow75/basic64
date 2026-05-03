package pl.dido.c64.emulator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class Memory {
	// RAM
	public static final int ram[] = new int[64 * 1024];

	// ROM
	private static final int basic[] = new int[8 * 1024];
	private static final int kernal[] = new int[8 * 1024];

	// CHARGEN
	protected static final int chargen[] = new int[4 * 1024];

	// DEVICES
	protected static final int ports[] = new int[4 * 1024];

	// ROM enablers
	public static boolean basicON = true;
	public static boolean kernalON = true;
	public static boolean ioON = true;
	public static boolean charsetON = false;

	// CPU
	public static final int fetch(final int address) {
		// RAM
		if (address < 0xa000)
			return ram[address];

		// 0xa000–0xbfff – BASIC lub RAM
		if (address < 0xc000)
			return basicON ? basic[address & 0x1fff] : ram[address];

		if (address < 0xd000)
			return ram[address];

		// 0xd000–0xdfff – I/O lub chargen lub RAM (w zale¿noœci od ioON i trybu)
		if (address < 0xe000) {
			if (charsetON)
				return chargen[address & 0x0fff];

			if (address == 0xd01e || address == 0xd01f) {
				final int v = ram[address] & 0xff;
				ram[address] = 0;

				return v;
			}
			
			if (address == 0xdc0d)
				CIA.readACK(address);

			return ram[address];
		}

		// 0xE000–0xFFFF – KERNAL lub RAM
		if (kernalON)
			return kernal[address & 0x1fff];

		return ram[address];
	}

	// VIC2
	public static final int fetchVIC2(final int address) {
	    // adres w obrêbie 16 KB banku VIC
	    final int inBank = (address - VIC2.offset) & 0x3FFF;  // 0..3FFF

	    // okno CHARGEN dla VIC: $1000-$1FFF w banku
	    if (inBank >= 0x1000 && inBank < 0x2000)
	        return chargen[inBank & 0x0FFF];

	    // reszta to RAM (w tym screen/bitmap/sprity itp.)
	    return ram[address];
	}

	public static final void store(final int address, final int data) {
		if (address == 1) { // memory configuration
			switch (data & 0b111) {
			case 0b000: // all RAM
				ioON = false;
				basicON = false;
				kernalON = false;
				charsetON = false;
				break;
			case 0b001:
				ioON = false;
				basicON = false;
				kernalON = false;
				charsetON = true;
				break;
			case 0b010:
				ioON = false;
				basicON = false;
				kernalON = true;
				charsetON = true;
				break;
			case 0b011:
				ioON = false;
				basicON = true;
				kernalON = true;
				charsetON = true;
				break;
			case 0b100: // all RAM
				ioON = false;
				basicON = false;
				kernalON = false;
				charsetON = false;
				break;
			case 0b101:
				basicON = false;
				kernalON = false;
				ioON = true;
				charsetON = false;
				break;
			case 0b110:
				basicON = false;
				kernalON = true;
				ioON = true;
				charsetON = false;
				break;
			case 0b111:
				basicON = true;
				kernalON = true;
				ioON = true;
				charsetON = false;
				break;
			}

			ram[1] = data;
			return;
		}

		if (address < 0xd000) {
			ram[address] = data;
			return;
		}

		if (address > 0xd7ff && address < 0xdc00) { // color ram is separate, 4 bits wide
			ram[address] = data;
			return;
		}

		if (address < 0xe000) {
			final int addr = address - 0xd000;
			ports[addr] = data;

			// wyczyœæ rejestry SID:
			if (addr < 0x500)
				SID.store(addr & 0x1f, data);

			return;
		}

		ram[address] = data;
	}

	public static final void fill(final int address, final int array[]) {
		final int end = array.length + address;
		assert (end - 1 <= 0xffff);

		for (int i = 0; i < array.length; i++)
			ram[address + i] = array[i];
	}

	private static final byte[] load(final int address, final InputStream is) throws IOException {
		final byte[] data = is.readAllBytes();
		final int end = data.length + address;

		assert (end - 1 <= 0xffff);
		return data;
	}

	public static final void fill(final int address, final InputStream is) throws IOException {
		final byte data[] = load(address, is);

		for (int i = 0; i < data.length; i++)
			ram[address + i] = data[i] & 0xff;
	}

	public static final void loadBasic(final InputStream is) throws IOException {
		final byte data[] = load(0xa000, is);

		for (int i = 0; i < data.length; i++)
			basic[i] = data[i] & 0xff;

		basicON = true;
	}

	public static final void loadKernal(final InputStream is) throws IOException {
		final byte data[] = load(0xa000, is);

		for (int i = 0; i < data.length; i++)
			kernal[i] = data[i] & 0xff;

		kernalON = true;
	}

	public static final void loadChargen(final InputStream is) throws IOException {
		final byte data[] = load(0xa000, is);

		for (int i = 0; i < data.length; i++)
			chargen[i] = data[i] & 0xff;
	}

	public static final boolean match(final int address1, final int address2, int bytes) {
		final int end = address1 > address2 ? address1 + bytes : address2 + bytes;
		assert (end <= 0xffff);

		for (int i = 0; i < bytes; i++)
			if (ram[address2 + i] != ram[address1 + i])
				return false;

		return true;
	}

	public static final String printMemory(final int address, int bytes) {
		final int end = address + bytes;
		assert (end <= 0xffff);

		String s = "";
		for (int i = 0; i < bytes; i++)
			s = s + Integer.toHexString((byte) fetch(address + i) & 0xff) + ' ';

		return s;
	}

	public static final void clear() {
		int pattern = 0xff;
		for (int i = 0; i <= 0xffff; i++)
			ram[i] = (i % 64 == 0) ? pattern = ~pattern & 0xff: pattern;

		// wyzeruj I/O shadow
		for (int i = 0; i <= 0x0fff; i++)
			ports[i] = 0;

		// CPU port default after reset-like state
		ram[0x0000] = 0x2f; // DDR
		ram[0x0001] = 0x37; // BASIC+KERNAL+I/O visible

		basicON = true;
		kernalON = true;
		ioON = true;
		charsetON = false;

		for (int a = 0xD800; a <= 0xDBFF; a++)
			ram[a] = 0;

		// VIC-II sensible defaults
		ram[0xD011] = 0x1B;
		ram[0xD016] = 0x08;
		ram[0xD018] = 0x14;
		ram[0xD019] = 0x00;
		ram[0xD01A] = 0x00;
		ram[0xD015] = 0x00;
		ram[0xD020] = 0x0E;
		ram[0xD021] = 0x06;
	}

	public static final void dump() throws UnsupportedEncodingException {
		dumpRegion(0, 4);
		dumpScreen(4);
		dumpRegion(8, 256);
	}

	private static final void dumpRegion(final int pStart, final int pEnd) throws UnsupportedEncodingException {
		final int as = pStart * 256;
		final int ae = pEnd * 256;

		for (int i = as; i < ae; i += 16) {
			String p = "";
			System.out.print(Helper.padLeftZeros(Integer.toHexString(i), 4) + " ");

			for (int j = 0; j < 16; j++) {
				final int ch = ram[i + j];

				if (ch < 32 || ch == 127)
					p += ".";
				else
					p += String.format("%1$c", (char) ch);

				System.out.print(Helper.padLeftZeros(Integer.toHexString(ch), 2) + " ");
			}

			System.out.println(p);
		}
	}

	private static final void dumpScreen(final int page) throws UnsupportedEncodingException {
		final int start = page * 256;

		for (int y = 0; y < 25; y++) {
			for (int x = 0; x < 40; x++)
				System.out.print(Helper.screen2Petscii(ram[start + (40 * y) + x]));

			System.out.println();
		}
	}

	public static final void reset() {
		clear();
		try {
			loadKernal(new FileInputStream("kernal")); // kernel
			loadBasic(new FileInputStream("basic")); // basic
			loadChargen(new FileInputStream("chargen")); // chargen
		} catch (final IOException e) {
			System.out.println("Can't load one of rom file !!!");
		}
	}

	public static final boolean match(final int address, final int[] data) {
		assert (address + data.length <= 0xffff);

		for (int i = 0; i < data.length; i++)
			if (ram[address + i] != data[i])
				return false;

		return true;
	}
}
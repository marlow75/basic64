package pl.dido.c64.emulator;

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
	public static boolean basicON = false;
	public static boolean kernalON = false;
	public static boolean ioON = true;
	
	// CPU 
	public static final int fetch(final int address) {
		// IO mapped RAM (read) PORT (write)
		
		if (address > 0x9fff) // Basic
			if (address < 0xc000 && basicON)
				return basic[address & 0x1fff];
			else 
			if (address > 0xdfff) {
					if (kernalON) // Kernal
						return kernal[address & 0x1fff];
					else
						return ram[address];
			} else
			if (address > 0xcfff && !ioON) // chargen
				return chargen[address & 0x0fff];

		return ram[address];
	}
	
	// VIC2
	public static final int fetchVIC2(final int address) {
		// VIC2 memory configuration is done via CIA2 ports (NOT IMPLEMENTED)
		
		if (address < 0x1000)		// VIC2 0 bank ROM in (0x1000, 0x1fff) 
			return ram[address];
		
		if (address < 0x2000)
			return chargen[address & 0x0fff];
		
		if (address < 0x9000)		// VIC2 2 bank ROM in (0x9000, 0x9fff)
			return ram[address];
		
		if (address < 0xc000)
			return chargen[address & 0x0fff];
		
		return ram[address];
	}

	public static final void store(final int address, final int data) {
		if (address == 1) { // memory configuration
			ioON = (data & 0b100) == 0b100;
			
			switch (data & 0b11) {
				case 0:  // all RAM
					ioON = false;
					basicON = false;
					kernalON = false;
					break;
				case 0b01:
					basicON = false;
					kernalON = false;
					break;
				case 0b10:
					basicON = false;
					kernalON = true;
					break;
				case 0b11:
					basicON = true;
					kernalON = true;
					break;				
			}
			
			ram[1] = data; 
			return;
		}
		
		if (address < 0xd000) {
			ram[address] = data;
			return;
		}
		
		if (address > 0xd7ff && address < 0xdc00) {  // color ram is a separate static ram, 4 bits wide
			ram[address] = data;
			return;
		}
		
		if (address < 0xe000) {
			ports[address - 0xd000] = data;
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
			ram[address + i] = data[i] & 0xff;;
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
			kernal[i] = data[i]  & 0xff;
		
		kernalON = true;
	}
	
	public static final void loadChargen(final InputStream is) throws IOException {
		final byte data[] = load(0xa000, is);

		for (int i = 0; i < data.length; i++)
			chargen[i] = data[i]  & 0xff;
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
		for (int i = 0; i <= 0xffff; i++)
			ram[i] = 0;
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
			for (int x = 0; x < 40;  x++)
				System.out.print(Helper.screen2Petscii(ram[start + (40 * y) + x]));
			
			System.out.println();
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
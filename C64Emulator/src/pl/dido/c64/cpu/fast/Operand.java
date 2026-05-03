package pl.dido.c64.cpu.fast;

import pl.dido.c64.emulator.Memory;

public class Operand {

	private static int address;
	private static int type; // 0 - memory 1 - ACC

	public static final void assign(final int address) {
		type = 0;
		Operand.address = address & 0xffff;
	}
	
	public static final void assignACC() {
		type = 1;
	}	

	public static final int get() {
		return type == 1 ? MOS6510.AC : Memory.fetch(address); 
	}

	public static final int getAddress() {
		return address;
	}

	public static final void set(final int value) {	
		final int v = value & 0xff;
		
		if (type == 0) 
			Memory.store(address, v);
		else
			MOS6510.AC = v;
	}
}
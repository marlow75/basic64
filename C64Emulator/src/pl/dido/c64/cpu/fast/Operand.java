package pl.dido.c64.cpu.fast;

import pl.dido.c64.emulator.Memory;

public class Operand {

	public static enum TYPE {
		MEMORY_OPERAND, REG_AC
	};

	private static int address;
	public static TYPE type;

	public static final void assign(final int address) {
		type = TYPE.MEMORY_OPERAND;
		Operand.address = address & 0xffff;
	}

	public static final int get() {
		return type == TYPE.REG_AC ? MOS6510.AC : Memory.fetch(address); 
	}

	public static final int getAddress() {
		return address;
	}

	public static final void set(final int value) {		
		switch (type) {
			case MEMORY_OPERAND:
				Memory.store(address, value & 0xff);
				break;
			case REG_AC:
				MOS6510.AC = value & 0xff;
				break;
		}
	}
}
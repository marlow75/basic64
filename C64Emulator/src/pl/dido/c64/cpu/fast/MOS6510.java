package pl.dido.c64.cpu.fast;

import pl.dido.c64.emulator.Memory;

public class MOS6510 {

	public static int PC, AC, SP, X, Y; // 6502 registers

	public static int N; // 7- negative
	public static int V; // 6- overflow
	// ---------------------------------
	public static int B; // 4- break
	public static int D; // 3- decimal
	public static int I; // 2-interrupt
	public static int Z; // 1- zero
	public static int C; // 0- carry

	private static int cycles;

	// increment program counter
	private static final void incPC() {
		PC = ++PC & 0xffff;
	}

	// increment stack pointer
	private static final void incSP() {
		SP = ++SP & 0xff;
	}

	// decrement stack pointer
	private static final void decSP() {
		SP = --SP & 0xff;
	}

	// make word from lo and hi bytes
	private static final int word(final int lo, final int hi) {
		return (hi << 8) | lo;
	}

	// set Negative and Zero flags
	private static final void setNZ(final int value) {
		Z = (value == 0) ? 2 : 0;
		N = value & 0x80;
	}

	// push value at stack
	private static final void push(final int data) {
		Memory.store(0x0100 | SP, data & 0xff);
		decSP();
	}

	// pull value from stack
	private static final int pull() {
		incSP();
		return Memory.fetch(0x0100 | SP);
	}

	// clear all registers
	public static final void clearREG() {
		SP = 0xff;

		// regs
		AC = X = Y = 0;
		// flags
		B = C = V = N = Z = 0;
		I = 4;
	}

	// reset processor
	public static final void reset() {
		final int lo = Memory.fetch(0xFFFC);
		final int hi = Memory.fetch(0xFFFD);

		PC = word(lo, hi);
		clearREG();
	}

	// pack all flags into single byte
	private static final int packFlags() {
		return N | V | 48 | D | I | Z | C;
	}

	// unpack all flags
	private static final void unpackFlags(final int flags) {
		N = flags & 128;
		V = flags & 64;
		D = flags & 8;
		I = flags & 4;
		Z = flags & 2;
		C = flags & 1;
	}

	// initialize interrupt
	public static final int IRQ() {
		int hi = PC >>> 8;
		int lo = PC & 0xff;

		push(hi);
		push(lo);

		push(N | V | D | I | Z | C);
		I = 4;

		lo = Memory.fetch(0xFFFE);
		hi = Memory.fetch(0xFFFF);

		PC = word(lo, hi);

		return 7;
	}

	// initialize non maskable interrupt
	public static final int NMI() {
		int hi = PC >>> 8;
		int lo = PC & 0xff;

		push(hi);
		push(lo);
		push(packFlags());
		I = 4;

		lo = Memory.fetch(0xFFFA);
		hi = Memory.fetch(0xFFFB);
		PC = word(lo, hi);

		return 7;
	}

	// subtraction with carry
	protected static final int subC(int A, int B) {
		// compliment 2 add -B
		A &= 0xff;
		B = ((B & 0xff) ^ 0b11111111) + 1;

		int ALU = A + B - (C ^ 0b1);
		C = (ALU & 0x100) >> 8;
		ALU &= 0xff;

		setNZ(ALU);
		return ALU;
	}

	// subtraction without carry
	protected static final int sub(int A, int B) {
		// compliment 2 add -B
		A &= 0xff;
		B = ((B & 0xff) ^ 0b11111111) + 1;

		int ALU = A + B;
		C = (ALU & 0x100) >> 8;
		ALU &= 0xff;

		setNZ(ALU);
		return ALU;
	}

	// addition with carry
	protected static final int addC(int A, int B) {
		A &= 0xff;
		B &= 0xff;

		int ALU = A + B + C;

		C = (ALU & 0x100) >> 8;
		ALU &= 0xff;

		setNZ(ALU);
		return ALU;
	}

	protected static final int executeBranch() {
		final byte rel = (byte) Memory.fetch(PC - 1);
		final int oPC = PC;

		PC = (PC + rel) & 0xffff;
		return (((oPC ^ PC) & 0xff00) != 0) ? 4: 3;
	}

	// resolve operand
	private static final int getOperandG2(final int opcode, final int addressingMode) {
		switch (addressingMode) {
		case 0b000: // immediate
			Operand.assign(PC);
			incPC();

			return 0;
		case 0b001: // zero page
			Operand.assign(Memory.fetch(PC));
			incPC();

			return 1;
		case 0b010: // accumulator
			Operand.type = Operand.TYPE.REG_AC;

			return 0;
		case 0b011: // absolute
			int lo = Memory.fetch(PC);
			incPC();
			int hi = Memory.fetch(PC);
			incPC();

			Operand.assign(word(lo, hi));
			return 2;
		case 0b101: // zero page,X or zero page,Y
			if (opcode == 0b101 || opcode == 0b100) // LDX, STX?
				Operand.assign((Memory.fetch(PC) + Y) & 0xff);
			else
				Operand.assign((Memory.fetch(PC) + X) & 0xff);
			incPC();

			return 2;
		case 0b111: // absolute,X or absolute,Y
			if (opcode == 0b100)
				throw new RuntimeException("Illegal addressing mode");

			lo = Memory.fetch(PC);
			incPC();
			hi = Memory.fetch(PC);
			incPC();

			int oldALU = word(lo, hi);
			int ALU = (opcode == 0b101 || opcode == 0b100) ? oldALU + Y: oldALU + X;// LDX add Y LDY add X

			Operand.assign(ALU);
			return (((ALU ^ oldALU) & 0xff00) == 0) ? 3: 4;
		default:
			throw new RuntimeException("Not implemented addresing mode: " + addressingMode);
		}
	}

	private static final int getOperandG3(final int addressingMode) {
		switch (addressingMode) {
		case 0b000: // immediate
			Operand.assign(PC);
			incPC();

			return 0;
		case 0b001: // zero page
			Operand.assign(Memory.fetch(PC));
			incPC();

			return 1;
		case 0b011: // absolute
			int lo = Memory.fetch(PC);
			incPC();
			int hi = Memory.fetch(PC);
			incPC();

			Operand.assign(word(lo, hi));
			return 2;
		case 0b101: // zero page,X
			Operand.assign((Memory.fetch(PC) + X) & 0xff);
			incPC();

			return 2;
		case 0b111: // absolute,X
			lo = Memory.fetch(PC);
			incPC();
			hi = Memory.fetch(PC);
			incPC();

			int oldALU = word(lo, hi);
			int ALU = oldALU + X;
			Operand.assign(ALU);

			return (((ALU ^ oldALU) & 0xff00) == 0) ? 3: 4;
		default:
			throw new RuntimeException("Not implemented addresing mode: " + addressingMode);
		}
	}

	private static final int getOperandG1(final int addressingMode) {
		switch (addressingMode) {
		case 0b000: // (zero page,X)
			int ALU = Memory.fetch(PC);
			incPC();
			ALU = (ALU + X) & 0xff;

			int lo = Memory.fetch(ALU);
			ALU = ++ALU & 0xff;
			int hi = Memory.fetch(ALU);

			Operand.assign(word(lo, hi));
			return 4;
		case 0b001: // zero page
			Operand.assign(Memory.fetch(PC));
			incPC();

			return 1;
		case 0b010: // immediate
			Operand.assign(PC);
			incPC();

			return 0;
		case 0b011: // absolute
			lo = Memory.fetch(PC);
			incPC();
			hi = Memory.fetch(PC);
			incPC();

			Operand.assign(word(lo, hi));
			return 2;
		case 0b100: // (zero page),Y
			ALU = Memory.fetch(PC);
			incPC();

			lo = Memory.fetch(ALU);
			ALU = ++ALU & 0xff;
			hi = Memory.fetch(ALU);

			int oldALU = word(lo, hi);
			ALU = oldALU + Y;
			Operand.assign(ALU);

			return (((ALU ^ oldALU) & 0xff00) == 0) ? 4: 5;
		case 0b101: // zero page,X
			Operand.assign((Memory.fetch(PC) + X) & 0xff);
			incPC();

			return 2;
		case 0b110: // absolute,Y
			lo = Memory.fetch(PC);
			incPC();
			hi = Memory.fetch(PC);
			incPC();

			oldALU = word(lo, hi);
			ALU = oldALU + Y;
			Operand.assign(ALU);

			return (((ALU ^ oldALU) & 0xff00) == 0) ? 3: 4;
		case 0b111: // absolute,X
			lo = Memory.fetch(PC);
			incPC();
			hi = Memory.fetch(PC);
			incPC();

			oldALU = word(lo, hi);
			ALU = oldALU + X;
			Operand.assign(ALU);

			return (((ALU ^ oldALU) & 0xff00) == 0) ? 3: 4;
		default:
			throw new RuntimeException("Not implemented addresing mode");
		}
	}

	public static final int executeNext() {
		final int opcode = Memory.fetch(PC);
		incPC();

		// irregular opcodes
		switch (opcode) {
		case 0x00: // BRK
			int ALU = (PC + 1) & 0xffff; // return address

			int hi = ALU >>> 8;
			int lo = ALU & 0xff;

			push(hi);
			push(lo);

			B = 16;
			push(packFlags());
			I = 4;

			lo = Memory.fetch(0xFFFE);
			hi = Memory.fetch(0xFFFF);

			PC = word(lo, hi);

			return 7;
		case 0x20: // JSR
			ALU = (PC + 1) & 0xffff;

			hi = ALU >>> 8;
			lo = ALU & 0xff;

			push(hi);
			push(lo);

			lo = Memory.fetch(PC);
			incPC();
			hi = Memory.fetch(PC);
			PC = word(lo, hi);

			return 6;
		case 0x40: // RTI
			unpackFlags(pull());
			lo = pull();
			hi = pull();

			PC = word(lo, hi);

			return 6;
		case 0x60: // RTS
			lo = pull();
			hi = pull();

			PC = (word(lo, hi) + 1) & 0xffff;

			return 6;
		case 0x08: // PHP
			push(packFlags());

			return 4;
		case 0x28: // PLP
			unpackFlags(pull());

			return 4;
		case 0x48: // PHA
			push(AC);

			return 4;
		case 0x68: // PLA
			AC = pull();
			setNZ(AC);

			return 4;
		case 0x88: // DEY
			Y = --Y & 0xff;
			setNZ(Y);

			return 2;
		case 0xA8: // TAY
			Y = AC;
			setNZ(Y);

			return 2;
		case 0xC8: // INY
			Y = ++Y & 0xff;
			setNZ(Y);

			return 2;
		case 0xE8: // INX
			X = ++X & 0xff;
			setNZ(X);

			return 2;
		case 0x18: // CLC
			C = 0;

			return 2;
		case 0x38: // SEC
			C = 1;

			return 2;
		case 0x58: // CLI
			I = 0;

			return 2;
		case 0x78: // SEI
			I = 4;

			return 2;
		case 0x98: // TYA
			AC = Y;
			setNZ(Y);

			return 2;
		case 0xB8: // CLV
			V = 0;

			return 2;
		case 0xD8: // CLD
			D = 0;

			return 2;
		case 0xF8: // SED
			D = 8;

			return 2;
		case 0x8A: // TXA
			AC = X;

			setNZ(AC);
			return 2;
		case 0x9A: // TXS
			SP = X;

			return 2;
		case 0xAA: // TAX
			X = AC;
			setNZ(X);

			return 2;
		case 0xBA: // TSX
			X = SP;
			setNZ(X);

			return 2;
		case 0xCA: // DEX
			X = --X & 0xff;
			setNZ(X);

			return 2;
		case 0xEA: // NOP

			return 2;
		}

		// is it a branch instruction? xxy10000
		if ((opcode & 0b00011111) == 0b10000) {
			incPC();
			final int wait = 2;

			switch (opcode & 0b11100000) {
			case 0b00100000: // BMI
				return (N == 128) ? wait + executeBranch() : wait;
			case 0b00000000: // BPL
				return (N == 0) ? wait + executeBranch() : wait;
			case 0b01100000: // BVS
				return (V == 64) ? wait + executeBranch() : wait;
			case 0b01000000: // BVC
				return (V == 0) ? wait + executeBranch() : wait;
			case 0b10000000: // BCC
				return (C == 0) ? wait + executeBranch() : wait;
			case 0b10100000: // BCS
				return (C == 1) ? wait + executeBranch() : wait;
			case 0b11000000: // BNE
				return (Z == 0) ? wait + executeBranch() : wait;
			case 0b11100000: // BEQ
				return (Z == 2) ? wait + executeBranch() : wait;
			default:
				return wait;
			}
		}

		// regular op instructions
		// aaabbbcc opcode
		final int cc = opcode & 0b00000011;
		final int bbb = (opcode & 0b00011100) >> 2;
		final int aaa = (opcode & 0b11100000) >> 5;

		cycles = 1;
		switch (cc) {
		case 0b01: // group one
			cycles += getOperandG1(bbb);
			switch (aaa) {
			case 0b000: // ORA
				AC |= Operand.get();

				setNZ(AC);
				return ++cycles;
			case 0b001: // AND
				AC &= Operand.get();

				setNZ(AC);
				return ++cycles;
			case 0b010: // EOR
				AC ^= Operand.get();

				setNZ(AC);
				return ++cycles;
			case 0b011: // ADC
				int value = Operand.get();

				if (D == 8) {
					int ALU = (AC & 0xf) + (value & 0xf) + C;
					ALU += ALU > 9 ? 6 : 0;

					/*
					 * Negative and Overflow flags are set with the same logic than in Binary mode,
					 * but after fixing the lower nibble.
					 */
					ALU = (ALU > 0xf) ? (ALU & 0xf) + (AC & 0xf0) + (value & 0xf0) + 0x10
							: (ALU & 0xf) + (AC & 0xf0) + (value & 0xf0);

					N = ALU & 0x80;
					V = ((AC ^ ALU) & (value ^ ALU) & 0x80) >> 1;

					if ((ALU & 0x1f0) > 0x90)
						ALU += 0x60; // BCD fixup for upper nibble.

					// carry is the only flag set after fixing the result.
					C = ALU > 0xff ? 1 : 0;

					Z = ((AC + value + C) & 0xff) == 0 ? 2 : 0; // Zero flag is set just like in Binary mode.
					AC = ALU & 0xff;
				} else {
					// binary mode
					int ALU = addC(AC, value); // with carry

					V = ((AC ^ ALU) & (value ^ ALU) & 0x80) >> 1;
					AC = ALU;
				}

				return ++cycles;
			case 0b100: // STA
				Operand.set(AC);

				return ++cycles;
			case 0b101: // LDA
				AC = Operand.get();

				setNZ(AC);
				return ++cycles;
			case 0b110: // CMP
				sub(AC, Operand.get());

				return ++cycles;
			case 0b111: // SBC
				value = Operand.get();

				if (D == 8) {
					final int notC = (C ^ 0b1);
					int AL = (AC & 0xf) - (value & 0xf) - notC; // Calculate the lower nibble.

					final int k = (AL & 0x10) >> 4;
					AL -= k * 6; // BCD fixup for lower nibble.

					int AH = (AC & 0xf0) - (value & 0xf0) - k * 0x10; // Calculate the upper nibble.

					if ((AH & 0x100) == 0x100)
						AH -= 0x60; // BCD fixup for upper nibble.

					// The flags are set just like in Binary mode.
					final int sum = (AC - value - notC) & 0x1ff;

					C = (sum < 0x100) ? 1 : 0;
					Z = (sum & 0xff) == 0 ? 2 : 0;
					V = ((AC ^ sum) & (value ^ sum) & 0x80) >> 1;
					N = sum & 0x80;

					AC = (AH & 0xf0) | (AL & 0xf);
				} else {
					int ALU = subC(AC, value); // with carry

					V = ((AC ^ ALU) & (value ^ ALU) & 0x80) >> 1;
					AC = ALU;
				}

				return ++cycles;
			}

		case 0b10: // group two
			final int opCycles = getOperandG2(aaa, bbb);

			switch (aaa) {
			case 0b000: // ASL
				int ALU = Operand.get() << 1;

				C = ALU > 255 ? 1 : 0;
				ALU = ALU & 0xff;
				setNZ(ALU);

				Operand.set(ALU);
				return ++cycles + (opCycles << 1);
			case 0b001: // ROL
				ALU = (Operand.get() << 1) | C;

				C = ALU > 255 ? 1 : 0;
				ALU = ALU & 0xff;
				setNZ(ALU);

				Operand.set(ALU);
				return ++cycles + (opCycles << 1);
			case 0b010: // LSR
				int value = Operand.get();

				C = value & 0b1;
				ALU = value >> 1;

				setNZ(ALU);
				Operand.set(ALU);

				return ++cycles + (opCycles << 1);
			case 0b011: // ROR
				value = Operand.get();

				final int o = value & 0b1;
				ALU = value >> 1 | 128 * C;
				C = o;

				setNZ(ALU);
				Operand.set(ALU);

				return ++cycles + (opCycles << 1);
			case 0b100: // STX
				Operand.set(X);

				return ++cycles;
			case 0b101: // LDX
				X = Operand.get();

				setNZ(X);
				return cycles;
			case 0b110: // DEC
				ALU = (Operand.get() - 1) & 0xff;
				setNZ(ALU);
				Operand.set(ALU);

				return ++cycles + (opCycles << 1);
			case 0b111: // INC
				ALU = (Operand.get() + 1) & 0xff;
				setNZ(ALU);
				Operand.set(ALU);

				return ++cycles + (opCycles << 1);
			}

		case 0b00: // group three
			cycles += getOperandG3(bbb);

			switch (aaa) {
			case 0b001: // BIT
				int value = Operand.get();
				int ALU = AC & value;

				Z = (ALU == 0) ? 2 : 0;
				V = value & 0x40;
				N = value & 0x80;

				return ++cycles;
			case 0b010: // JMP
				PC = Operand.getAddress();

				return cycles;
			case 0b011: // JMP (abs)
				ALU = Operand.getAddress();

				int lo = Memory.fetch(ALU);
				ALU = ++ALU & 0xffff;
				int hi = Memory.fetch(ALU);

				PC = word(lo, hi);

				return cycles + 2;
			case 0b100: // STY
				Operand.set(Y);

				return ++cycles;
			case 0b101: // LDY
				Y = Operand.get();

				setNZ(Y);
				return ++cycles;
			case 0b110: // CPY
				sub(Y, Operand.get());

				return ++cycles;
			case 0b111: // CPX
				sub(X, Operand.get());

				return ++cycles;
			}
		}

		return cycles;
	}
}
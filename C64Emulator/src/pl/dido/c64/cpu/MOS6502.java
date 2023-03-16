package pl.dido.c64.cpu;

import pl.dido.c64.emulator.Memory;

public class MOS6502 {

	public static int PC, AC, SP, X, Y; // 6510 registers

	public static int N; // 7- negative
	public static int V; // 6- overflow
	// --------------------------------
	public static int B; // 4- break
	public static int D; // 3- decimal
	public static int I; // 2-interrupt
	public static int Z; // 1- zero
	public static int C; // 0- carry
	
    public static interface CommandProcesor {
        public int processCommand(final MODE mode);
    }
    
    public static interface Operand {
		public int calculateAddress();
    } 
    
    // addressing mode
    public enum MODE {    	
    	ACCUMULATOR(() -> { 
    		incPC();
    		
    		cycles = 0;
    		return -1;
    	}),
        ABSOLUTE(() -> {
			final int lo = Memory.fetch(PC);
			incPC();
			final int hi = Memory.fetch(PC);
			incPC();

			cycles = 0;
			return word(lo, hi);			
        }),
        ABSOLUTE_Y(() -> {
			final int lo = Memory.fetch(PC);
			incPC();
			final int hi = Memory.fetch(PC);
			incPC();

			final int oldALU = word(lo, hi);
			final int ALU = oldALU + Y & 0xffff;
			
			cycles = (((ALU ^ oldALU) & 0xff00) == 0) ? 0: 1;
			return ALU;
        }),
        ABSOLUTE_X(() -> {
			final int lo = Memory.fetch(PC);
			incPC();
			final int hi = Memory.fetch(PC);
			incPC();

			final int oldALU = word(lo, hi);
			final int ALU = oldALU + X & 0xffff;
			
			cycles = (((ALU ^ ALU) & 0xff00) == 0) ? 0: 1;
			return ALU;
        }),
        IMMEDIATE(() -> {
        	final int address = PC;
        	incPC();
        	
        	cycles = 0;
        	return address;
        }),
        IMPLIED(() -> {
        	cycles = 0;
        	return 0;
        }),
        INDIRECT(() -> {
			int lo = Memory.fetch(PC);
			incPC();
			int hi = Memory.fetch(PC);
			incPC();			
									
			int ALU = word(lo, hi);

			lo = Memory.fetch(ALU);
			ALU = ++ALU & 0xffff;	// bug fixed
			hi = Memory.fetch(ALU);

			return word(lo, hi);
        }),
        INDIRECT_X(() -> {
        	int ALU = Memory.fetch(PC);
			incPC();
			ALU = (ALU + X) & 0xff;

			final int lo = Memory.fetch(ALU);
			ALU = ++ALU & 0xff;
			final int hi = Memory.fetch(ALU);
			
			cycles = 0;
			return word(lo, hi); 
        }),
        INDIRECT_Y(() -> {
			int ALU = Memory.fetch(PC);
			incPC();

			final int lo = Memory.fetch(ALU);
			ALU = ++ALU & 0xff;
			final int hi = Memory.fetch(ALU);

			final int oldALU = word(lo, hi);
			final int address = (oldALU + Y) & 0xffff;

			cycles = (((ALU ^ oldALU) & 0xff00) == 0) ? 0: 1;			
			return address; 
        }),        
        RELATIVE(() -> {
    		final byte rel = (byte) Memory.fetch(PC);
    		incPC();
    		final int oPC = PC;

    		final int address = (PC + rel) & 0xffff;
    		cycles = (((oPC ^ PC) & 0xff00) != 0) ? 0: 1;
    		
    		return address;
        }),
        ZEROPAGE(() -> {
			final int address = Memory.fetch(PC);
			incPC();
			
			cycles = 0;
			return address;
        }),
        ZEROPAGE_X(() -> {
        	final int address = (Memory.fetch(PC) + X) & 0xff;
        	incPC();
        	
        	cycles = 0;
        	return address;
        }),
        ZEROPAGE_Y(() -> {
        	final int address = (Memory.fetch(PC) + Y) & 0xff;
        	incPC();
        	
        	cycles = 0;
        	return address;
        });
        
    	private static int cycles;    	
        private Operand operand;
    	
    	MODE(final Operand operand) {
    		this.operand = operand;
    	}
    	
    	public final Operand operand() {
    		return operand;
    	}
    	
    	public final int getCycles() {
    		return cycles;
    	}    	
    }
	
	// all 6510 commands
    public enum COMMAND {
        ADC((mode) -> {
        	final int value = Memory.fetch(mode.operand().calculateAddress());

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
				final int ALU = addC(AC, value); // with carry

				V = ((AC ^ ALU) & (value ^ ALU) & 0x80) >> 1;
				AC = ALU;
			}
			
			return mode.getCycles();
        }),
        AND((mode) -> {
			AC &= Memory.fetch(mode.operand().calculateAddress());

			setNZ(AC);			
			return mode.getCycles();
        }),
        ASL((mode) -> {
        	final int address = mode.operand().calculateAddress();
			int ALU = Memory.fetch(address) << 1;
			
			C = ALU > 255 ? 1 : 0;
			ALU &= 0xff;
			setNZ(ALU);

			Memory.store(address, ALU);
			return mode.getCycles();
        }),
        ASL_A((mode) -> {
			AC <<= 1;
			
			C = AC > 255 ? 1 : 0;
			AC &= 0xff;
			setNZ(AC);

			return mode.getCycles();
        }),
        BCC((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	
        	if (C == 0) {
                PC = address;
                return mode.getCycles();
            }
        	
        	return 0;
        }),
        BCS((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	
        	if (C == 1) {
                PC = address;
                return mode.getCycles();
            }
        	
        	return 0;
        }),
        BEQ((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	
        	if (Z == 2) {
                PC = address;
                return mode.getCycles();
            }
        	
        	return 0;
        }),
        BIT((mode) -> {
			final int value = Memory.fetch(mode.operand().calculateAddress());
			final int ALU = AC & value;

			Z = (ALU == 0) ? 2 : 0;
			V = value & 0x40;
			N = value & 0x80;

			return mode.getCycles();
        }),
        BMI((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	
        	if (N == 128) {
                PC = address;
                return mode.getCycles();
            }
        	
        	return 0;
        }),
        BNE((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	
        	if (Z == 0) {
                PC = address;
                return mode.getCycles();
            }
        	
        	return 0;
        }),
        BPL((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	
        	if (N == 0) {
                PC = address;
                return mode.getCycles();
            }
        	
        	return 0;
        }),
        BRK((mode) -> {
			final int ALU = (PC + 1) & 0xffff; // return address

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
			return 0;
        }),
        BVC((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	
        	if (V == 0) {
                PC = address;
                return mode.getCycles();
            }
        	
        	return 0;        
        }),
        BVS((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	
        	if (V == 64) {
                PC = address;
                return mode.getCycles();
            }
        	
        	return 0;
        }),
        CLC((mode) -> {
            return C = 0;
        }),
        CLD((mode) -> {
            return D = 0;
        }),
        CLI((mode) -> {
            return I = 0;
        }),
        CLV((mode) -> {
            return V = 0;
        }),
        CMP((mode) -> {
			sub(AC, Memory.fetch(mode.operand().calculateAddress()));
			return mode.getCycles();
        }),
        CPX((mode) -> {
        	sub(X, Memory.fetch(mode.operand().calculateAddress()));
        	return mode.getCycles();
        }),
        CPY((mode) -> {
        	sub(Y, Memory.fetch(mode.operand().calculateAddress()));
        	return mode.getCycles();
        }),
        DEC((mode) -> {
        	final int address = mode.operand().calculateAddress();
			final int ALU = (Memory.fetch(address) - 1) & 0xff;
			setNZ(ALU);
			
			Memory.store(address, ALU);
			return mode.getCycles();
        }),
        DEX((mode) -> {
			X = --X & 0xff;
			setNZ(X);
			
			return 0;
        }),
        DEY((mode) -> {
			Y = --Y & 0xff;
			setNZ(Y);
			
			return 0;
        }),
        EOR((mode) -> {
			AC ^= Memory.fetch(mode.operand().calculateAddress());

			setNZ(AC);
			return mode.getCycles();
        }),
        INC((mode) -> {
        	final int address = mode.operand().calculateAddress();
        	final int ALU = (Memory.fetch(address) + 1) & 0xff;
			
        	setNZ(ALU);
			Memory.store(address, ALU);

			return mode.getCycles();
        }),
        INX((mode) -> {
        	X = ++X & 0xff;
			
        	setNZ(X);
			return 0;
        }),
        INY((mode) -> {
        	Y = ++Y & 0xff;
			
        	setNZ(Y);
			return 0;
        }),
        JMP((mode) -> {
            PC = mode.operand().calculateAddress();
            return mode.getCycles();
        }),
        JSR((mode) -> {
			int ALU = (PC + 1) & 0xffff;

			int hi = ALU >>> 8;
			int lo = ALU & 0xff;

			push(hi);
			push(lo);

			lo = Memory.fetch(PC);
			incPC();
			
			hi = Memory.fetch(PC);
			PC = word(lo, hi);
			
			return 0;
        }),
        LDA((mode) -> {
			AC = Memory.fetch(mode.operand().calculateAddress());

			setNZ(AC);
			return mode.getCycles();
        }),
        LDX((mode) -> {
			X = Memory.fetch(mode.operand().calculateAddress());

			setNZ(X);
			return mode.getCycles();
        }),
        LDY((mode) -> {
			Y = Memory.fetch(mode.operand().calculateAddress());

			setNZ(Y);
			return mode.getCycles();
        }),
        LSR((mode) -> {
        	final int address = mode.operand().calculateAddress();
			final int value = Memory.fetch(address);

			C = value & 0b1;
			final int ALU = value >> 1;

			setNZ(ALU);
			Memory.store(address, ALU);
			
			return mode.getCycles();
        }),
        LSR_A((mode) -> {
			C = AC & 0b1;
			AC >>= 1;

			setNZ(AC);
			
			return mode.getCycles();
        }),
        NOP((mode) -> {
        	return 0;
        }),
        ORA((mode) -> {
			AC |= Memory.fetch(mode.operand().calculateAddress());

			setNZ(AC);
			return mode.getCycles();
        }),
        PHA((mode) -> {
            push(AC);
            return 0;
        }),
        PHP((mode) -> {
        	push(packFlags());
        	return 0;
        }),
        PLA((mode) -> {
			AC = pull();
			setNZ(AC);
			
			return 0;
        }),
        PLP((mode) -> {
			unpackFlags(pull());
			return 0;
        }),
        ROL((mode) -> {
        	final int address = mode.operand().calculateAddress();
			int ALU = (Memory.fetch(address) << 1) | C;

			C = ALU > 255 ? 1 : 0;
			ALU &= 0xff;
			setNZ(ALU);

			Memory.store(address, ALU);
			return mode.getCycles();
        }),
        ROL_A((mode) -> {
			AC = (AC << 1) | C;

			C = AC > 255 ? 1 : 0;
			AC &= 0xff;
			setNZ(AC);

			return 0;
        }),
        ROR((mode) -> {
        	final int address = mode.operand().calculateAddress();
			int ALU = Memory.fetch(address);

			final int o = ALU & 0b1;
			ALU = ALU >> 1 | 128 * C;
			C = o;

			setNZ(ALU);
			Memory.store(address, ALU);
			
			return mode.getCycles();
        }),
        ROR_A((mode) -> {
			final int o = AC & 0b1;
			AC = AC >> 1 | 128 * C;
			
			C = o;
			setNZ(AC);
			
			return 0;
        }),
        RTI((mode) -> {
			unpackFlags(pull());
			final int lo = pull();
			final int hi = pull();

			PC = word(lo, hi);
			return 0;
        }),
        RTS((mode) -> {
        	final int lo = pull();
			final int hi = pull();

			PC = (word(lo, hi) + 1) & 0xffff;
			return 0;
        }),
        SBC((mode) -> {
        	final int value = Memory.fetch(mode.operand().calculateAddress());

			if (D == 8) { // decimal mode
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
			
			return mode.getCycles();
        }),
        SEC((mode) -> {
            C = 1;
            return 0;
        }),
        SED((mode) -> {
			D = 8;
			return 0;
        }),
        SEI((mode) -> {
			I = 4;
			return 0;
        }),
        STA((mode) -> {
			Memory.store(mode.operand().calculateAddress(), AC);
			
			return mode.getCycles();
        }),
        STX((mode) -> {
			Memory.store(mode.operand().calculateAddress(), X);
			
			return mode.getCycles();
        }),
        STY((mode) -> {
			Memory.store(mode.operand().calculateAddress(), Y);
			
			return mode.getCycles();
        }),
        TAX((mode) -> {
            X = AC;
            setNZ(X);
            
            return 0;
        }),
        TAY((mode) -> {
            Y = AC;
            setNZ(Y);
            
            return 0;
        }),
        TSX((mode) -> {
        	X = SP;
			setNZ(X);

			return 0;
        }),
        TXA((mode) -> {
			AC = X;
			setNZ(AC);
			
			return 0;
        }),
        TXS((mode) -> {
        	SP = X;
        	return 0;
        }),
        TYA((mode) -> {
			AC = Y;
			setNZ(AC);
			
			return 0;
        });
    	
    	private CommandProcesor cmd;
    	
        private COMMAND(final CommandProcesor cmd) {
            this.cmd = cmd;
        }
        
        public int processCommand(final MODE mode) {
        	return cmd.processCommand(mode);
        }
    };
	
	// all opcodes
	public enum OPCODE {
        ADC_IMM(0x0069, COMMAND.ADC, MODE.IMMEDIATE, 2),
        ADC_ZP(0x0065, COMMAND.ADC, MODE.ZEROPAGE, 3),
        ADC_ZP_X(0x0075, COMMAND.ADC, MODE.ZEROPAGE_X, 4),
        ADC_AB(0x006D, COMMAND.ADC, MODE.ABSOLUTE, 4),
        ADC_IND_ZP_X(0x0061, COMMAND.ADC, MODE.INDIRECT_X, 6),
        ADC_AB_X(0x007D, COMMAND.ADC, MODE.ABSOLUTE_X, 4),
        ADC_AB_Y(0x0079, COMMAND.ADC, MODE.ABSOLUTE_Y, 4),
        ADC_IND_ZP_Y(0x0071, COMMAND.ADC, MODE.INDIRECT_Y, 5),
        AND_IMM(0x0029, COMMAND.AND, MODE.IMMEDIATE, 2),
        AND_ZP(0x0025, COMMAND.AND, MODE.ZEROPAGE, 3),
        AND_ZP_X(0x0035, COMMAND.AND, MODE.ZEROPAGE_X, 4),
        AND_AB(0x002D, COMMAND.AND, MODE.ABSOLUTE, 4),
        AND_IND_ZP_X(0x0021, COMMAND.AND, MODE.INDIRECT_X, 6),
        AND_AB_X(0x003D, COMMAND.AND, MODE.ABSOLUTE_X, 4),
        AND_AB_Y(0x0039, COMMAND.AND, MODE.ABSOLUTE_Y, 4),
        AND_IND_ZP_Y(0x0031, COMMAND.AND, MODE.INDIRECT_Y, 5),
        ASL(0x000A, COMMAND.ASL_A, MODE.IMPLIED, 2),
        ASL_ZP(0x0006, COMMAND.ASL, MODE.ZEROPAGE, 5),
        ASL_ZP_X(0x0016, COMMAND.ASL, MODE.ZEROPAGE_X, 6),
        ASL_AB(0x000E, COMMAND.ASL, MODE.ABSOLUTE, 6),
        ASL_AB_X(0x001E, COMMAND.ASL, MODE.ABSOLUTE_X, 7),
        BCC_REL(0x0090, COMMAND.BCC, MODE.RELATIVE, 2),
        BCS_REL(0x00B0, COMMAND.BCS, MODE.RELATIVE, 2),
        BEQ_REL0(0x00F0, COMMAND.BEQ, MODE.RELATIVE, 2),
        BIT_ZP(0x0024, COMMAND.BIT, MODE.ZEROPAGE, 3),
        BIT_AB(0x002C, COMMAND.BIT, MODE.ABSOLUTE, 4),
        BMI_REL(0x0030, COMMAND.BMI, MODE.RELATIVE, 2),
        BNE_REL(0x00D0, COMMAND.BNE, MODE.RELATIVE, 2),
        BPL_REL(0x0010, COMMAND.BPL, MODE.RELATIVE, 2),
        BRK(0x0000, COMMAND.BRK, MODE.IMMEDIATE, 7),
        BVC_REL(0x0050, COMMAND.BVC, MODE.RELATIVE, 2),
        BVS_REL(0x0070, COMMAND.BVS, MODE.RELATIVE, 2),
        CLC(0x0018, COMMAND.CLC, MODE.IMPLIED, 2),
        CLD(0x00D8, COMMAND.CLD, MODE.IMPLIED, 2),
        CLI(0x0058, COMMAND.CLI, MODE.IMPLIED, 2),
        CLV(0x00B8, COMMAND.CLV, MODE.IMPLIED, 2),
        CMP_IMM(0x00C9, COMMAND.CMP, MODE.IMMEDIATE, 2),
        CMP_ZP(0x00C5, COMMAND.CMP, MODE.ZEROPAGE, 3),
        CMP_ZP_X(0x00D5, COMMAND.CMP, MODE.ZEROPAGE_X, 4),
        CMP_AB(0x00CD, COMMAND.CMP, MODE.ABSOLUTE, 4),
        CMP_IND_ZP_X(0x00C1, COMMAND.CMP, MODE.INDIRECT_X, 6),
        CMP_AB_X(0x00DD, COMMAND.CMP, MODE.ABSOLUTE_X, 4),
        CMP_AB_Y(0x00D9, COMMAND.CMP, MODE.ABSOLUTE_Y, 4),
        CMP_IND_ZP_Y(0x00D1, COMMAND.CMP, MODE.INDIRECT_Y, 5),
        CPX_IMM(0x00E0, COMMAND.CPX, MODE.IMMEDIATE, 2),
        CPX_ZP(0x00E4, COMMAND.CPX, MODE.ZEROPAGE, 3),
        CPX_AB(0x00EC, COMMAND.CPX, MODE.ABSOLUTE, 4),
        CPY_IMM(0x00C0, COMMAND.CPY, MODE.IMMEDIATE, 2),
        CPY_ZP(0x00C4, COMMAND.CPY, MODE.ZEROPAGE, 3),
        CPY_AB(0x00CC, COMMAND.CPY, MODE.ABSOLUTE, 4),
        DEC_ZP(0x00C6, COMMAND.DEC, MODE.ZEROPAGE, 5),
        DEC_ZP_X(0x00D6, COMMAND.DEC, MODE.ZEROPAGE_X, 6),
        DEC_AB(0x00CE, COMMAND.DEC, MODE.ABSOLUTE, 6),
        DEC_AB_X(0x00DE, COMMAND.DEC, MODE.ABSOLUTE_X, 7),
        DEX(0x00CA, COMMAND.DEX, MODE.IMPLIED, 2),
        DEY(0x0088, COMMAND.DEY, MODE.IMPLIED, 2),
        EOR_IMM(0x0049, COMMAND.EOR, MODE.IMMEDIATE, 2),
        EOR_ZP(0x0045, COMMAND.EOR, MODE.ZEROPAGE, 3),
        EOR_ZP_X(0x0055, COMMAND.EOR, MODE.ZEROPAGE_X, 4),
        EOR_AB(0x004D, COMMAND.EOR, MODE.ABSOLUTE, 4),
        EOR_IND_ZP_X(0x0041, COMMAND.EOR, MODE.INDIRECT_X, 6),
        EOR_AB_X(0x005D, COMMAND.EOR, MODE.ABSOLUTE_X, 4),
        EOR_AB_Y(0x0059, COMMAND.EOR, MODE.ABSOLUTE_Y, 4),
        EOR_IND_ZP_Y(0x0051, COMMAND.EOR, MODE.INDIRECT_Y, 5),
        INC_ZP(0x00E6, COMMAND.INC, MODE.ZEROPAGE, 5),
        INC_ZP_X(0x00F6, COMMAND.INC, MODE.ZEROPAGE_X, 6),
        INC_AB(0x00EE, COMMAND.INC, MODE.ABSOLUTE, 6),
        INC_AB_X(0x00FE, COMMAND.INC, MODE.ABSOLUTE_X, 7),
        INX(0x00E8, COMMAND.INX, MODE.IMPLIED, 2),
        INY(0x00C8, COMMAND.INY, MODE.IMPLIED, 2),
        JMP_AB(0x004C, COMMAND.JMP, MODE.ABSOLUTE, 3),
        JMP_IND(0x006C, COMMAND.JMP, MODE.INDIRECT, 5),
        JSR_AB(0x0020, COMMAND.JSR, MODE.ABSOLUTE, 6),
        LDA_IMM(0x00A9, COMMAND.LDA, MODE.IMMEDIATE, 2),
        LDA_ZP(0x00A5, COMMAND.LDA, MODE.ZEROPAGE, 3),
        LDA_ZP_X(0x00B5, COMMAND.LDA, MODE.ZEROPAGE_X, 4),
        LDA_AB(0x00AD, COMMAND.LDA, MODE.ABSOLUTE, 4),
        LDA_IND_ZP_X(0x00A1, COMMAND.LDA, MODE.INDIRECT_X, 6),
        LDA_AB_X(0x00BD, COMMAND.LDA, MODE.ABSOLUTE_X, 4),
        LDA_AB_Y(0x00B9, COMMAND.LDA, MODE.ABSOLUTE_Y, 4),
        LDA_IND_ZP_Y(0x00B1, COMMAND.LDA, MODE.INDIRECT_Y, 5),
        LDX_IMM(0x00A2, COMMAND.LDX, MODE.IMMEDIATE, 2),
        LDX_ZP(0x00A6, COMMAND.LDX, MODE.ZEROPAGE, 3),
        LDX_ZP_Y(0x00B6, COMMAND.LDX, MODE.ZEROPAGE_Y, 4),
        LDX_AB(0x00AE, COMMAND.LDX, MODE.ABSOLUTE, 4),
        LDX_AB_Y(0x00BE, COMMAND.LDX, MODE.ABSOLUTE_Y, 4),
        LDY_IMM(0x00A0, COMMAND.LDY, MODE.IMMEDIATE, 2),
        LDY_ZP(0x00A4, COMMAND.LDY, MODE.ZEROPAGE, 3),
        LDY_ZP_X(0x00B4, COMMAND.LDY, MODE.ZEROPAGE_X, 4),
        LDY_AB(0x00AC, COMMAND.LDY, MODE.ABSOLUTE, 4),
        LDY_AB_X(0x00BC, COMMAND.LDY, MODE.ABSOLUTE_X, 4),
        LSR(0x004A, COMMAND.LSR_A, MODE.IMPLIED, 2),
        LSR_ZP(0x0046, COMMAND.LSR, MODE.ZEROPAGE, 5),
        LSR_ZP_X(0x0056, COMMAND.LSR, MODE.ZEROPAGE_X, 6),
        LSR_AB(0x004E, COMMAND.LSR, MODE.ABSOLUTE, 6),
        LSR_AB_X(0x005E, COMMAND.LSR, MODE.ABSOLUTE_X, 7),
        NOP(0x00EA, COMMAND.NOP, MODE.IMPLIED, 2),        
        ORA_IMM(0x0009, COMMAND.ORA, MODE.IMMEDIATE, 2),
        ORA_ZP(0x0005, COMMAND.ORA, MODE.ZEROPAGE, 3),
        ORA_ZP_X(0x0015, COMMAND.ORA, MODE.ZEROPAGE_X, 4),
        ORA_AB(0x000D, COMMAND.ORA, MODE.ABSOLUTE, 4),
        ORA_IND_ZP_X(0x0001, COMMAND.ORA, MODE.INDIRECT_X, 6),
        ORA_AB_X(0x001D, COMMAND.ORA, MODE.ABSOLUTE_X, 4),
        ORA_AB_Y(0x0019, COMMAND.ORA, MODE.ABSOLUTE_Y, 4),
        ORA_IND_ZP_Y(0x0011, COMMAND.ORA, MODE.INDIRECT_Y, 5),
        PHA(0x0048, COMMAND.PHA, MODE.IMPLIED, 3),
        PHP(0x0008, COMMAND.PHP, MODE.IMPLIED, 3),
        PLA(0x0068, COMMAND.PLA, MODE.IMPLIED, 4),
        PLP(0x0028, COMMAND.PLP, MODE.IMPLIED, 4),
        ROL(0x002A, COMMAND.ROL_A, MODE.IMPLIED, 2),
        ROL_ZP(0x0026, COMMAND.ROL, MODE.ZEROPAGE, 5),
        ROL_ZP_X(0x0036, COMMAND.ROL, MODE.ZEROPAGE_X, 6),
        ROL_AB(0x002E, COMMAND.ROL, MODE.ABSOLUTE, 6),
        ROL_AB_X(0x003E, COMMAND.ROL, MODE.ABSOLUTE_X, 7),
        ROR(0x006A, COMMAND.ROR_A, MODE.IMPLIED, 2),
        ROR_ZP(0x0066, COMMAND.ROR, MODE.ZEROPAGE, 5),
        ROR_ZP_X(0x0076, COMMAND.ROR, MODE.ZEROPAGE_X, 6),
        ROR_AB(0x006E, COMMAND.ROR, MODE.ABSOLUTE, 6),
        ROR_AB_X(0x007E, COMMAND.ROR, MODE.ABSOLUTE_X, 7),
        RTI(0x0040, COMMAND.RTI, MODE.IMPLIED, 6),
        RTS(0x0060, COMMAND.RTS, MODE.IMPLIED, 6),
        SBC_IMM(0x00E9, COMMAND.SBC, MODE.IMMEDIATE, 2),
        SBC_ZP(0x00E5, COMMAND.SBC, MODE.ZEROPAGE, 3),
        SBC_ZP_X(0x00F5, COMMAND.SBC, MODE.ZEROPAGE_X, 4),
        SBC_AB(0x00ED, COMMAND.SBC, MODE.ABSOLUTE, 4),
        SBC_IND_ZP_X(0x00E1, COMMAND.SBC, MODE.INDIRECT_X, 6),
        SBC_AB_X(0x00FD, COMMAND.SBC, MODE.ABSOLUTE_X, 4),
        SBC_AB_Y(0x00F9, COMMAND.SBC, MODE.ABSOLUTE_Y, 4),
        SBC_IND_ZP_Y(0x00F1, COMMAND.SBC, MODE.INDIRECT_Y, 5),
        SEC(0x0038, COMMAND.SEC, MODE.IMPLIED, 2),
        SED(0x00F8, COMMAND.SED, MODE.IMPLIED, 2),
        SEI(0x0078, COMMAND.SEI, MODE.IMPLIED, 2),
        STA_ZP(0x0085, COMMAND.STA, MODE.ZEROPAGE, 3),
        STA_ZP_X(0x0095, COMMAND.STA, MODE.ZEROPAGE_X, 4),
        STA_AB(0x008D, COMMAND.STA, MODE.ABSOLUTE, 4),
        STA_AB_X(0x009D, COMMAND.STA, MODE.ABSOLUTE_X, 5),
        STA_AB_Y(0x0099, COMMAND.STA, MODE.ABSOLUTE_Y, 5),
        STA_IND_ZP_X(0x0081, COMMAND.STA, MODE.INDIRECT_X, 6),
        STA_IND_ZP_Y(0x0091, COMMAND.STA, MODE.INDIRECT_Y, 6),
        STX_ZP(0x0086, COMMAND.STX, MODE.ZEROPAGE, 3),
        STX_ZP_Y(0x0096, COMMAND.STX, MODE.ZEROPAGE_Y, 4),
        STX_AB(0x008E, COMMAND.STX, MODE.ABSOLUTE, 4),
        STY_ZP(0x0084, COMMAND.STY, MODE.ZEROPAGE, 3),
        STY_ZP_X(0x0094, COMMAND.STY, MODE.ZEROPAGE_X, 4),
        STY_AB(0x008C, COMMAND.STY, MODE.ABSOLUTE, 4),
        TAX(0x00AA, COMMAND.TAX, MODE.IMPLIED, 2),
        TAY(0x00A8, COMMAND.TAY, MODE.IMPLIED, 2),
        TSX(0x00BA, COMMAND.TSX, MODE.IMPLIED, 2),
        TXA(0x008A, COMMAND.TXA, MODE.IMPLIED, 2),
        TXS(0x009A, COMMAND.TXS, MODE.IMPLIED, 2),
        TYA(0x0098, COMMAND.TYA, MODE.IMPLIED, 2);
		
        private int code;
		private COMMAND command;
		
		private MODE mode;
		private int cycles;
		
        private OPCODE(final int code, final COMMAND command, final MODE mode, final int cycles) {
            this.code = code;
            this.command = command;
            this.mode = mode;
            this.cycles = cycles;
        }
        
        public int execute() {
        	return cycles + command.processCommand(mode);
        }
	}
	
	static private OPCODE[] opcodes;

    static {
        opcodes = new OPCODE[256];
        
        for (final OPCODE o: OPCODE.values())
            opcodes[o.code] = o;
    }

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

	public static final int executeNext() {
		final int opcode = Memory.fetch(PC);
		incPC();
		
		final OPCODE op = opcodes[opcode];
		return op.execute();
	}
}
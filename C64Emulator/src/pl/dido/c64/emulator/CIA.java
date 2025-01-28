package pl.dido.c64.emulator;

public class CIA {

	// keyboard pins
	private static int column, column2, row, row2;
	
	// counterA
	private static int counterA, latchA;
	private static boolean runA;
	
	public static boolean IRQ;

	public static final void counterA(final int cycles) {
		final int CTA = Memory.ports[0xc0e]; // CTA
		
		final int TAlo = Memory.ports[0xc04];
		final int TAhi = Memory.ports[0xc05];
		
		// run counter if enable bit is set or there was writing to hi byte latch
		final boolean continuous = (CTA & 0x8) == 0;
		runA = (CTA & 0b1) == 0b1 || runA && !continuous;		
		
		if (latchA != TAhi)
			if (!continuous) {
				runA = true;
				
				latchA = TAhi;
				counterA = TAlo | TAhi << 8;
				
				return;
			}
		
		if (runA) {
			final int delta = counterA - cycles; // only processor ticks
			if (continuous)  { // continuous mode?
				counterA = delta > 0 ?  delta : (TAlo | TAhi << 8) - delta;
				IRQ = delta < 0;
			} else  // single shot
				if (delta > 0)
					counterA = delta;
				else {
					counterA = (TAlo | TAhi << 8) - delta;
					runA = false;
				}

			Memory.ram[0xdc04] = counterA & 0xff;
			Memory.ram[0xdc05] = counterA >> 8;
		}
	}
	
	public static final void keyboard() {
		final int portA = Memory.ports[0xc00];

		if (portA == 0) // all keys - any key pressed?
			Memory.ram[0xdc01] = column;
		else if (portA == column)
			Memory.ram[0xdc01] = row;
		else if (portA == column2)
			Memory.ram[0xdc01] = row2;
		else
			Memory.ram[0xdc01] = 0xff;
	}
	
	public static final void clock(final int cycles) {
		keyboard();
		counterA(cycles);
		VIC2config();
	}

	private static final void VIC2config() {
		final int config = Memory.ports[0xd00];
		if (Memory.ram[0xdd00] != config) {
			Memory.ram[0xdd00] = config; // shadow register
		
			VIC2.offset = ((config & 0b11) ^ 0b11) * 16 * 1024;
		}
	}

	public static final void keypressed(final int key, final boolean shift) {
		switch (key) {

		// 17 -- Ctrl = RUN STOP
		case 17:
			row = 0x7f;
			column = 0x7f;
			if (shift) {
				row2 = 0x7f;
				column2 = 0xfd;
			}
			break;
		// 8 -- Backspace = DEL
		case 8:
			row = 0xfe;
			column = 0xfe;
			break;
		// 9 -- Tab = CONTROL
		case 9:
			row = 0xfb;
			column = 0x7f;
			break;
		// 10 -- Enter = RETURN
		case 10:
			row = 0xfd;
			column = 0xfe;
			break;
		// 16 -- Shift
		case 16:
			row = 0x7f;
			column = 0xfd;
			break;
		// 18 -- Alt = C=
		case 18:
			row = 0xdf;
			column = 0x7f;
			if (shift) {
				row2 = 0x7f;
				column2 = 0xfd;
			}
			break;
		// 27 -- Escape = <-
		case 27:
			row = 0xfd;
			column = 0x7f;
			break;
		// 32 -- Space
		case 32:
			row = 0xef;
			column = 0x7f;
			break;
		// 36 - home
		case 36:
			row = 0xf7;
			column = 0xbf;
			if (shift) {
				row2 = 0x7f;
				column2 = 0xfd;
			}			
			break;
		// 37 -- Left
		case 37:
			row = 0xfb;
			row2 = 0xef;
			column = 0xfe;
			column2 = 0xbf;
			break;
		// 38 -- Up
		case 38:
			row = 0x7f;
			row2 = 0xef;
			column = 0xfe;
			column2 = 0xbf;
			break;
		// 39 -- Right
		case 39:
			row = 0xfb;
			column = 0xfe;
			break;
		// 40 -- Down
		case 40:
			row = 0x7f;
			column = 0xfe;
			break;
		// 44 -- Comma
		case 44:
			row = 0x7f;
			column = 0xdf;
			if (shift) {
				row2 = 0x7f;
				column2 = 0xfd;
			}
			break;
		// 45 -- Minus
		case 45:
			row = 0xf7;
			column = 0xdf;
			break;
		// 46 -- Period
		case 46:
			row = 0xef;
			column = 0xdf;
			if (shift) {
				row2 = 0x7f;
				column2 = 0xfd;
			}
			break;
		// 47 -- Slash
		case 47:
			row = 0x7f;
			column = 0xbf;
			break;
		// 48 -- 0
		case 48:
			if (shift) {
				row = 0xfe;
				column = 0xef;
				row2 = 0x7f;
				column2 = 0xfd;
			} else {
				row = 0xf7;
				column = 0xef;
			}
			break;
		// 49 -- 1
		case 49:
			row = 0xfe;
			column = 0x7f;
			break;
		// 50 -- 2
		case 50:
			row = 0xf7;
			column = 0x7f;
			if (shift) {
				row2 = 0x7f;
				column2 = 0xfd;
			}
			break;
		// 51 -- 3
		case 51:
			row = 0xfe;
			column = 0xfd;
			break;
		// 52 -- 4
		case 52:
			row = 0xf7;
			column = 0xfd;
			if (shift) {
				row2 = 0xef;
				column2 = 0xbf;
			}
			break;
		// 53 -- 5
		case 53:
			row = 0xfe;
			column = 0xfb;
			break;
		// 54 -- 6
		case 54:
			row = 0xf7;
			column = 0xfb;
			break;
		// 55 -- 7
		case 55:
			row = 0xfe;
			column = 0xf7;
			break;
		// 56 -- 8
		case 56:
			if (shift) {
				row = 0xfd;
				column = 0xbf;
			} else {
				row = 0xf7;
				column = 0xf7;
			}
			break;
		// 57 -- 9
		case 57:
			if (shift) {
				// map as 8 + shift
				row = 0xf7;
				column = 0xf7;
				row2 = 0x7f;
				column2 = 0xfd;
			} else {
				row = 0xfe;
				column = 0xef;
			}
			break;
		// 59 -- Semicolon
		case 59:
			if (shift) {
				row = 0xdf;
				column = 0xdf;
			} else {
				row = 0xfb;
				column = 0xbf;
			}
			break;
		// 61 -- Equals
		case 61:
			if (shift) {
				row = 0xfe;
				column = 0xdf;
			} else {
				row = 0xdf;
				column = 0xbf;
			}
			break;
		// 65 -- A
		case 65:
			row = 0xfb;
			column = 0xfd;
			break;
		// 66 -- B
		case 66:
			row = 0xef;
			column = 0xf7;
			break;
		// 67 -- C
		case 67:
			row = 0xef;
			column = 0xfb;
			break;
		// 68 -- D
		case 68:
			row = 0xfb;
			column = 0xfb;
			break;
		// 69 -- E
		case 69:
			row = 0xbf;
			column = 0xfd;
			break;
		// 70 -- F
		case 70:
			row = 0xdf;
			column = 0xfb;
			break;
		// 71 -- G
		case 71:
			row = 0xfb;
			column = 0xf7;
			break;
		// 72 -- H
		case 72:
			row = 0xdf;
			column = 0xf7;
			break;
		// 73 -- I
		case 73:
			row = 0xfd;
			column = 0xef;
			break;
		// 74 -- J
		case 74:
			row = 0xfb;
			column = 0xef;
			break;
		// 75 -- K
		case 75:
			row = 0xdf;
			column = 0xef;
			break;
		// 76 -- L
		case 76:
			row = 0xfb;
			column = 0xdf;
			break;
		// 77 -- M
		case 77:
			row = 0xef;
			column = 0xef;
			break;
		// 78 -- N
		case 78:
			row = 0x7f;
			column = 0xef;
			break;
		// 79 -- O
		case 79:
			row = 0xbf;
			column = 0xef;
			break;
		// 80 -- P
		case 80:
			row = 0xfd;
			column = 0xdf;
			break;
		// 81 -- Q
		case 81:
			row = 0xbf;
			column = 0x7f;
			break;
		// 82 -- R
		case 82:
			row = 0xfd;
			column = 0xfb;
			break;
		// 83 -- S
		case 83:
			row = 0xdf;
			column = 0xfd;
			break;
		// 84 -- T
		case 84:
			row = 0xbf;
			column = 0xfb;
			break;
		// 85 -- U
		case 85:
			row = 0xbf;
			column = 0xf7;
			break;
		// 86 -- V
		case 86:
			row = 0x7f;
			column = 0xf7;
			break;
		// 87 -- W
		case 87:
			row = 0xfd;
			column = 0xfd;
			break;
		// 88 -- X
		case 88:
			row = 0x7f;
			column = 0xfb;
			break;
		// 89 -- Y
		case 89:
			row = 0xfd;
			column = 0xf7;
			break;
		// 90 -- Z
		case 90:
			row = 0xef;
			column = 0xfd;
			break;
		}
	}

	public static final void keyReleased() {
		column = 255;
		row = 0;

		column2 = 0;
		row2 = 0;
	}

	public static void reset() {
		column = 0; column2 = 0; row = 0; row2 = 0;
		
		// counterA
		counterA = 0xffff; latchA = 0x00;
		runA = false;
		
		IRQ = false;
	}
}
package pl.dido.c64.cpu.fast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import pl.dido.c64.emulator.Memory;

public class BCDLongTest {

	public static final void main(final String args[]) throws FileNotFoundException, IOException {		
		Memory.clear();
		MOS6510.clearREG();
		
		MOS6510.PC = 0x0200;
		Memory.fill(0x0200, new FileInputStream("bcd.bin"));
		
		long t = System.nanoTime();
		while (MOS6510.B != 16)
			MOS6510.executeNext();
		
		t = System.nanoTime() - t;
		System.out.println(t);
		
		int error = Memory.ram[11];
		System.out.println("Error: " + error);
		
		if (error == 0)
			return;
		
		System.out.println("Operands  N1 = " +  Memory.ram[0] + ", N2 = " + Memory.ram[1] + ", C = " + MOS6510.Y);
		System.out.println("Binary A = " +  Memory.ram[2] + "\nBinary Flags = " + Integer.toBinaryString(Memory.ram[3]));
		System.out.println("Decimal A = " +  Memory.ram[4] + ", Predicted A = " + Memory.ram[6]);
		
		final int flags = Memory.ram[5];
		System.out.println("Decimal Flags NVZC: " + Integer.toBinaryString(flags));
		System.out.println("Predicted Flags : NF " + ((Memory.ram[7] ^ flags) & 128) + 
				" VF " + ((Memory.ram[8]  ^ flags) & 64) + " ZF " + ((Memory.ram[9] ^ flags) & 2) + " CF " + ((Memory.ram[10] ^ flags) & 1));
	}
}

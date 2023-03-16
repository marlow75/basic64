package pl.dido.c64.cpu.fast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import pl.dido.c64.emulator.Memory;

public class FunctionalTest {

	public static final void main(final String args[]) throws FileNotFoundException, IOException {		
		Memory.clear();
		MOS6510.clearREG();
		
		MOS6510.PC = 0x0400;
		Memory.fill(10, new FileInputStream("functional.bin"));
		
		while (true) {
			System.out.println(Integer.toHexString(MOS6510.PC) + " =" + Memory.ram[0xd] + "-" + Memory.ram[0x12]);
			MOS6510.executeNext();			
		}		
	}
}

package pl.dido.c64.cpu.fast;

import org.junit.jupiter.api.Test;

import pl.dido.c64.emulator.Memory;

public class MOS6510Test {

	@Test
	public void resetTest() {
		// reset MOS6510fastedure
		// ldx 2
		// @loop
		// dex
		// bne @loop
		// brk

		MOS6510.clearREG();
		Memory.clear();
		
		Memory.kernalON = false;
		Memory.fill(0xfffc, new int[] { 0x00, 0x10 }); // reset
		Memory.fill(0x1000, new int[] { 0xa2, 0x02, 0xca, 0xd0, 0xfd, 0x00 }); // simple after cpu reset

		MOS6510.reset();

		while (MOS6510.B != 16) { // check if BRK instruction
			MOS6510.executeNext(); // execute next instruction
		}
	}

	@Test
	public void copyOnIRQ() {
// copy MOS6510fastedure (zero page)
// 		 sei
//		 lda #00
//		 sta $fb
//		 sta $fd
//		 lda #$10
//		 sta $fc
//		 lda #$11
//		 sta $fe
//		 ldx #5 - five bytes
//		 ldy #0
//		 @loop
//		 lda ($fb),y
//		 sta ($fd),y
//		 iny
//		 dex
//		 bne @loop
//		 cli
//		 brk

		Memory.clear();
		Memory.kernalON = false;
		
		Memory.fill(0xfffe, new int[] { 0x00, 0x10 }); // irq vector
		// simple program that copy fragments of ram on IRQ
		Memory.fill(0x1000, new int[] { 0x78, 0xA9, 0x00, 0x85, 0xFB, 0x85, 0xFD, 0xA9, 0x10, 0x85, 0xFC, 0xA9, 0x11, 0x85,
				0xFE, 0xA2, 0x05, 0xA0, 0x00, 0xB1, 0xFB, 0x91, 0xFD, 0xC8, 0xCA, 0xD0, 0xF8, 0x58, 0x00 });

		MOS6510.clearREG();
		MOS6510.IRQ();

		while (MOS6510.B != 16) // check if BRK instruction
			MOS6510.executeNext(); // execute next instruction

		assert (Memory.match(0x1000, 0x1100, 5)); // check if Memory is copied
	}

	@Test
	public void ADC() {

		MOS6510.clearREG();
		Memory.clear();
		// below all instruction test
		Memory.fill(0x1000,
				new int[] { 0x69, 0x0A, 0x75, 0xFA, 0x6D, 0x00, 0x20, 0x79, 0x00, 0x30, 0x61, 0xFB, 0x71, 0xFB, 0x00 });

//        adc #10        
		MOS6510.AC = 20;
		MOS6510.C = 0;
		MOS6510.V = 0;
		MOS6510.PC = 0x1000;
		MOS6510.executeNext();

		assert (MOS6510.AC == 30);
		assert (MOS6510.C == 0);
		assert (MOS6510.V == 0);

//        adc 250,X
		MOS6510.AC = 30;
		Memory.fill(252, new int[] { 240 });
		MOS6510.X = 2;
		MOS6510.executeNext();

		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 14);
//        adc $2000
		MOS6510.C = 0;
		MOS6510.AC = (byte) -10;
		Memory.fill(0x2000, new int[] { (byte) -25 });
		MOS6510.executeNext();

		assert (MOS6510.C == 1);
		assert ((byte) MOS6510.AC == -35);
//        adc $3000,Y
		MOS6510.C = 0;
		MOS6510.AC = (byte) -12;
		Memory.fill(0x3002, new int[] { 15 });
		MOS6510.Y = 2;

		MOS6510.executeNext();
		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 3);
//        adc ($fb,X)
		MOS6510.C = 0;
		MOS6510.V = 0;
		MOS6510.AC = 0xbe;
		MOS6510.X = 1;
		Memory.fill(0xfb, new int[] { 0, 2, 0x30 });
		Memory.fill(0x3002, new int[] { 0xbf });

		MOS6510.executeNext();
		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 125);
		assert (MOS6510.V == 64);

//        adc ($fb),y
		MOS6510.C = 0;
		MOS6510.AC = 55;
		MOS6510.Y = 2;
		Memory.fill(0xfb, new int[] { 0, 0x30 });
		Memory.fill(0x3002, new int[] { (byte) -5 });

		MOS6510.executeNext();
		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 50);

		// overflow and BCD arithmetic
		// adc #60 (70+60)
		MOS6510.PC = 0x1000;

		MOS6510.C = 0;
		MOS6510.V = 0;

		MOS6510.AC = 70;
		Memory.fill(0x1000, new int[] { 0x69, 60, 0x00 });

		MOS6510.executeNext();
		assert (MOS6510.C == 0);
		assert (MOS6510.V == 64);
		assert (MOS6510.AC == 130);

		// adc #50 (55+50) BCD
		MOS6510.PC = 0x1000;

		MOS6510.C = 0;
		MOS6510.D = 8;

		MOS6510.AC = 0x55;
		Memory.fill(0x1000, new int[] { 0x69, 0x50, 0x00 });

		MOS6510.executeNext();
		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 0x05);
	}

	@Test
	public void SBC() {
		MOS6510.clearREG();

		Memory.clear();
		// below all instruction test
		Memory.fill(0x1000,
				new int[] { 0xE9, 0x0A, 0xF5, 0xFA, 0xED, 0x00, 0x20, 0xF9, 0x00, 0x30, 0xE1, 0xFB, 0xF1, 0xFB });

		// SBC #$0A (36 - 10 - !carry)
		MOS6510.AC = 36;
		MOS6510.C = 0;
		MOS6510.V = 0;
		MOS6510.D = 0;
		MOS6510.PC = 0x1000;
		MOS6510.executeNext();

		assert (MOS6510.AC == 25);
		assert (MOS6510.C == 1);
		assert (MOS6510.V == 0);

		// SBC $FA,X (135 - 240)
		MOS6510.AC = 135;
		Memory.fill(0xfa + 2, new int[] { 240 });
		MOS6510.X = 2;
		MOS6510.C = 1;
		MOS6510.V = 0;
		MOS6510.executeNext();

		assert (MOS6510.AC == 151);
		assert (MOS6510.C == 0); // borrow
		assert (MOS6510.V == 0);

		// SBC $2000 (135 - 137)
		MOS6510.AC = (byte) 135;
		Memory.fill(0x2000, new int[] { 137 });
		MOS6510.C = 1;
		MOS6510.V = 64;
		MOS6510.executeNext();

		assert ((byte) MOS6510.AC == -2);
		assert (MOS6510.C == 0); // borrow
		assert (MOS6510.V == 0);

		// SBC $3000,Y (135 - 133 = 2)
		MOS6510.AC = (byte) 135;
		Memory.fill(0x3001, new int[] { 133 });
		MOS6510.Y = 1;
		MOS6510.C = 1;
		MOS6510.V = 0;
		MOS6510.executeNext();

		assert (MOS6510.AC == 2);
		assert (MOS6510.C == 1);
		assert (MOS6510.V == 64);

		// SBC ($FB,X) (135 - 15 = 120)
		MOS6510.AC = (byte) 135;
		Memory.fill(0x3004, new int[] { 15 });
		Memory.fill(0xfb, new int[] { 0, 4, 0x30 });
		MOS6510.X = 1;
		MOS6510.C = 1;
		MOS6510.V = 0;
		MOS6510.executeNext();

		assert (MOS6510.AC == 120);
		assert (MOS6510.C == 1);
		assert (MOS6510.V == 0);

		// SBC ($FB),Y (100 - 150 = -50)
		MOS6510.AC = 100;
		Memory.fill(0x3005, new int[] { 150 });
		Memory.fill(0xfb, new int[] { 0, 0x30 });
		MOS6510.Y = 5;
		MOS6510.C = 1;
		MOS6510.V = 0;
		MOS6510.executeNext();

		assert ((byte) MOS6510.AC == -50);
		assert (MOS6510.C == 0); // borrow
		assert (MOS6510.V == 0);

		// overflow and BCD arithmetic
		// sbc #170 (35 - 170)
		MOS6510.PC = 0x1000;

		MOS6510.C = 1;
		MOS6510.V = 0;

		MOS6510.AC = 35;
		Memory.fill(0x1000, new int[] { 0xE9, 170, 0x00 });

		MOS6510.executeNext();
		assert (MOS6510.C == 0);
		assert (MOS6510.V == 0);
		assert (MOS6510.AC == 121);

		// sbc #50 (45-50) BCD
		MOS6510.PC = 0x1000;

		MOS6510.C = 0; // borrowed
		MOS6510.D = 8;

		MOS6510.AC = 0x45;
		Memory.fill(0x1000, new int[] { 0xE9, 0x50, 0x00 });

		MOS6510.executeNext();
		assert (MOS6510.AC == 0x94);
		assert (MOS6510.C == 0); // borrowed		

		MOS6510.D = 0;
	}

	@Test
	void jumps() {

		// lda #10
		// php
		// pha
		// jsr @MOS6510
		// tax
		// pla
		// plp
		// jmp @next
		// @MOS6510 lda #20
		// rts
		// @next brk

		MOS6510.clearREG();
		Memory.clear();

		MOS6510.PC = 0x1000;
		Memory.fill(0x1000, new int[] { 0xA9, 0x0A, 0x08, 0x48, 0x20, 0x0D, 0x10, 0xAA, 0x68, 0x28, 0x4C, 0x10, 0x10, 0xA9,
				0x14, 0x60, 0x00 });

		while (MOS6510.B != 16) { // check if BRK instruction
			MOS6510.executeNext(); // execute next instruction
		}

		assert (MOS6510.AC == 10);
		assert (MOS6510.X == 20);
	}

	@Test
	void regXY() {

//        ldx #1
//        ldy #2
//        txa
//        clc
//        adc #2
//        tax
//        inx
//        iny
//        iny
//        dey
//        dex
//        stx $fe,y
//        sty $03,x
//        lda $01
//        sta $3000,y
//        lda $06
//        sta $3000,x
//        ldx $3000,y
//        ldy $3000,x
//
//        brk   

		MOS6510.clearREG();
		Memory.clear();

		MOS6510.PC = 0x1000;
		Memory.fill(0x1000,
				new int[] { 0xA2, 0x01, 0xA0, 0x02, 0x8A, 0x18, 0x69, 0x02, 0xAA, 0xE8, 0xC8, 0xC8, 0x88, 0xCA, 0x96,
						0xFE, 0x94, 0x03, 0xA5, 0x01, 0x99, 0x00, 0x30, 0xA5, 0x06, 0x9D, 0x00, 0x30, 0xBE, 0x00, 0x30,
						0xBC, 0x00, 0x30, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });

		while (MOS6510.B  != 16) // check if BRK instruction
			MOS6510.executeNext(); // execute next instruction

		assert (MOS6510.AC == 3);
		assert (MOS6510.X == 3);
		assert (MOS6510.Y == 3);

		Memory.match(0x01, new int[] { 0x3 });
		Memory.match(0x06, new int[] { 0x3 });

		MOS6510.clearREG();
		Memory.clear();

		Memory.fill(0x1000, new int[] { 0xe8, 0xc8 });

		MOS6510.X = 100;
		MOS6510.Y = 100;

		byte data = 100;
		for (int i = 0; i < 255; i++) {
			MOS6510.PC = 0x1000;

			MOS6510.executeNext();
			MOS6510.executeNext();

			data++;
			assert (MOS6510.X == MOS6510.Y);
			assert (MOS6510.X == (data & 0xff));
		}
	}

	@Test
	public void ACReg() {

		// ASL

//        lda #1
//        sta $1
//        sta $2
//        sta $3000
//        sta $3003
//        asl
//        sta $3
//        asl $1
//        ldx #3
//        asl $ff,x
//        asl $3000
//        asl $3000,x
//        brk

		MOS6510.clearREG();
		Memory.clear();

		MOS6510.PC = 0x1000;
		Memory.fill(0x1000,
				new int[] { 0xA9, 0x01, 0x85, 0x01, 0x85, 0x02, 0x8D, 0x00, 0x30, 0x8D, 0x03, 0x30, 0x0A, 0x85, 0x03,
						0x06, 0x01, 0xA2, 0x03, 0x16, 0xFF, 0x0E, 0x00, 0x30, 0x1E, 0x00, 0x30, 0x00, 0x00, 0x00, 0x00,
						0x00 });

		while (MOS6510.B != 16) // check if BRK instruction
			MOS6510.executeNext(); // execute next instruction

		assert (MOS6510.C == 0);
		Memory.match(0x01, new int[] { 0x2, 0x2, 0x2 });
		Memory.match(0x3000, new int[] { 0x02, 0x00, 0x00, 0x02 });

		// ASL 128 without carry
		MOS6510.clearREG();
		Memory.clear();
		MOS6510.PC = 0x1000;
		MOS6510.AC = 128;
		Memory.fill(0x1000, new int[] { 0xA });
		MOS6510.executeNext();

		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 0);

		// ROL 128 with carry
		MOS6510.clearREG();
		Memory.clear();
		MOS6510.PC = 0x1000;
		MOS6510.C = 1;
		MOS6510.AC = 128;
		Memory.fill(0x1000, new int[] { 0x2A });
		MOS6510.executeNext();

		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 1);

		// LSR 1
		MOS6510.clearREG();
		Memory.clear();
		MOS6510.PC = 0x1000;
		MOS6510.AC = 1;
		Memory.fill(0x1000, new int[] { 0x4A });
		MOS6510.executeNext();

		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 0);

		// ROR 1 with carry
		MOS6510.clearREG();
		Memory.clear();
		MOS6510.PC = 0x1000;
		MOS6510.C = 1;
		MOS6510.AC = 1;
		Memory.fill(0x1000, new int[] { 0x6A });
		MOS6510.executeNext();

		assert (MOS6510.C == 1);
		assert (MOS6510.AC == 128);
	}

	@Test
	public void INC_DEC() {

//		   lda #255
//        sta $2
//        sta $3000
//        inc $2
//        inc $3000
//        dec $2
//        dec $3000
//
//        brk

		MOS6510.clearREG();
		Memory.clear();
		MOS6510.PC = 0x1000;

		Memory.fill(0x1000, new int[] { 0xA9, 0xFF, 0x85, 0x02, 0x8D, 0x00, 0x30, 0xE6, 0x02, 0xEE, 0x00, 0x30, 0xC6, 0x02,
				0xCE, 0x00, 0x30, 0x00 });

		while (MOS6510.B != 16)
			MOS6510.executeNext();

		assert (MOS6510.AC == 255);
		Memory.match(0x02, new int[] { 0xff });
		Memory.match(0x3000, new int[] { 0xff });
	}

	@Test
	public void CMP() {

//        ; immediate
//        lda #-128
//        cmp #127
//
//        ; zero page
//        lda #10
//        sta $3
//        lda #20
//        cmp $3
//
//        ldx #3
//        lda #10
//        sta $2
//        lda #20
//        cmp $ff,x
//
//        ; absolute
//        lda #255
//        sta $3005
//        lda #150
//        cmp $3005
//
//        ; absolute,X
//        lda #255
//        ldx #5
//        sta $3005
//        lda #150
//        cmp $3000,x
//
//        ; absolute,Y
//        lda #255
//        ldy #5
//        sta $3005
//        lda #150
//        cmp $3000,y
//
//        ; (zero page),y
//        lda #00
//        sta $fb
//        lda #30
//        sta $fc
//        ldy #5
//        lda #127
//        cmp ($fb),y
//
//        ; (zero page + x)
//        lda #00
//        sta $f6
//        lda #30
//        sta $f7
//        ldx #5
//        lda #127
//        cmp ($fb),y
//        brk

		MOS6510.clearREG();
		Memory.clear();
		MOS6510.PC = 0x1000;

		Memory.fill(0x1000,
				new int[] { 0xA9, 0x80, 0xC9, 0x7F, 0xA9, 0x0A, 0x85, 0x03, 0xA9, 0x14, 0xC5, 0x03, 0xA2, 0x03, 0xA9,
						0x0A, 0x85, 0x02, 0xA9, 0x14, 0xD5, 0xFF, 0xA9, 0xFF, 0x8D, 0x05, 0x30, 0xA9, 0x96, 0xCD, 0x05,
						0x30, 0xA9, 0xFF, 0xA2, 0x05, 0x8D, 0x05, 0x30, 0xA9, 0x96, 0xDD, 0x00, 0x30, 0xA9, 0xFF, 0xA0,
						0x05, 0x8D, 0x05, 0x30, 0xA9, 0x96, 0xD9, 0x00, 0x30, 0xA9, 0x00, 0x85, 0xFB, 0xA9, 0x1E, 0x85,
						0xFC, 0xA0, 0x05, 0xA9, 0x7F, 0xD1, 0xFB, 0xA9, 0x00, 0x85, 0xF6, 0xA9, 0x1E, 0x85, 0xF7, 0xA2,
						0x05, 0xA9, 0x7F, 0xD1, 0xFB, 0x00 });
		// immediate
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.N == 0);
		assert (MOS6510.V == 0);
		assert (MOS6510.C == 1);

		// zero page
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.N == 0);
		assert (MOS6510.V == 0);
		assert (MOS6510.C == 1);

		// zero page,x
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.N == 0);
		assert (MOS6510.V == 0);
		assert (MOS6510.C == 1);

		// absolute
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.N == 128);
		assert (MOS6510.V == 0);
		assert (MOS6510.C == 0);

		// absolute,x
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.N == 128);
		assert (MOS6510.V == 0);
		assert (MOS6510.C == 0);

		// absolute,y
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.N == 128);
		assert (MOS6510.V == 0);
		assert (MOS6510.C == 0);

		// (zero page),y
		Memory.fill(0x1e05, new int[] { 128 });

		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.N == 128);
		assert (MOS6510.V == 0);
		assert (MOS6510.C == 0);

		// (zero page),y
		Memory.fill(0x1e00, new int[] { 128 });

		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.N == 128);
		assert (MOS6510.V == 0);
		assert (MOS6510.C == 0);
	}

	@Test
	public void D012() {

//        lda $d012
//        bne @loop

		MOS6510.clearREG();
		Memory.clear();
		MOS6510.PC = 0x1000;
		Memory.fill(0xd012, new int[] { 100 }); // raster = 100

		Memory.fill(0x1000, new int[] { 0xAD, 0x12, 0xD0, 0xD0, 0xFB, 0x00 });
		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.PC == 0x1000);
		Memory.fill(0xd012, new int[] { 0 }); // raster = 0

		MOS6510.executeNext();
		MOS6510.executeNext();

		assert (MOS6510.PC == 0x1005);
	}

	@Test
	public void IOInit() {
		
//		; restore I/O vectors
//
//		FD15   A2 30      LDX #$30   ; low  FD30
//		FD17   A0 FD      LDY #$FD   ; high FD30
//		FD19   18         CLC
//
//		; set I/O vectors depending on XY
//
//		FD1A   86 C3      STX $C3
//		FD1C   84 C4      STY $C4
//		FD1E   A0 1F      LDY #$1F
//		FD20   B9 14 03   LDA $0314,Y
//		FD23   B0 02      BCS $FD27
//		FD25   B1 C3      LDA ($C3),Y
//		FD27   91 C3      STA ($C3),Y
//		FD29   99 14 03   STA $0314,Y
//		FD2C   88         DEY
//		FD2D   10 F1      BPL $FD20

		Memory.kernalON = false;
		Memory.clear();

		MOS6510.clearREG();
		MOS6510.PC = 0xFD15;

		Memory.fill(0xFD15, new int[] { 0xA2, 0x30, 0xA0, 0xFD, 0x18, 0x86, 0xC3, 0x84, 0xC4, 0xA0, 0x1F, 0xB9, 0x14, 0x03,
				0xB0, 0x02, 0xB1, 0xC3, 0x91, 0xC3, 0x99, 0x14, 0x03, 0x88, 0x10, 0xF1, 0x00, 0x31, 0xEA, 0x66, 0xFE,
				0x47, 0xFE, 0x4A, 0xF3, 0x91, 0xF2, 0x0E, 0xF2, 0x50, 0xF2, 0x33, 0xF3, 0x57, 0xF1, 0xCA, 0xF1, 0xED,
				0xF6, 0x3E, 0xF1, 0x2F, 0xF3, 0x66, 0xFE, 0xA5, 0xF4, 0xED, 0xF5, 0x00, 0x00, 0x00, 0x00, 0x00 });

		while (MOS6510.B != 16) // check if BRK instruction
			MOS6510.executeNext(); // execute next instruction

		assert (Memory.match(0x0314,
				new int[] { 0x31, 0xEA, 0x66, 0xFE, 0x47, 0xFE, 0x4A, 0xF3, 0x91, 0xF2, 0x0E, 0xF2, 0x50, 0xF2, 0x33,
						0xF3, 0x57, 0xF1, 0xCA, 0xF1, 0xED, 0xF6, 0x3E, 0xF1, 0x2F, 0xF3, 0x66, 0xFE, 0xA5, 0xF4, 0xED,
						0xF5 }));
	}
	
	@Test
	public void GC() {
		Memory.clear();
		MOS6510.clearREG();
		
		Memory.fill(0x2c, new int[] { 8 } );
		Memory.fill(0x34, new int[] { 0x9f } );
		
		MOS6510.PC = 0x1000;
		Memory.fill(0x1000, new int[] {
				0xA4, 0x2C, 0xC4, 0x34, 0x90, 0x05, 0xD0, 0x06,
				0xA9, 0x00, 0x00, 0xA9, 0x01, 0x00, 0xA9, 0x03,
				0x00, 0x00
		} );
		
		while (MOS6510.B != 16)
			MOS6510.executeNext();
		
		assert MOS6510.AC == 1;
	} 

	@Test
	public void CNZFlags() {
		for (int a = 0; a < 256; a++)
			for (int b = 0; b < 256; b++) {

				MOS6510.sub(a, b); // without carry CMP, CPY, CPX

				if (a < b)
					assert  MOS6510.C == 0 : a + " " + b;

				if (a > b)
					assert MOS6510.C == 1 : a + " " + b;

				if (a == b)
					assert  MOS6510.Z == 2 : a + " " + b;
				else
					assert  MOS6510.Z == 0: a + " " + b;
			}
	}
	
	@Test
	public void basicFree() {
		Memory.clear();
		MOS6510.clearREG();
		
		Memory.fill(0x2b, new int[] { 1, 8 } );
		Memory.fill(0x37, new int[] { 0, 0xa0 } );
		
		MOS6510.PC = 0x1000;
		Memory.fill(0x1000, new int[] {
				0xA5, 0x37, 0x38, 0xE5, 0x2B, 0xAA, 0xA5, 0x38, 0xE5, 0x2C, 0x00
		});
		
		while (MOS6510.B != 16)
			MOS6510.executeNext();
		
		assert MOS6510.AC * 256 + MOS6510.X == 38911; 
	}
	
	@Test
	public void bcdZFlag() {
		
//        sed
//        clc
//        lda #$99
//        adc #$01
//        cld
//        rts
		
//		after adding 99 + 1 Z flag is not set 
		
		Memory.clear();
		MOS6510.clearREG();
		
		MOS6510.PC = 0x1000;
		Memory.fill(0x1000, new int[] {
				0xF8, 0x18, 0xA9, 0x99, 0x69, 0x01, 0xD8
		});
		
		while (MOS6510.B != 16)
			MOS6510.executeNext();
		
		assert MOS6510.Z == 0; 
	}
}
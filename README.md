# SIMPLE Microsoft Basic emulator on C64

Simple C64 MS Basic emulator. You can run simple basic and machine code programs.

Features:
- 6502 documented ops
- runs PRG files
- keyboard matrix (not all keys)
- CIA timers (jiffis)
- VIC graphics and text modes (bad lines)
- C64 memory layout & banking
- Microsoft BASIC interpreter
- Commodore Kernel ROM

Runs at PAL speed.

In the future
- Sprites, hardware scrolling, VIC interrupts
- better CPU/memory emulation (write cycle)
- simple SID
- full CIA, TOD and serial emulation

Have fun and feel free to grab and modify

Alt+F10 - loads PRG file into memory

Before running shoplifting-c.prg, first stealth game ever you must run pet4032.prg emulator. It will setup pet memory configuration.
Credit for Robin, 8-bit Show and Tell for improved version of it.

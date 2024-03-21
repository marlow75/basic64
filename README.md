# SIMPLE Microsoft Basic emulator on C64

Simple C64 MS Basic emulator. You can run simple basic and machine code programs.

Features:
- 6502 documented ops
- runs PRG files
- keyboard matrix (not all keys)
- CIA timers (jiffies)
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

# Keys

- F10 - loads PRG file into memory at the headers address
- F11 - dumps all RAM memory
- F12 - machine cold reset

# PRG example files

- pet4032 - simple Commodore PET4032 emulator in Basic.
- shoplifting-c.prg - first stealth game ever written in Basic. Requires PET4032 emulator. Credit to Robin, 8-bit Show and Tell for improved version of it.
- themil, snoopy_hires - hires picture, machine coded
- 1 line game, 10print, fala - one liners, little effort, great effect
- nonmonochrome - interesting 6 bytes DEMO. Scanes all C64 memory, RAM gives monochrome stripes, ROM color ones.

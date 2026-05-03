# SIMPLE C64 emulator 

Simple C64 emulator. You can run simple basic and machine code programs.

Features:
- 6502 documented ops
- runs PRG files
- emulated keyboard matrix (not all keys)
- CIA timers (jiffies)
- SID emulation, 3 fave forms, ADSR
- VIC graphics and text modes, sprites and IRQ (bad lines)
- C64 memory layout & banking
- Microsoft BASIC interpreter
- Commodore Kernel ROM
- Simple PAL emulation (S-Video quality)

Runs at PAL speed.

In the future
- better CPU/memory emulation (write cycle)
- full CIA, TOD and serial emulation

Have fun and feel free to grab and modify

# Keys

- F10 - loads PRG file into memory at the headers address
- F11 - dumps all RAM memory
- F12 - machine cold reset

# PRG example files

- pet4032 - simple Commodore PET4032 emulator in Basic.
- SAM - speech synthesis program, load sam.prg and boot sam.prg, run 150  
- shoplifting-c.prg - first stealth game ever written in Basic. Requires PET4032 emulator. Credit to Robin, 8-bit Show and Tell for improved version of it.
- themil, snoopy_hires - hires picture, machine coded
- basicmultiplexer - simple basic sprites demo (more than 8 at ones)
- ballon - simple basic sprite demo
- notlandung - simple basic demo with sound and sprites
- sid, dance sample, organ, synth sample - SID emulation testers
- 1 line game, 10print, fala - one liners, little effort, great effect
- nonmonochrome - interesting 6 bytes DEMO. Scanes all C64 memory, RAM gives monochrome stripes, ROM color ones.

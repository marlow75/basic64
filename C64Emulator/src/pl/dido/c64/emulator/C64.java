package pl.dido.c64.emulator;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import pl.dido.c64.cpu.MOS6502;

public class C64 {

	private static double clock = 0.985 * 1e6; // 0.985 MHz PAL

	protected static final int interval = (int) (1 / clock * 1e9); // interval between clock tick in nanoseconds
	protected static long errors = 0; // total error

	protected static long operations = 0; // total instruction counter
	protected static long ticks = 0; // total clock ticks
	
	protected static long irqs = 0; // total interrupt count
	protected static long delta;
	
	protected static boolean run = true;

	private static final int width = 403;
	private static final int height = 284;

	private static final String title = "C64 Simple BASIC emulator";
	// creating the frame
	protected static JFrame frame;

	private static void initializeVideo() {
		frame = new JFrame(title);
		frame.setSize(width * 2, height * 2);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);
		frame.addKeyListener(new KeyListener());
		frame.addWindowListener(new WindowListener());

		frame.setResizable(false);
		frame.setVisible(true);

		// creating the canvas.
		final Canvas canvas = new Canvas();

		canvas.setSize(width * 2, height * 2);
		canvas.setBackground(Color.BLACK);
		canvas.setVisible(true);
		canvas.setFocusable(false);

		// putting it all together.
		frame.add(canvas);
		frame.pack();
		canvas.createBufferStrategy(2);

		VIC2.initialize(canvas);
	}
	
	public static void reset() {
		Memory.reset();
		MOS6502.reset();
	}

	public static void main(final String args[]) throws InterruptedException, FileNotFoundException, IOException {
		reset();
		initializeVideo();

		final long time = System.currentTimeMillis();

		new Computer().start();
		while (run)
			Thread.sleep(100);

		final long elapsed = System.currentTimeMillis() - time;

		System.out.println("C64 was running: " + elapsed + " ms");
		System.out.println("Machine cycles: " + ticks + " ticks");
		System.out.println("CPU operations: " + operations / elapsed * 1000 + " op/sec");
		System.out.println("Interrupt events: " + irqs);
		System.out.println("Last loop time: " + delta + " nanos");
		System.out.println(errors + " ERR");

		System.out.println("VIC raster line: " + ((Memory.fetch(0xd011) & 128) * 2 + Memory.fetch(0xd012)));
		System.out.println("\nCPU Registers\nPC: " + Integer.toHexString(MOS6502.PC) + " AC: "
				+ Integer.toHexString(MOS6502.AC & 0xff) + " X: " + Integer.toHexString(MOS6502.X & 0xff) + " Y: "
				+ Integer.toHexString(MOS6502.Y & 0xff) + " SP: " + Integer.toHexString(MOS6502.SP & 0xff));
	}

	public static final void dump() {
		try {
			Memory.dump();
		} catch (final UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	public static void loadPRG(final File selectedFile) {
		FileInputStream is = null;
		try {
			is = new FileInputStream(selectedFile);
			final byte data[] = is.readAllBytes();

			final int start = (data[0] & 0xff) | ((data[1] & 0xff) << 8);
			final int len = data.length;

			int j = start;
			for (int i = 2; i < len; i++)
				Memory.store(j++, data[i] & 0xff);

			//if (start == 0x0801) { // assume that is basic program
				final int end = j & 0xffff;
				if (end < 0x9fff) {
					// IO
					Memory.store( 0xb, 76); // Current token during tokenization.
					Memory.store( 0xf, 2);  // Quotation mode
					Memory.store(0x23, 8); // Temporary area

					final int lo = end & 0xff;
					final int hi = end >> 8;

					Memory.store(0x2d, lo); // Pointer to beginning of variable area. (End of program plus 1.)
					Memory.store(0x2e, hi);

					Memory.store(0x2f, lo); // Pointer to beginning of array variable area.
					Memory.store(0x30, hi);

					Memory.store(0x31, lo); // Pointer to end of array variable area.
					Memory.store(0x32, hi);

					Memory.store(0x49, 1); // Device number of LOAD, SAVE and VERIFY.

					Memory.store(0x90, 64); // Serial bus output cache status
					Memory.store(0x94, 64);
					Memory.store(0xa3, 64); // EOI switch during serial bus output.

					Memory.store(0xb8, 1);  // Logical number of current file.
					Memory.store(0xb9, 96); // Secondary address of current file.
					Memory.store(0xba, 1);  // Device number of current file.

					Memory.store(0xc3, 1); // Start address for a secondary address of 0 for LOAD and VERIFY from serial
										   // bus or datasette.
					Memory.store(0xc4, 8);
					Memory.store(0xb7, 0); // first parameter of LOAD, SAVE and VERIFY or fourth parameter of OPEN.
				//}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
			}
		}
	}
}

class KeyListener extends KeyAdapter {
	@Override
	public void keyPressed(final KeyEvent event) {
		final int key = event.getKeyCode();

		switch (key) {
		case KeyEvent.VK_F10:
			final JFileChooser fc = new JFileChooser(".");
			final FileFilter filter = new FileNameExtensionFilter("C64 programs", "prg");
			fc.setFileFilter(filter);

			final int returnVal = fc.showOpenDialog(C64.frame);

			if (returnVal == JFileChooser.APPROVE_OPTION)
				C64.loadPRG(fc.getSelectedFile());

			break;
		case KeyEvent.VK_F11:
			C64.dump();
			break;
		case KeyEvent.VK_F12:
			C64.reset();
			break;
		default:
			CIA.keypressed(key, event.isShiftDown());
		}
	}

	@Override
	public void keyReleased(final KeyEvent e) {
		CIA.keyReleased();
	}
}

class WindowListener extends WindowAdapter {
	@Override
	public void windowClosing(final WindowEvent event) {
		C64.run = false;
	}
}

class Computer extends Thread {
	// main Computer thread
	public void run() {
		final int interval = C64.interval;
		
		long t1 = 0, t2 = 0, wt = 0, errors = 0, operations = 0;
		int cycles, ticks = 0, delta = 985 * interval;
		
		try {
			// main loop
			t1 = System.nanoTime();
			while (C64.run) {
				int count = 0;
				long elapsed = delta;
				
				while (elapsed > 0) { // 1ms = 985 cycles for PAL
					if (!VIC2.bad_line) { // bad line stops (CPU, SID, CIA)
						if ((CIA.IRQ || VIC2.IRQ) && MOS6502.I == 0) {
							C64.irqs++;
							cycles = MOS6502.IRQ();
						} else
							cycles = MOS6502.executeNext();

						CIA.clock(cycles);
						operations++;
					} else
						cycles = 2;

					VIC2.clock(cycles);	
					count += cycles;
					
					elapsed -= cycles * interval;
				}

				ticks += count;
				
				wt = t1 + delta; // how much time instruction took
				t2 = System.nanoTime();

				if (wt > t2)
					Thread.sleep(0, (int) (wt - t2));
				else
					errors++;

				t1 = t2;
			}
		} catch (final InterruptedException ex) {
			// do nothing
		} finally {
			C64.delta = delta;
			C64.ticks = ticks;
			
			C64.errors = errors;
			C64.operations = operations;
		}
	}
}
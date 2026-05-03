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

import javax.sound.sampled.LineUnavailableException;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import pl.dido.c64.cpu.MOS6502;
import pl.dido.c64.sound.SoundDriver;

public class C64 {

	// 0.985 MHz PAL
	public static final double CLOCK_HZ = 0.985248 * 1e6;

	// dok³adny czas jednego cyklu CPU
	protected static final long NS_PER_CYCLE = 1015;

	// sta³y kwant czasu pêtli 1/50 pal
	protected static final long DELTA = 9_852;

	// statystyki
	protected static long errors = 0;
	protected static long operations = 0;

	protected static long ticks = 0;
	protected static long irqs = 0;

	protected static long delta;
	protected static boolean run = true;

	private static final int width = 720;
	private static final int height = 576;

	private static final String title = "C64 Simple emulator";
	protected static JFrame frame;

	private static void initializeVideo() {
		frame = new JFrame(title);
		frame.setSize(width, height);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);
		frame.addKeyListener(new KeyListener());
		frame.addWindowListener(new WindowListener());

		frame.setResizable(false);
		frame.setVisible(true);

		final Canvas canvas = new Canvas();
		canvas.setSize(width, height);
		canvas.setBackground(Color.BLACK);
		canvas.setVisible(true);
		canvas.setFocusable(false);

		frame.add(canvas);
		frame.pack();
		canvas.createBufferStrategy(2);

		VIC2.initialize(canvas);
	}

	public static void main(final String args[])
			throws InterruptedException, FileNotFoundException, IOException, LineUnavailableException {
		Computer.reset();

		initializeVideo();
		final long time = System.currentTimeMillis();

		new Computer().start();
		while (run)
			Thread.sleep(100);

		final long elapsed = System.currentTimeMillis() - time;

		System.out.println("C64 was running: " + elapsed + " ms");
		System.out.println("Machine cycles: " + ticks + " ticks");
		System.out.println("CPU operations: " + (elapsed > 0 ? operations / elapsed * 1000 : 0) + " op/sec");
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

			final int end = j & 0xffff;
			if (end < 0x9fff) {
				Memory.store(0xb, 76);
				Memory.store(0xf, 2);
				Memory.store(0x23, 8);

				final int lo = end & 0xff;
				final int hi = end >> 8;

				Memory.store(0x2d, lo);
				Memory.store(0x2e, hi);

				Memory.store(0x2f, lo);
				Memory.store(0x30, hi);

				Memory.store(0x31, lo);
				Memory.store(0x32, hi);

				Memory.store(0x49, 1);

				Memory.store(0x90, 64);
				Memory.store(0x94, 64);
				Memory.store(0xa3, 64);

				Memory.store(0xb8, 1);
				Memory.store(0xb9, 96);
				Memory.store(0xba, 1);

				Memory.store(0xc3, 1);
				Memory.store(0xc4, 8);
				Memory.store(0xb7, 0);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (is != null)
					is.close();
			} catch (IOException e) {
				// ignore
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
			Computer.reset();
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
	private static boolean halt = true;

	// precyzyjne doci¹ganie po sleep
	private static final long SPIN_GUARD_NS = 200_000L; // 200 us

	public static void reset() {
		halt = true;

		VIC2.reset();
		Memory.reset();

		CIA.reset();
		SID.reset();

		MOS6502.reset();
		halt = false;
	}

	@Override
	public void run() {

		final SoundDriver driver = new SoundDriver();
		final Thread audioThread = new Thread(driver, "AudioDriver");

		audioThread.setDaemon(true);
		audioThread.start();

		long t1;
		long t2;

		try {
			t1 = System.nanoTime();

			while (C64.run) {
				if (!halt) {
					long countCycles = 0;

					while (countCycles < C64.DELTA) {
						final int cycles;

						//if (!VIC2.bad_line) {
							if (MOS6502.I == 0 && (CIA.IRQ || VIC2.IRQ)) {
								C64.irqs++;
								cycles = MOS6502.IRQ();
							} else
								cycles = MOS6502.executeNext();
							
							C64.operations++;
						//} else
							//cycles = 1;

						CIA.clock(cycles);
						SID.clock(cycles);
						VIC2.clock(cycles);

						countCycles += cycles;
					}

					C64.ticks += countCycles;

					final long target = t1 + countCycles * C64.NS_PER_CYCLE; // ile symulowanego czasu up³yne³o
					t2 = System.nanoTime(); // ile up³yne³o naprawdê

					if (target > t2) {
						// jesteœmy zbyt szybko
						final long q = target - t2;

						// sleep wiêksza czêœæ
						if (q > SPIN_GUARD_NS + 10 * C64.NS_PER_CYCLE) {
							final long sleepNs = q - SPIN_GUARD_NS;
							final long ms = sleepNs / 1_000_000L;
							
							final int ns = (int) (sleepNs - ms * 1_000_000L);
							Thread.sleep(ms, ns);
						}

						// krótki spin do target
						while ((t2 = System.nanoTime()) < target)
							Thread.onSpinWait();

					} else
						// zabrak³o czasu
						C64.errors++;

					C64.delta = t2 - t1;
					t1 = target;
				} else {
					Thread.sleep(1000);
					t1 = System.nanoTime();
				}
			}

		} catch (final InterruptedException ex) {
			// ignore
		}
	}
}
package pl.dido.c64.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import pl.dido.c64.emulator.SID;

public final class SoundDriver implements Runnable {
	private volatile boolean running = true;
	private volatile boolean drainOnStop = false;
	private SourceDataLine line;

	private static final int SAMPLE_RATE = 44100;
	private static final int CHANNELS = 1;
	private static final int BYTES_PER_SAMPLE = 2;

	public void requestStop(final boolean drain) {
		drainOnStop = drain;
		running = false;
	}

	@Override
	public void run() {
		final short[] sidBuf = new short[1024];
		final byte[] pcm = new byte[sidBuf.length * 2];

		try {
			Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 1);
			final AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, CHANNELS,
					CHANNELS * BYTES_PER_SAMPLE, SAMPLE_RATE, false);

			line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));

			final int lineBytes = 8192; // strojenie
			line.open(fmt, lineBytes);
			line.start();
			
			while (running) {
				final int n = SID.popSamples(sidBuf, 0, sidBuf.length);
				if (n == 0) {
				    Thread.onSpinWait();
				    continue;
				}

				int p = 0;
				for (int i = 0; i < n; i++) {
					final short s = sidBuf[i];
					
					pcm[p++] = (byte) (s & 0xff);
					pcm[p++] = (byte) ((s >>> 8) & 0xff);
				}

				line.write(pcm, 0, n * 2);
			}

			if (drainOnStop)
				line.drain();
			else
				line.flush();

			line.stop();
			line.close();
		} catch (final Exception e) {
			// tu pod³¹cz swój logger
		}
	}
}
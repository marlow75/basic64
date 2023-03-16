package pl.dido.c64.emulator;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class VIC {

	private static int raster = 0;
	private static int ticks = 0;
	
	protected static boolean vblank = false;
	protected static boolean running = true;
	
	public static final void initialize(final Canvas canvas) {
		new Update(canvas).start();
	}
	
	public static final void clock(final long cycles) {
		// not accurate !!! - bad lines
		ticks += cycles;
    	if (ticks >  63) {
    		ticks -= 63;
    		
    		final int lo = (raster & 0xff);
    		final int hi = (raster & 0x100) >> 1;
    		
    		Memory.store(0xd012, lo);
    		Memory.store(0xd011, Memory.fetch(0xd011) & 127 | hi);
    		
    		if (raster < 312)  // PAL
    			raster++;
    		else { 
    			raster = 0;
    			vblank = true;
    		}
    	}
	}
}

class Update extends Thread {

	private final static int colors[] = new int[] { 0, 0xFFFFFF, 0x68372B, 0x70A4B2, 0x6F3D86, 0x588D43, 0x352879, 0xB8C76F, 
			0x6F4F25, 0x433900, 0x9A6759, 0x444444, 0x6C6C6C, 0x9AD284, 0x6C5EB5, 0x959595 };
	
	private Canvas canvas;
	private int[] pixels;
	
	private final int width = 400;
	private final int height = 284;
	
	private BufferedImage image;
	
	public  Update(final Canvas canvas) {
		this.canvas = canvas;			
			
		image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
	}
	
	public void run() {		
		final BufferStrategy bufferStrategy = canvas.getBufferStrategy();
		final Graphics graphics = bufferStrategy.getDrawGraphics();
		
		long wait = 8;
		while (VIC.running) {			
			// wait for VBLANK
			while (!VIC.vblank)
				try {
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					return;
				}
			
			VIC.vblank = false;
			long t = System.currentTimeMillis();			
			final int fc = colors[Memory.ports[0x20]];
				
			for (int screenY = 0; screenY < 284; screenY++) {
				int index = screenY * 400;; 
				
				if (screenY < 42 || screenY > 241) {
					for (int i = 0; i < 100; i++) { // fill all line
						pixels[index++] = fc;
						pixels[index++] = fc;
						pixels[index++] = fc;
						pixels[index++] = fc;
					}
					
					continue;		
				}
				
				final int y = screenY - 42;  
				final int row = y >>> 3;		 // ROM character row
				final int pos = y & 0b111;   // character position in charset ROM
				
				for (int screenX = 0; screenX < 50; screenX++) {
					if (screenX < 5 || screenX > 44) {						
						pixels[index++] = fc;
						pixels[index++] = fc;
						pixels[index++] = fc;
						pixels[index++] = fc;
						
						pixels[index++] = fc;
						pixels[index++] = fc;
						pixels[index++] = fc;
						pixels[index++] = fc;
						
						continue;
					}
				
					final int x = 40 * row + screenX - 5;
					final int code = Memory.ram[0x0400 + x];;
					
					final int pen = colors[Memory.ports[0x800 + x]];
					final int bc = colors[Memory.ports[0x21]];
					
					final int ch = Memory.chargen[8 * code + pos];
						
					// draw character on screen
					pixels[index++] = ((ch & 0x80) != 0) ? pen : bc; // foreground / background
					pixels[index++] = ((ch & 0x40) != 0) ? pen : bc; // foreground / background
					pixels[index++] = ((ch & 0x20) != 0) ? pen : bc; // foreground / background
					pixels[index++] = ((ch & 0x10) != 0) ? pen : bc; // foreground / background
					
					pixels[index++] = ((ch & 0x8) != 0) ? pen : bc; // foreground / background
					pixels[index++] = ((ch & 0x4) != 0) ? pen : bc; // foreground / background
					pixels[index++] = ((ch & 0x2) != 0) ? pen : bc; // foreground / background
					pixels[index++] = ((ch & 0x1) != 0) ? pen : bc; // foreground / background
				}
			}

			// draw image
			graphics.drawImage(image, 
					0, 0, canvas.getWidth(), canvas.getHeight(),
					0, 0, image.getWidth(), image.getHeight(),
					null
			);
			
			bufferStrategy.show();
			
			wait = 20 - (System.currentTimeMillis() -  t);
			if (wait < 0)
				wait = 0;
		}
		
		graphics.dispose();
	}
}
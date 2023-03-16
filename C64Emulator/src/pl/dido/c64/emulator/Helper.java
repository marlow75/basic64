package pl.dido.c64.emulator;

public class Helper {

	public static final String padLeftZeros(final String inputString, final int length) {
		if (inputString.length() >= length)
			return inputString;

		final StringBuilder sb = new StringBuilder();
		while (sb.length() < length - inputString.length())
			sb.append('0');

		sb.append(inputString);

		return sb.toString();
	}

	public static final char screen2Petscii(int a) {
		
		if (a >= 128 && a <= 159)
			a -= 128;
		else
		if (a >= 0 && a <= 31)
			a += 64;
		else
		if (a >= 64 && a <= 95)
			a += 32;
		else
		if (a >= 192 && a <= 223)
			a -= 64;
		else
		if (a >= 96 && a <= 127)
			a += 64;
		else
		if (a >= 64 && a <= 95)
			a += 128;
		else
		if (a >= 96 && a <= 126)
			a += 128;
		
		return (char)a;
	}
}
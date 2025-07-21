package com.jackbendtsen.aserver;

import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;

public class ByteVector {
	public byte[] buf;
	public int size;

	public void readAll(InputStream is) throws IOException {
		int newCap = 4096;
		if (buf != null && buf.length > 0) {
			newCap = buf.length;
		}

		while (true) {
			if (newCap > buf.length) {
				byte[] newBuf = null;
				if (buf != null)
					newBuf = Arrays.copyOf(buf, newCap);
				else
					newBuf = new byte[newCap];
				buf = newBuf;
			}

			int res = is.read(buf, size, buf.length - size);
			if (res <= 0) {
				break;
			}
			size += res;

			newCap = size;
			while (newCap <= buf.length) {
				newCap = ((newCap + 1) * 17) / 10;
			}
		}
	}
}

package com.jackbendtsen.aserver;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferRecycler {
	static final ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<ByteBuffer>();

	public static ByteBuffer acquire(int minSize) {
		ByteBuffer buf;
		do {
			buf = queue.poll();
			if (buf == null)
				return ByteBuffer.allocate(minSize);
		}
		while (buf.capacity() < minSize);

		buf.position(0);
		buf.limit(minSize);
		return buf;
	}

	public static void release(ByteBuffer buf) {
		if (buf != null)
			queue.offer(buf);
	}
}

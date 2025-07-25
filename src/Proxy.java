package com.jackbendtsen.aserver;

import android.os.Handler;
import java.nio.channels.*;

// TODO: Maybe dispatch inside some completion handlers to avoid stack overflow?
// TODO: Properly handle failure, disconnection
// TODO: Does this design result in writing to a socket while reading from it? Is that allowed?
// TODO: Investigate if requestBuf and responseBuf are being modified concurrently
// TODO: Fix circular dependencies?

public class Proxy {
	final Handler taskRunner;
	final InetSocketAddress incomingAddr;
	final InetSocketAddress outgoingAddr;
	final InetSocketAddress destAddr;
	final AsynchronousSocketChannel client;
	final ByteBuffer requestBuf;
	final ByteBuffer responseBuf;

	class IncomingAccept implements CompletionHandler<AsynchronousSocketChannel, Void> {
		public void completed(AsynchronousSocketChannel conn, Void attachment) {
			requestBuf.position(0);
			conn.read(requestBuf, null, new IncomingRead(this, conn));
		}
		public void failed(Throwable ex, Void attachment) {
			
		}
	}

	class IncomingRead implements CompletionHandler<Integer, Void> {
		final IncomingAccept acceptHandler;
		final OutgoingWrite writeHandler;
		final AsynchronousSocketChannel conn;

		public (IncomingAccept acceptHandler, AsynchronousSocketChannel conn) {
			this.acceptHandler = acceptHandler;
			this.writeHandler = new OutgoingWrite();
			this.conn = conn;
		}
		public void completed(Integer bytesRead, Void attachment) {
			if (bytesRead > 0) {
				requestBuf.position(0);
				client.write(requestBuf, null, writeHandler);
			}
		}
		public void failed(Throwable ex, Void attachment) {
			
		}
	}

	class OutgoingWrite implements CompletionHandler<Integer, Void> {
		public void completed(Integer bytesWritten, Void attachment) {
			requestBuf.position(0);
			conn.read(requestBuf, null, incomingRead);
			responseBuf.position(0);
			client.read(responseBuf, null, outgoingRead);
		}
		public void failed(Throwable ex, Void attachment) {
			
		}
	}

	class OutgoingRead implements CompletionHandler<Integer, Void> {
		public void completed(Integer bytesRead, Void attachment) {
			responseBuf.position(0);
			conn.write(responseBuf, null, incomingWrite);
		}
		public void failed(Throwable ex, Void attachment) {
			
		}
	}

	class IncomingWrite implements CompletionHandler<Integer, Void> {
		public void completed(Integer bytesRead, Void attachment) {
			responseBuf.position(0);
			client.read(responseBuf, null, outgoingRead);
		}
		public void failed(Throwable ex, Void attachment) {
			
		}
	}

	public Proxy(Handler handler, InetSocketAddress incomingAddr, InetSocketAddress outgoingAddr, InetSocketAddress destAddr) {
		this.incomingAddr = incomingAddr;
		this.outgoingAddr = outgoingAddr;
		this.destAddr = destAddr;
		this.client = AsynchronousSocketChannel.open().bind(outgoingAddr);
		this.requestBuf = ByteBuffer.allocate(16 * 1024);
		this.responseBuf = ByteBuffer.allocate(16 * 1024);
	}

	public void acceptFirstServerRequest() {
		server.accept(null, new IncomingAccept());
	}
}

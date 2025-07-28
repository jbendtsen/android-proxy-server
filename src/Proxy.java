package com.jackbendtsen.aserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutionException;

// TODO: Maybe dispatch inside some completion handlers to avoid stack overflow?
// TODO: Properly handle failure, disconnection
// TODO: Does this design result in writing to a socket while reading from it? Is that allowed?
// TODO: Investigate if requestBuf and responseBuf are being modified concurrently
// TODO: Fix circular dependencies?

public class Proxy {
	final MainActivity mainCtx;
	final InetSocketAddress incomingAddr;
	final InetSocketAddress outgoingAddr;
	final InetSocketAddress destAddr;
	final ByteBuffer requestBuf;
	final ByteBuffer responseBuf;
	final AtomicBoolean isConnected;
	AsynchronousServerSocketChannel curServer;
	AsynchronousSocketChannel curClient;
	Flow curFlow;

	class Flow {
		final IncomingAccept  incomingAccept  = new IncomingAccept();
		final OutgoingConnect outgoingConnect = new OutgoingConnect();
		final IncomingRead    incomingRead    = new IncomingRead();
		final OutgoingWrite   outgoingWrite   = new OutgoingWrite();
		final OutgoingRead    outgoingRead    = new OutgoingRead();
		final IncomingWrite   incomingWrite   = new IncomingWrite();

		class IncomingAccept implements CompletionHandler<AsynchronousSocketChannel, Void> {
			public void completed(AsynchronousSocketChannel conn, Void attachment) {
				if (!isConnected.getAndSet(true)) {
					curClient.connect(destAddr, conn, outgoingConnect);
				}
				else {
					requestBuf.limit(requestBuf.capacity());
					requestBuf.position(0);
					conn.read(requestBuf, conn, incomingRead);
				}
			}
			public void failed(Throwable ex, Void attachment) {
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerSetupFailure(ex, "IncomingAccept");
				});
			}
		}

		class OutgoingConnect implements CompletionHandler<Void, AsynchronousSocketChannel> {
			public void completed(Void param, AsynchronousSocketChannel conn) {
				requestBuf.limit(requestBuf.capacity());
				requestBuf.position(0);
				conn.read(requestBuf, conn, incomingRead);
			}
			public void failed(Throwable ex, AsynchronousSocketChannel conn) {
				try { conn.close(); }
				catch (Exception ignored) {}
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerSetupFailure(ex, "OutgoingConnect");
				});
			}
		}

		class IncomingRead implements CompletionHandler<Integer, AsynchronousSocketChannel> {
			public void completed(Integer bytesRead, AsynchronousSocketChannel conn) {
				if (bytesRead > 0) {
					requestBuf.position(0);
					requestBuf.limit(bytesRead);
					curClient.write(requestBuf, conn, outgoingWrite);
				}
			}
			public void failed(Throwable ex, AsynchronousSocketChannel conn) {
				try { conn.close(); }
				catch (Exception ignored) {}
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerConnectionFailure(ex, "IncomingRead");
				});
			}
		}

		class OutgoingWrite implements CompletionHandler<Integer, AsynchronousSocketChannel> {
			public void completed(Integer bytesWritten, AsynchronousSocketChannel conn) {
				requestBuf.limit(requestBuf.capacity());
				requestBuf.position(0);
				conn.read(requestBuf, conn, incomingRead);
				responseBuf.limit(requestBuf.capacity());
				responseBuf.position(0);
				curClient.read(responseBuf, conn, outgoingRead);
			}
			public void failed(Throwable ex, AsynchronousSocketChannel conn) {
				try { conn.close(); }
				catch (Exception ignored) {}
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerConnectionFailure(ex, "OutgoingWrite");
				});
			}
		}

		class OutgoingRead implements CompletionHandler<Integer, AsynchronousSocketChannel> {
			public void completed(Integer bytesRead, AsynchronousSocketChannel conn) {
				if (bytesRead > 0) {
					responseBuf.position(0);
					responseBuf.limit(bytesRead);
					conn.write(responseBuf, conn, incomingWrite);
				}
			}
			public void failed(Throwable ex, AsynchronousSocketChannel conn) {
				try { conn.close(); }
				catch (Exception ignored) {}
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerConnectionFailure(ex, "OutgoingRead");
				});
			}
		}

		class IncomingWrite implements CompletionHandler<Integer, AsynchronousSocketChannel> {
			public void completed(Integer bytesRead, AsynchronousSocketChannel conn) {
				responseBuf.position(0);
				curClient.read(responseBuf, conn, outgoingRead);
			}
			public void failed(Throwable ex, AsynchronousSocketChannel conn) {
				try { conn.close(); }
				catch (Exception ignored) {}
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerConnectionFailure(ex, "IncomingWrite");
				});
			}
		}
	}

	public Proxy(MainActivity ctx, InetSocketAddress incomingAddr, InetSocketAddress outgoingAddr, InetSocketAddress destAddr) {
		this.mainCtx = ctx;
		this.incomingAddr = incomingAddr;
		this.outgoingAddr = outgoingAddr;
		this.destAddr = destAddr;
		this.requestBuf = ByteBuffer.allocate(16 * 1024);
		this.responseBuf = ByteBuffer.allocate(16 * 1024);
		this.isConnected = new AtomicBoolean(false);
	}

	public int[] acceptFirstServerRequest(AsynchronousServerSocketChannel server) throws IOException {
		this.curServer = server;
		this.curClient = AsynchronousSocketChannel.open().bind(outgoingAddr);
		this.curFlow = new Flow();
		this.curServer.accept(null, curFlow.incomingAccept);
		return new int[] {
			((InetSocketAddress)curServer.getLocalAddress()).getPort(),
			((InetSocketAddress)curClient.getLocalAddress()).getPort(),
		};
	}

	public void close() throws IOException {
		try {
			if (curClient != null)
				curClient.close();
		}
		finally {
			if (curServer != null)
				curServer.close();
		}
	}
}

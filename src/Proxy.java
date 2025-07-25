package com.jackbendtsen.aserver;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

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
	final AsynchronousSocketChannel client;
	final ByteBuffer requestBuf;
	final ByteBuffer responseBuf;
	Flow curFlow;

	class Flow {
		final IncomingAccept incomingAccept;
		final IncomingRead incomingRead;
		final OutgoingWrite outgoingWrite;
		final OutgoingRead outgoingRead;
		final IncomingWrite incomingWrite;
		AsynchronousSocketChannel conn;

		class IncomingAccept implements CompletionHandler<AsynchronousSocketChannel, Void> {
			public void completed(AsynchronousSocketChannel conn, Void attachment) {
				requestBuf.position(0);
				conn.read(requestBuf, null, incomingRead);
			}
			public void failed(Throwable ex, Void attachment) {
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerSetupFailure(ex);
				});
			}
		}

		class IncomingRead implements CompletionHandler<Integer, Void> {
			public void completed(Integer bytesRead, Void attachment) {
				if (bytesRead > 0) {
					requestBuf.position(0);
					client.write(requestBuf, null, outgoingWrite);
				}
			}
			public void failed(Throwable ex, Void attachment) {
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerConnectionFailure(ex);
				});
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
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerConnectionFailure(ex);
				});
			}
		}

		class OutgoingRead implements CompletionHandler<Integer, Void> {
			public void completed(Integer bytesRead, Void attachment) {
				responseBuf.position(0);
				conn.write(responseBuf, null, incomingWrite);
			}
			public void failed(Throwable ex, Void attachment) {
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerConnectionFailure(ex);
				});
			}
		}

		class IncomingWrite implements CompletionHandler<Integer, Void> {
			public void completed(Integer bytesRead, Void attachment) {
				responseBuf.position(0);
				client.read(responseBuf, null, outgoingRead);
			}
			public void failed(Throwable ex, Void attachment) {
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerConnectionFailure(ex);
				});
			}
		}
	}

	public Proxy(MainActivity ctx, InetSocketAddress incomingAddr, InetSocketAddress outgoingAddr, InetSocketAddress destAddr) {
		this.mainCtx = ctx;
		this.incomingAddr = incomingAddr;
		this.outgoingAddr = outgoingAddr;
		this.destAddr = destAddr;
		this.client = AsynchronousSocketChannel.open().bind(outgoingAddr);
		this.requestBuf = ByteBuffer.allocate(16 * 1024);
		this.responseBuf = ByteBuffer.allocate(16 * 1024);
	}

	public void acceptFirstServerRequest(AsynchronousServerSocketChannel server) {
		curFlow = new Flow();
		server.accept(null, curFlow.incomingAccept);
	}
}

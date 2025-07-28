package com.jackbendtsen.aserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.atomic.AtomicReference;

public class Proxy {
	static class InOutPair {
		public AsynchronousSocketChannel sIn;
		public AsynchronousSocketChannel sOut;

		public InOutPair(AsynchronousSocketChannel inSocket, AsynchronousSocketChannel outSocket) {
			this.sIn = inSocket;
			this.sOut = outSocket;
		}

		public void closeBoth() throws IOException {
			try {
				if (sIn != null)
					sIn.close();
			}
			finally {
				if (sOut != null)
					sOut.close();
			}
		}

		public void silentlyCloseBoth() {
			try {
				if (sIn != null)
					sIn.close();
			}
			catch (Exception ignored) {}
			try {
				if (sOut != null)
					sOut.close();
			}
			catch (Exception ignored) {}
		}
	}

	final MainActivity mainCtx;
	final InetSocketAddress incomingAddr;
	final InetSocketAddress outgoingAddr;
	final InetSocketAddress destAddr;
	final AsynchronousServerSocketChannel server;

	final ByteBuffer requestBuf;
	final ByteBuffer responseBuf;
	final AtomicReference<InOutPair> curSocketPair;

	final IncomingAccept  incomingAccept  = new IncomingAccept();
	final OutgoingConnect outgoingConnect = new OutgoingConnect();
	final IncomingRead    incomingRead    = new IncomingRead();
	final OutgoingWrite   outgoingWrite   = new OutgoingWrite();
	final OutgoingRead    outgoingRead    = new OutgoingRead();
	final IncomingWrite   incomingWrite   = new IncomingWrite();

	class IncomingAccept implements CompletionHandler<AsynchronousSocketChannel, Void> {
		public void completed(AsynchronousSocketChannel inSocket, Void attachment) {
			AsynchronousSocketChannel outSocket;
			try {
				outSocket = AsynchronousSocketChannel.open().bind(outgoingAddr);
			}
			catch (IOException ex) {
				mainCtx.enqueueTaskToMainThread(() -> {
					mainCtx.onServerSetupFailure(ex, "IncomingAccept");
				});
				return;
			}

			InOutPair socketPair = new InOutPair(inSocket, outSocket);
			curSocketPair.set(socketPair);
			outSocket.connect(destAddr, socketPair, outgoingConnect);
		}
		public void failed(Throwable ex, Void attachment) {
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerSetupFailure(ex, "IncomingAccept");
			});
		}
	}

	class OutgoingConnect implements CompletionHandler<Void, InOutPair> {
		public void completed(Void param, InOutPair pair) {
			requestBuf.limit(requestBuf.capacity());
			requestBuf.position(0);
			pair.sIn.read(requestBuf, pair, incomingRead);
		}
		public void failed(Throwable ex, InOutPair pair) {
			pair.silentlyCloseBoth();
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerSetupFailure(ex, "OutgoingConnect");
			});
		}
	}

	class IncomingRead implements CompletionHandler<Integer, InOutPair> {
		public void completed(Integer bytesRead, InOutPair pair) {
			if (bytesRead > 0) {
				requestBuf.position(0);
				requestBuf.limit(bytesRead);
				pair.sOut.write(requestBuf, pair, outgoingWrite);
			}
		}
		public void failed(Throwable ex, InOutPair pair) {
			pair.silentlyCloseBoth();
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerConnectionFailure(ex, "IncomingRead");
			});
		}
	}

	class OutgoingWrite implements CompletionHandler<Integer, InOutPair> {
		public void completed(Integer bytesWritten, InOutPair pair) {
			requestBuf.limit(requestBuf.capacity());
			requestBuf.position(0);
			pair.sIn.read(requestBuf, pair, incomingRead);
			responseBuf.limit(requestBuf.capacity());
			responseBuf.position(0);
			pair.sOut.read(responseBuf, pair, outgoingRead);
		}
		public void failed(Throwable ex, InOutPair pair) {
			pair.silentlyCloseBoth();
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerConnectionFailure(ex, "OutgoingWrite");
			});
		}
	}

	class OutgoingRead implements CompletionHandler<Integer, InOutPair> {
		public void completed(Integer bytesRead, InOutPair pair) {
			if (bytesRead > 0) {
				responseBuf.position(0);
				responseBuf.limit(bytesRead);
				pair.sIn.write(responseBuf, pair, incomingWrite);
			}
			else {
				pair.silentlyCloseBoth();
			}
		}
		public void failed(Throwable ex, InOutPair pair) {
			pair.silentlyCloseBoth();
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerConnectionFailure(ex, "OutgoingRead");
			});
		}
	}

	class IncomingWrite implements CompletionHandler<Integer, InOutPair> {
		public void completed(Integer bytesRead, InOutPair pair) {
			responseBuf.position(0);
			pair.sOut.read(responseBuf, pair, outgoingRead);
		}
		public void failed(Throwable ex, InOutPair pair) {
			pair.silentlyCloseBoth();
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerConnectionFailure(ex, "IncomingWrite");
			});
		}
	}

	public Proxy(
		MainActivity ctx,
		InetSocketAddress incomingAddr,
		InetSocketAddress outgoingAddr,
		InetSocketAddress destAddr,
		AsynchronousServerSocketChannel server
	) {
		this.mainCtx = ctx;
		this.incomingAddr = incomingAddr;
		this.outgoingAddr = outgoingAddr;
		this.destAddr = destAddr;
		this.server = server;
		this.requestBuf = ByteBuffer.allocate(16 * 1024);
		this.responseBuf = ByteBuffer.allocate(16 * 1024);
		this.curSocketPair = new AtomicReference<InOutPair>(null);
	}

	public int acceptFirstServerRequest() throws IOException {
		this.server.accept(null, this.incomingAccept);

		InetSocketAddress serverAddr = (InetSocketAddress)this.server.getLocalAddress();
		return serverAddr.getPort();
	}

	public void close() throws IOException {
		try {
			if (server != null)
				server.close();
		}
		finally {
			InOutPair pair = curSocketPair.get();
			if (pair != null)
				pair.closeBoth();
		}
	}
}

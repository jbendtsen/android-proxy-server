package com.jackbendtsen.aserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Proxy {
	static class InOutPair {
		public AsynchronousSocketChannel sIn;
		public AsynchronousSocketChannel sOut;

		public InOutPair(AsynchronousSocketChannel inSocket, AsynchronousSocketChannel outSocket) {
			this.sIn = inSocket;
			this.sOut = outSocket;
		}
	}
	static class Transfer {
		public InOutPair pair;
		public ByteBuffer buf;

		public Transfer(InOutPair pair, ByteBuffer buf) {
			this.pair = pair;
			this.buf = buf;
		}

		public void releaseBuffer() {
			BufferRecycler.release(this.buf);
			this.buf = null;
		}
	}

	static final int PACKET_SIZE = 16 * 1024;

	final MainActivity mainCtx;
	final InetSocketAddress incomingAddr;
	final InetSocketAddress outgoingAddr;
	final InetSocketAddress destAddr;
	final AsynchronousServerSocketChannel server;
	final ConcurrentLinkedQueue<InOutPair> activeSocketQueue;

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
			activeSocketQueue.offer(socketPair);
			outSocket.connect(destAddr, socketPair, outgoingConnect);
			server.accept(null, incomingAccept);
		}
		public void failed(Throwable ex, Void attachment) {
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerSetupFailure(ex, "IncomingAccept");
			});
		}
	}

	class OutgoingConnect implements CompletionHandler<Void, InOutPair> {
		public void completed(Void param, InOutPair pair) {
			ByteBuffer requestBuf = BufferRecycler.acquire(PACKET_SIZE);
			ByteBuffer responseBuf = BufferRecycler.acquire(PACKET_SIZE);
			pair.sIn.read(requestBuf, new Transfer(pair, requestBuf), incomingRead);
			pair.sOut.read(responseBuf, new Transfer(pair, responseBuf), outgoingRead);
		}
		public void failed(Throwable ex, InOutPair pair) {
			silentlyCloseSocketPair(pair);
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerSetupFailure(ex, "OutgoingConnect");
			});
		}
	}

	class IncomingRead implements CompletionHandler<Integer, Transfer> {
		public void completed(Integer bytesRead, Transfer t) {
			if (bytesRead >= 0) {
				Transfer nextTransfer = t;
				if (bytesRead > 0) {
					t.buf.position(0);
					t.buf.limit(bytesRead);
					t.pair.sOut.write(t.buf, t, outgoingWrite);
					nextTransfer = new Transfer(t.pair, BufferRecycler.acquire(PACKET_SIZE));
				}
				t.pair.sIn.read(nextTransfer.buf, nextTransfer, incomingRead);
			}
			else {
				BufferRecycler.release(t.buf);
				t.buf = null;
			}
		}
		public void failed(Throwable ex, Transfer t) {
			endTransfer(t);
			/*
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerConnectionFailure(ex, "IncomingRead");
			});
			*/
		}
	}

	class OutgoingWrite implements CompletionHandler<Integer, Transfer> {
		public void completed(Integer bytesWritten, Transfer t) {
			t.releaseBuffer();
		}
		public void failed(Throwable ex, Transfer t) {
			endTransfer(t);
			/*
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerConnectionFailure(ex, "OutgoingWrite");
			});
			*/
		}
	}

	class OutgoingRead implements CompletionHandler<Integer, Transfer> {
		public void completed(Integer bytesRead, Transfer t) {
			if (bytesRead >= 0) {
				Transfer nextTransfer = t;
				if (bytesRead > 0) {
					t.buf.position(0);
					t.buf.limit(bytesRead);
					t.pair.sIn.write(t.buf, t, incomingWrite);
					nextTransfer = new Transfer(t.pair, BufferRecycler.acquire(PACKET_SIZE));
				}
				t.pair.sOut.read(nextTransfer.buf, nextTransfer, outgoingRead);
			}
			else {
				endTransfer(t);
			}
		}
		public void failed(Throwable ex, Transfer t) {
			endTransfer(t);
			/*
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerConnectionFailure(ex, "OutgoingRead");
			});
			*/
		}
	}

	class IncomingWrite implements CompletionHandler<Integer, Transfer> {
		public void completed(Integer bytesRead, Transfer t) {
			t.releaseBuffer();
		}
		public void failed(Throwable ex, Transfer t) {
			endTransfer(t);
			/*
			mainCtx.enqueueTaskToMainThread(() -> {
				mainCtx.onServerConnectionFailure(ex, "IncomingWrite");
			});
			*/
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
		this.activeSocketQueue = new ConcurrentLinkedQueue<InOutPair>();
	}

	public void silentlyCloseSocketPair(InOutPair socketPair) {
		if (socketPair == null)
			return;

		activeSocketQueue.remove(socketPair);

		try {
			if (socketPair.sIn != null)
				socketPair.sIn.close();
		}
		catch (Exception ignored) {}
		try {
			if (socketPair.sOut != null)
				socketPair.sOut.close();
		}
		catch (Exception ignored) {}
	}

	public void endTransfer(Transfer t) {
		ByteBuffer b = t.buf;
		t.buf = null;
		BufferRecycler.release(b);
		silentlyCloseSocketPair(t.pair);
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
			IOException lastEx = null;
			InOutPair socketPair = null;
			while ((socketPair = activeSocketQueue.poll()) != null) {
				try {
					if (socketPair.sIn != null)
						socketPair.sIn.close();
				}
				catch (IOException ex) {
					lastEx = ex;
				}
				try {
					if (socketPair.sOut != null)
						socketPair.sOut.close();
				}
				catch (IOException ex) {
					lastEx = ex;
				}
			}
			if (lastEx != null)
				throw lastEx;
		}
	}
}

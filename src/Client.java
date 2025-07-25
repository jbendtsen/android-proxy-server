package com.jackbendtsen.aserver;

import android.os.Handler;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.*;

public class Client implements Runnable {
	public static class ClientRequest {
		public final String cmd;
		public final byte[] requestData;
		public final CompletableFuture<ByteVector> future;

		public ClientRequest(String cmd, byte[] requestData, CompletableFuture<ByteVector> future) {
			this.cmd = cmd;
			this.requestData = requestData;
			this.future = future;
		}
	}

	public final MainActivity ctx;
	public final LinkedBlockingQueue<ClientRequest> queue;
	public final InetSocketAddress srcAddr;
	public final InetSocketAddress destAddr;
	public Thread thread;

	public Client(MainActivity ctx, InetSocketAddress bindAddr, InetSocketAddress destAddr) {
		this.ctx = ctx;
		this.queue = new LinkedBlockingQueue<>();
		this.srcAddr = bindAddr;
		this.destAddr = destAddr;
		this.thread = null;
	}

	public void start() {
		if (thread == null) {
			thread = new Thread(this);
			thread.start();
		}
	}

	public void stop() {
		queue.add(new ClientRequest("close", null, null));
	}

	public Future<ByteVector> submitRequest(ByteVector requestData) {
		start();
		byte[] data = null;
		if (requestData.buf != null && requestData.size > 0)
			data = Arrays.copyOf(requestData.buf, requestData.size);

		CompletableFuture<ByteVector> future = new CompletableFuture<>();
		ClientRequest request = new ClientRequest("", data, future);
		queue.add(request);
		return future;
	}

	@Override
	public void run() {
		ClientRequest request;

		while (true) {
			request = null;
			try {
				request = queue.take();
			}
			catch (InterruptedException ex) {
				break;
			}

			if ("close".equals(request.cmd))
				break;

			ByteVector responseBv = null;
			try {
				responseBv = Utils.sendAndAwaitResponse(srcAddr, destAddr, request.requestData);
			}
			catch (Exception ignored) {}
			if (request.future != null) {
				request.future.complete(responseBv);
			}
		}

		thread = null;

		if (request != null && request.future != null) {
			request.future.cancel(false);
		}
		while ((request = queue.poll()) != null) {
			if (request != null && request.future != null) {
				request.future.cancel(false);
			}
		}
	}
}

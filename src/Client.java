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
		public final String srcAddrPortStr;
		public final String destAddrPortStr;
		public final byte[] requestData;
		public final CompletableFuture<ByteVector> future;

		public ClientRequest(String srcAddr, String destAddr, byte[] requestData, CompletableFuture<ByteVector> future) {
			this.srcAddrPortStr = srcAddr;
			this.destAddrPortStr = destAddr;
			this.requestData = requestData;
			this.future = future;
		}
	}

	public final MainActivity ctx;
	public final LinkedBlockingQueue<ClientRequest> queue;
	public Thread thread;

	public Client(MainActivity ctx) {
		this.ctx = ctx;
		this.queue = new LinkedBlockingQueue<>();
		this.thread = null;
	}

	public void start() {
		if (thread == null) {
			thread = new Thread(this);
			thread.start();
		}
	}

	public void stop() {
		queue.add(new ClientRequest("close", null, null, null));
	}

	public Future<ByteVector> submitRequest(String srcAddrPortStr, String destAddrPortStr, ByteVector requestData) {
		start();
		byte[] data = null;
		if (requestData.buf != null && requestData.size > 0)
			data = Arrays.copyOf(requestData.buf, requestData.size);

		CompletableFuture<ByteVector> future = new CompletableFuture<>();
		ClientRequest request = new ClientRequest(srcAddrPortStr, destAddrPortStr, data, future);
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

			if ("close".equals(request.srcAddrPortStr))
				break;

			InetSocketAddress srcAddr, destAddr;
			try {
				srcAddr = Utils.parseAddress(request.srcAddrPortStr);
				destAddr = Utils.parseAddress(request.destAddrPortStr);
			}
			catch (Exception ex) {
				ctx.postMessage(ex.getMessage());
				break;
			}

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

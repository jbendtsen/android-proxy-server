package com.jackbendtsen.aserver;

import android.os.Handler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class Server implements Runnable {
	public final MainActivity ctx;
	public final InetSocketAddress addr;
	public Thread thread;
	public ServerSocket curServer;

	public Server(MainActivity ctx, InetSocketAddress bindAddr) {
		this.ctx = ctx;
		this.addr = bindAddr;
		this.thread = null;
		this.curServer = null;
	}

	public abstract ByteVector handleRequest(ByteVector input);

	public void start() {
		if (thread == null) {
			thread = new Thread(this);
			thread.start();
		}
	}

	public void stop() {
		ServerSocket s = curServer;
		if (s != null) {
			try {
				s.close();
			}
			catch (IOException ignored) {}
		}
	}

	@Override
	public void run() {
		curServer = null;
		ServerSocket server = null;
		try {
			server = new ServerSocket(addr.getPort(), 50, addr.getAddress());
			curServer = server;
			String addrStr = server.getLocalSocketAddress().toString();
			int port = server.getLocalPort();

			ctx.taskRunner.post(() -> {
				ctx.showAddressAndPort(addrStr, port);
			});
		}
		catch (Exception ex) {
			ctx.taskRunner.post(() -> {
				ctx.onServerSetupFailure(ex);
			});
			return;
		}
		finally {
			thread = null;
			curServer = null;
		}

		ByteVector inputBv = new ByteVector();
		try {
			while (true) {
				Socket conn = server.accept();
				InputStream is = conn.getInputStream();
				inputBv.size = 0;
				inputBv.readAll(is);

				ByteVector outputBv = handleRequest(inputBv);

				if (outputBv != null && outputBv.size > 0) {
					OutputStream os = conn.getOutputStream();
					os.write(outputBv.buf, 0, outputBv.size);
				}
				conn.close();
			}
		}
		catch (Exception ex) {
			ctx.taskRunner.post(() -> {
				ctx.onServerConnectionFailure(ex);
			});
		}
		finally {
			thread = null;
			curServer = null;
		}
	}
}

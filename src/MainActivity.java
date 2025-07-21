package com.jackbendtsen.aserver;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.concurrent.Future;

public class MainActivity extends Activity implements View.OnClickListener {
	public Server fileServer;
	public Server proxyServer;
	public Handler taskRunner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		((Button)findViewById(R.id.fileBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.proxyBtn)).setOnClickListener(this);
		taskRunner = new Handler(this.getMainLooper());
		fileServer = new FileServer(this);
		proxyServer = new ProxyServer(this);
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.fileBtn)
			startFileServer();
		else if (id == R.id.proxyBtn)
			startProxy();
	}

	public void startFileServer() {
	}

	public void startProxy() {
		ArrayList<String> addrList = Utils.getAllIpAddresses();
		String msg = Utils.join(addrList, "\n");
		((TextView)findViewById(R.id.mainTextView)).setText(msg);
	}

	public void showAddressAndPort(String addrStr, int port) {
		((TextView)findViewById(R.id.addrView)).setText("Address: " + addrStr);
		((TextView)findViewById(R.id.portView)).setText("Port: " + port);
	}

	public void onServerSetupFailure(Exception ex) {
		((TextView)findViewById(R.id.mainTextView)).setText("Setup exception: " + ex.toString());
	}

	public void onServerConnectionFailure(Exception ex) {
		((TextView)findViewById(R.id.mainTextView)).setText("Connection exception: " + ex.toString());
	}

	public void postMessage(String msg) {
		taskRunner.post(() -> {
			((TextView)findViewById(R.id.mainTextView)).setText(msg);
		});
	}
}

class FileServer extends Server {
	public FileServer(MainActivity ctx) {
		super(ctx);
	}

	@Override
	public ByteVector handleRequest(ByteVector input) {
		String str = new String(input.buf, 0, input.size);
		ctx.postMessage(str);
		return new ByteVector();
	}
}

class ProxyServer extends Server {
	public final Client client;

	public ProxyServer(MainActivity ctx) {
		super(ctx);
		client = new Client(ctx);
	}

	@Override
	public void stop() {
		super.stop();
		client.stop();
	}

	@Override
	public ByteVector handleRequest(ByteVector input) {
		/*
		Future<ByteVector> future = client.submitRequest(srcAddrPortStr, destAddrPortStr, input);
		try {
			return future.get();
		}
		catch (Exception ignored) {}
		*/
		return null;
	}
}

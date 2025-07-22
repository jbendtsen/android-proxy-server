package com.jackbendtsen.aserver;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.concurrent.Future;

public class MainActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	public Server fileServer;
	public Server proxyServer;
	public Handler taskRunner;
	public StringDropDown addrList;
	public ScrollView sv;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		((Button)findViewById(R.id.fileBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.proxyBtn)).setOnClickListener(this);

		this.sv = (ScrollView)findViewById(R.id.mainScroll);

		addProxy();

		this.taskRunner = new Handler(this.getMainLooper());
		this.fileServer = new FileServer(this);
		this.proxyServer = new ProxyServer(this);
		this.addrList = new StringDropDown(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		refreshInterfaces();
	}

	@Override
	public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
		ViewGroup row = (ViewGroup)cb.getParent();
		/*
		Rect rowRect = getRowRect(row);
		int height = rowRect.bottom - rowRect.top + 1;
		int index = rowRect.top / height;
		*/

		Spinner inSelectView  = (Spinner)row.findViewById(R.id.inSelect);
		Spinner outSelectView = (Spinner)row.findViewById(R.id.outSelect);
		EditText destIpView   = (EditText)row.findViewById(R.id.destIp);
		EditText destPortView = (EditText)row.findViewById(R.id.destPort);

		String inSelect = (String)inSelectView.getSelectedItem();
		String outSelect = (String)outSelectView.getSelectedItem();
		String errMsg = maybeCreateProxy(inSelect, outSelect, destIpView.getText().toString(), destPortView.getText().toString());

		if (errMsg != null && !errMsg.isEmpty()) {
			postDialogBuilder(new AlertDialog.Builder(this)
				.setTitle("Failed to create proxy server")
				.setMessage(errMsg)
				.setPositiveButton(android.R.string.ok, null)
				.setIconAttribute(android.R.attr.alertDialogIcon)
			);
		}
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		switch (id) {
			case R.id.refreshBtn:
				refreshInterfaces();
				break;
			case R.id.fileBtn:
				addFileServer();
				break;
			case R.id.proxyBtn:
				addProxy();
				break;
		}
	}

	public void refreshInterfaces() {
		this.addrList.items.clear();
		Utils.getAllIpAddresses(this.addrList.items);
		this.addrList.notifyItemsUpdated();
	}

	public void addFileServer() {
	}

	public void addProxy() {
		LayoutInflater inflater = this.getLayoutInflater();
		LinearLayout proxyUi = (LinearLayout)inflater.inflate(R.layout.proxy_form, null, false);
		CheckBox cb = (CheckBox)proxyUi.findViewById(R.id.proxyEnabled);
		cb.setOnCheckedChangeListener(this);
		Spinner incomingSpinner = (Spinner)proxyUi.findViewById(R.id.inSelect);
		incomingSpinner.setAdapter(this.addrList);
		Spinner outgoingSpinner = (Spinner)proxyUi.findViewById(R.id.inSelect);
		outgoingSpinner.setAdapter(this.addrList);
		this.sv.addView(proxyUi);
	}

	public Rect getRowRect(ViewGroup row) {
		Rect rect = new Rect();
		row.getDrawingRect(rect);
		this.sv.offsetDescendantRectToMyCoords(row, rect);
		return rect;
	}

	public String maybeCreateProxy(String incomingIp, String outgoingIp, String destIp, String destPort) {
		// TODO: make a string key from the inputs, lookup combination in hashmap to see if its already created
		// TODO: if so, return immediately with ""
		// TODO: validate inputs and return error message if not valid
		// TODO: if successful, create a new ProxyServer, start it and add it to the map
		return "Not implement yet ;)";
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

	public void postDialogBuilder(AlertDialog.Builder db) {
		taskRunner.post(() -> {
			db.show();
		});
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

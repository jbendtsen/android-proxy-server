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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class MainActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	public ConcurrentHashMap<String, Proxy> proxyMap;
	public Handler taskRunner;
	public GenericDropDown<InterfaceIp> addrList;
	public LinearLayout svPage;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		((Button)findViewById(R.id.refreshBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.fileBtn)).setOnClickListener(this);
		((Button)findViewById(R.id.proxyBtn)).setOnClickListener(this);

		this.svPage = (LinearLayout)findViewById(R.id.mainScrollContent);
		this.addrList = new GenericDropDown<InterfaceIp>(this);

		this.proxyMap = new ConcurrentHashMap<String, Proxy>();
		this.taskRunner = new Handler(this.getMainLooper());
	}

	@Override
	public void onStart() {
		super.onStart();
		refreshInterfaces();
		if (this.svPage.getChildCount() == 0)
			addProxy();
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
		int inSelectIdx = inSelectView.getSelectedItemPosition();
		if (inSelectIdx < 0)
			return;

		Spinner outSelectView = (Spinner)row.findViewById(R.id.outSelect);
		int outSelectIdx = outSelectView.getSelectedItemPosition();
		if (outSelectIdx < 0)
			return;

		InterfaceIp inIfIp = addrList.items.get(inSelectIdx);
		InterfaceIp outIfIp = addrList.items.get(outSelectIdx);

		EditText destIpView   = (EditText)row.findViewById(R.id.destIp);
		EditText destPortView = (EditText)row.findViewById(R.id.destPort);

		String errMsg = maybeCreateProxy(inIfIp, outIfIp, destIpView.getText().toString(), destPortView.getText().toString());

		if (errMsg != null && !errMsg.isEmpty()) {
			AlertDialog.Builder db = new AlertDialog.Builder(this)
				.setTitle("Failed to create proxy server")
				.setMessage(errMsg)
				.setPositiveButton(android.R.string.ok, null)
				.setIconAttribute(android.R.attr.alertDialogIcon);
			enqueueTaskToMainThread(() -> { db.show(); });
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
		Spinner outgoingSpinner = (Spinner)proxyUi.findViewById(R.id.outSelect);
		outgoingSpinner.setAdapter(this.addrList);
		this.svPage.addView(proxyUi);
	}

	public Rect getRowRect(ViewGroup row) {
		Rect rect = new Rect();
		row.getDrawingRect(rect);
		this.svPage.offsetDescendantRectToMyCoords(row, rect);
		return rect;
	}

	public String maybeCreateProxy(InterfaceIp inIfIp, InterfaceIp outIfIp, String destIp, String destPort) {
		// TODO: make a string key from the inputs, lookup combination in hashmap to see if its already created
		// TODO: if so, return immediately with ""
		// TODO: validate inputs and return error message if not valid
		// TODO: if successful, create a new ProxyServer, start it and add it to the map
		InetSocketAddress destAddr;
		try {
			destAddr = Utils.makeAddressFromIpPort(destIp, destPort);
		}
		catch (Exception ex) {
			return Utils.getExceptionAsString(ex);
		}

		String key = inIfIp.toString() + "_" + outIfIp.toString() + "_" + destAddr.toString();
		if (proxyMap.containsKey(key)) {
			return "";
		}

		InetSocketAddress incomingAddr = new InetSocketAddress(inIfIp.addr, 0);
		InetSocketAddress outgoingAddr = new InetSocketAddress(outIfIp.addr, 0);

		AsynchronousServerSocketChannel server;
		try {
			server = AsynchronousServerSocketChannel.open().bind(incomingAddr);
			Proxy proxy = new Proxy(this, incomingAddr, outgoingAddr, destAddr);
			proxy.acceptFirstServerRequest(server);
			proxyMap.put(key, proxy);
		}
		catch (IOException ex) {
			return Utils.getExceptionAsString(ex);
		}

		return "";
	}

	public void showAddressAndPort(String addrStr, int port) {
		((TextView)findViewById(R.id.addrView)).setText("Address: " + addrStr);
		((TextView)findViewById(R.id.portView)).setText("Port: " + port);
	}

	public void onServerSetupFailure(Throwable ex) {
		((TextView)findViewById(R.id.mainTextView)).setText("Setup exception: " + ex.toString());
	}

	public void onServerConnectionFailure(Throwable ex) {
		((TextView)findViewById(R.id.mainTextView)).setText("Connection exception: " + ex.toString());
	}

	public void enqueueTaskToMainThread(Runnable runnable) {
		taskRunner.post(runnable);
	}
}

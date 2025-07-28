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
import java.util.HashMap;
import java.util.concurrent.Future;

public class MainActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
	public HashMap<String, Proxy> proxyMap;
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

		this.proxyMap = new HashMap<String, Proxy>();
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

		Spinner outSelectView  = (Spinner)row.findViewById(R.id.outSelect);
		int outSelectIdx = outSelectView.getSelectedItemPosition();
		if (outSelectIdx < 0)
			return;

		EditText destIpView   = (EditText)row.findViewById(R.id.destIp);
		EditText destPortView = (EditText)row.findViewById(R.id.destPort);

		String destIp = destIpView.getText().toString();
		String destPort = destPortView.getText().toString();
		if (destIp == "" || destPort == "")
			return;

		InterfaceIp inIfIp = addrList.items.get(inSelectIdx);
		InterfaceIp outIfIp = addrList.items.get(outSelectIdx);

		TextView inPortView = (TextView)row.findViewById(R.id.inPort);
		TextView outPortView = (TextView)row.findViewById(R.id.outPort);

		if (isChecked) {
			String errMsg = maybeCreateProxy(inIfIp, outIfIp, destIp, destPort, inPortView, outPortView);

			if (errMsg != null && !errMsg.isEmpty()) {
				AlertDialog.Builder db = new AlertDialog.Builder(this)
					.setTitle("Failed to create proxy server")
					.setMessage(errMsg)
					.setPositiveButton(android.R.string.ok, null)
					.setIconAttribute(android.R.attr.alertDialogIcon);
				enqueueTaskToMainThread(() -> { db.show(); });
			}
		}
		else {
			try {
				InetSocketAddress destAddr = Utils.makeAddressFromIpPort(destIp, destPort);
				String key = makeProxyMapKey(inIfIp, outIfIp, destAddr);
				Proxy toRemove = proxyMap.remove(key);
				if (toRemove != null)
					toRemove.close();
			}
			catch (Exception ignored) {}
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

	public static String makeProxyMapKey(InterfaceIp inIfIp, InterfaceIp outIfIp, InetSocketAddress destAddr) {
		return inIfIp.toString() + "_" + outIfIp.toString() + "_" + destAddr.toString();
	}

	public String maybeCreateProxy(
		InterfaceIp inIfIp,
		InterfaceIp outIfIp,
		String destIp,
		String destPort,
		TextView inPortView,
		TextView outPortView
	) {
		InetSocketAddress destAddr;
		try {
			destAddr = Utils.makeAddressFromIpPort(destIp, destPort);
		}
		catch (Exception ex) {
			return Utils.getExceptionAsString(ex);
		}

		String key = makeProxyMapKey(inIfIp, outIfIp, destAddr);
		if (proxyMap.containsKey(key))
			return "";

		InetSocketAddress incomingAddr = new InetSocketAddress(inIfIp.addr, 0);
		InetSocketAddress outgoingAddr = new InetSocketAddress(outIfIp.addr, 0);

		AsynchronousServerSocketChannel server;
		try {
			server = AsynchronousServerSocketChannel.open().bind(incomingAddr);
			Proxy proxy = new Proxy(this, incomingAddr, outgoingAddr, destAddr);
			int[] serverClientPorts = proxy.acceptFirstServerRequest(server);
			proxyMap.put(key, proxy);
			inPortView.setText("" + serverClientPorts[0]);
			outPortView.setText("" + serverClientPorts[1]);
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

	public void onServerSetupFailure(Throwable ex, String kind) {
		((TextView)findViewById(R.id.mainTextView)).setText("Setup exception from " + kind + ": " + ex.toString());
	}

	public void onServerConnectionFailure(Throwable ex, String kind) {
		((TextView)findViewById(R.id.mainTextView)).setText("Connection exception from " + kind + ": " + ex.toString());
	}

	public void enqueueTaskToMainThread(Runnable runnable) {
		taskRunner.post(runnable);
	}
}

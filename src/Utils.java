package com.jackbendtsen.aserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Enumeration;

public class Utils {
	public static String join(ArrayList<String> array, String between) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String s : array) {
			if (!first) {
				sb.append(between);
			}
			sb.append(s);
			first = false;
		}
		return sb.toString();
	}

	public static void getAllIpAddresses(ArrayList<String> output) {
		try {
			Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {
				NetworkInterface iface = ifaces.nextElement();
				Enumeration<InetAddress> addrs = iface.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = addrs.nextElement();
					output.add(iface.toString() + " | " + addr.toString());
				}
			}
		}
		catch (Exception ex) {
			output.add("Exception: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
		}
	}

	public static InetSocketAddress parseAddress(String addrStr) throws Exception {
		int lastColon = addrStr.lastIndexOf(":");
		int portNum = 0;
		if (lastColon >= 0) {
			try {
				portNum = Integer.parseInt(addrStr.substring(lastColon + 1));
			}
			catch (Exception ignored) {}
		}
		if (portNum <= 0) {
			throw new Exception("The address \"" + addrStr + "\" did not contain a port number");
		}
		return new InetSocketAddress(addrStr.substring(0, lastColon), portNum);
	}

	public static ByteVector sendAndAwaitResponse(InetSocketAddress srcAddr, InetSocketAddress destAddr, byte[] requestData) throws IOException {
		Socket conn = new Socket();
		try {
			conn.bind(srcAddr);
			conn.connect(destAddr);

			if (requestData != null) {
				OutputStream os = conn.getOutputStream();
				os.write(requestData, 0, requestData.length);
			}

			InputStream is = conn.getInputStream();
			ByteVector responseBv = new ByteVector();
			responseBv.readAll(is);

			return responseBv;
		}
		finally {
			conn.close();
		}
	}
}


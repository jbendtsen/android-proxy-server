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

	public static Exception getAllIpAddresses(ArrayList<InterfaceIp> output) {
		try {
			Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {
				NetworkInterface iface = ifaces.nextElement();
				Enumeration<InetAddress> addrs = iface.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = addrs.nextElement();
					output.add(new InterfaceIp(iface, addr));
				}
			}
		}
		catch (Exception ex) {
			return ex;
		}
		return null;
	}

	public static String getExceptionAsString(Throwable t) {
		if (t == null)
			return "";
		String name = t.getClass().getSimpleName();
		String msg = t.getMessage();
		if (msg == null || msg.isEmpty())
			msg = "No exception message provided";
		return name + ": " + msg;
	}

	public static InetSocketAddress makeAddressFromIpPort(String ipStr, String portStr) throws Exception {
		int portNum = Integer.parseInt(portStr);
		return new InetSocketAddress(ipStr, portNum);
	}
}


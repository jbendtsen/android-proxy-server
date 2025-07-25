package com.jackbendtsen.aserver;

import java.net.NetworkInterface;
import java.net.InetAddress;

public class InterfaceIp {
	public NetworkInterface iface;
	public InetAddress addr;

	public InterfaceIp(NetworkInterface iface, InetAddress addr) {
		this.iface = iface;
		this.addr = addr;
	}

	@Override
	public String toString() {
		return iface.toString() + " | " + addr.toString();
	}
}

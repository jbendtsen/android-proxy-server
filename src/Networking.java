package com.jackbendtsen.aserver;

import android.os.Handler;
import java.nio.channels.*;

public class Networking {
	final Handler taskRunner;

	

	

	public String maybeCreateProxy(String incomingIp, String outgoingIp, String destIp, String destPort) {
		// TODO: make a string key from the inputs, lookup combination in hashmap to see if its already created
		// TODO: if so, return immediately with ""
		// TODO: validate inputs and return error message if not valid
		// TODO: if successful, create a new ProxyServer, start it and add it to the map
		AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open().bind(incomingAddr);
		Proxy proxy = new Proxy();
		proxy.acceptFirstServerRequest(server);
		return "";
	}
}

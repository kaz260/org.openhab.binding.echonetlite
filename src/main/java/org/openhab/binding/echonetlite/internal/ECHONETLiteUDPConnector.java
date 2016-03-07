/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.echonetlite.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;


/**
 * Connector for UDP communication.
 *
 * @since 1.8.0
 * @author aklevy
 */
class ECHONETLiteUDPConnector {


	/** debug and debug, always debug */
	private static final Logger logger = LoggerFactory.getLogger(ECHONETLiteBinding.class);


	/** Buffer for incoming UDP packages. */
	private static final int MAX_PACKET_SIZE = 512;

	/** Socket for receiving UDP packages. */
	private DatagramSocket socket = null;

	/** Time in second for waiting the reply from ECHONETLite devices. */
	private int time = 1;

	/** The port this connector is listening to. */
	private int receivePort = 3610;

	/** The port this connector is sending to. */
	private int sendPort = 3610;

	/**
	 * Create a new connector to an ECHONET Lite device via the given host and UDP ports.
	 *
	 * @param udpReceivePort The UDP port to listen for packages.
	 * @param udpSendPort The UDP port to send packages.
	 */

	ECHONETLiteUDPConnector(int udpReceivePort, int udpSendPort) {
		if (udpReceivePort <= 0)
			this.receivePort = 3610;
		else
			this.receivePort = udpReceivePort;

		if (udpSendPort <= 0)
			this.sendPort = 3610;
		else
			this.sendPort = udpSendPort;
	}

	/**
	 * Initialize socket connection to the UDP receive port.
	 *
	 * @throws SocketException
	 */
	public void connect() throws SocketException {
		if (socket == null) {
			socket = new DatagramSocket(receivePort);
			socket.setSoTimeout(time * 1000 * 2);
			// logger.debug("created new socket to connect");
		}
	}

	/**
	 * Close the socket connection.
	 */
	public void disconnect() {
		if (socket != null) {
			socket.close();
			socket = null;
		}
	}

	/**
	 * Convert bytes array to hex string (necessary for the reception of data)
	 */
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes, int length) {
		char[] hexChars = new char[length * 2];
		for (int j = 0; j < length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/**
	 * This is a blocking call for receiving data from the specific UDP port.
	 *
	 * @return The received data.
	 * @throws Exception If an exception occurred.
	 */
	String receiveDatagram(String host) throws Exception {
		String dataReceived = "";
		try {
			if (socket == null) {
				socket = new DatagramSocket(receivePort);
				socket.setSoTimeout(time * 1000 * 2);
			}
			final InetAddress ipAddress = InetAddress.getByName(host);

			// Creates a packet
			DatagramPacket packet =
				new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE, ipAddress,
					receivePort);

			// Receives an ECHONETLite packet (blocking)
			// nopChecks the ECHONETLite header "1081"

			socket.receive(packet);

			if (packet.getLength() == 0) {
				dataReceived = "nothing";
			} else {
				dataReceived = bytesToHex(packet.getData(), packet.getLength());
			}
			// logger.debug(dataReceived);


			logger.debug("Message received: " + dataReceived);// +" from " +
																												// Integer.toString(packet.getPort()));
			return dataReceived;
		} catch (SocketTimeoutException e) {
			dataReceived = "nothing";
			return dataReceived;
		} catch (SocketException e) {
			logger.debug("socketexception");
			return dataReceived;
			// throw new Exception(e);
		} catch (IOException e) {
			logger.debug("io exception");
			throw new Exception(e);
		}

	}

	/**
	 * Send data to the specified address via the specified UDP port.
	 *
	 * @param data The data to send.
	 * @throws Exception If an exception occurred.
	 */
	void sendDatagram(byte[] data, String host) throws Exception {
		if (data == null || data.length == 0)
			throw new IllegalArgumentException("data must not be null or empty");
		try {
			if (socket == null) {
				socket = new DatagramSocket(receivePort);
				socket.setSoTimeout(time * 1000 * 2);
			}

			final InetAddress ipAddress = InetAddress.getByName(host);
			final DatagramPacket packet =
				new DatagramPacket(data, data.length, ipAddress, sendPort);

			// logger.debug("port: "+packet.getPort() + " data "+packet.getData()+ " size " +
			// packet.getData().length+ " length "+packet.getLength()+" address " + packet.getAddress());

			socket.send(packet);
		} catch (SocketException e) {
			throw new Exception(e);
		} catch (UnknownHostException e) {
			throw new Exception("Could not resolve host: " + host, e);
		} catch (IOException e) {
			throw new Exception(e);
		}
	}

	public String toString() {
		return "ECHONETLiteUDPConnector: send UDP port is " + sendPort
			+ ", receive UDP port is " + receivePort;
	}

}

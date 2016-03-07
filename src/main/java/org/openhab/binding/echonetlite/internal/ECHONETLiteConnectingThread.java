/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.echonetlite.internal;

import org.apache.commons.lang3.ArrayUtils;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.events.EventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

/**
 * @author aklevy, Kazuhiro Matsuda
 * @since 1.8.0
 */


/**
 * This thread sends command to ECHONETLite devices and checks whether the reply sent by the device
 * is a error message or not
 *
 */
public class ECHONETLiteConnectingThread extends Thread {
	private static final Logger logger = LoggerFactory.getLogger(ECHONETLiteBinding.class);


	/**
	 * The UDP connector
	 */
	private ECHONETLiteUDPConnector udpConnector;

	/**
	 * Host to which the UDP message will be sent
	 */
	private String host;

	private String itemName;

	/**
	 * Data to be sent
	 */
	private byte[] data;

	private org.openhab.core.types.State state = null;

	private EventPublisher eventPublisher = null;

	public void setEventPublisher(EventPublisher pEventPublisher) {
		this.eventPublisher = pEventPublisher;
	}

	public void unsetEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = null;
	}

	public ECHONETLiteConnectingThread(String in, int port, byte[] dt, String hst) {
		udpConnector = new ECHONETLiteUDPConnector(port, port);
		itemName = in;
		data = dt;
		host = hst;
	}

	/**
	 * Sends commands to ECHONETLite devices And receives the reply from ECHONETLite device
	 */
	public void run() {
		// Sends the command to devices
		sendMessage();

		// Receives the reply from devices
		final char[] response = receiveMessage();
		state = StringType.valueOf(String.valueOf(Integer.parseInt(String.valueOf(response), 16)));
		eventPublisher.postUpdate(itemName, state);

		udpConnector.disconnect();
	}


	/**
	 * Sends ECHONETLite message to ECHONETLite devices
	 */
	public void sendMessage() {
		try {
			udpConnector.sendDatagram(data, host);

			logger.debug("The following command " + data
				+ " was successfully sent to ECHONETLite device with the following IP Address "
				+ InetAddress.getByName(host).toString());
		} catch (Exception e) {
			logger.error("could not send command to the ECHONETLite device");
		}
	}


	/**
	 * Receives ECHONETLite reply message Checks if it is an error message
	 */
	//public void receiveMessage() {
	char[] receiveMessage() {
		logger.debug("checking the response from the ECHONETLite device");
		char[] res = {'0', '0', '0', '0'};

		try {
			String response = udpConnector.receiveDatagram(host);
			if (response.equals("nothing")) {
				logger.debug("Nothing was sent back from ECHONETLite devices");
			} else {
				char[] code = new char[response.length()];
				response.getChars(0, response.length(), code, 0);

				if (response.length() > 21) {
					if (response.length() > 22 && code[20] == '5') {
						logger
							.info("An error message (ESV="
								+ code[20]
								+ code[21]
								+ ") was sent back by the ECHONETLite device, the device's state was not updated: please check the message sent previously");
					} else if (code[20] == '7') {
						if(code[21] == '1') {
							logger.info("SetC was successfully executed by the ECHONETLite device");
							res = ArrayUtils.subarray(code, 26, 28);
						} else if(code[21] == '2') {
							logger.info("Get was successfully executed by the ECHONETLite device");
							int resSize = Integer.parseInt(String.valueOf(ArrayUtils.subarray(code,26,28)), 16);
							int resFrom = 28;
							int resTo = 28 + resSize * 2;
							res = ArrayUtils.subarray(code, resFrom, resTo);
						} else {
							logger.info("receive unknown response");
						}
					}
				}
			}
			return res;
		} catch (Exception e) {
			logger.error(e.toString());
			logger.error("error when receiving message from ECHONETLite devices");
			return res;
		}
	}
}

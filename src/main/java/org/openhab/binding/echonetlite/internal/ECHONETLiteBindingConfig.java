/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.echonetlite.internal;

import org.openhab.core.binding.BindingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This represents the configuration of a openHAB item that is binded to a ECHONETLite devices.
 * These devices can have the following information:
 *
 * <ul>
 * <li></li>
 * <li>The binding type of a ECHONETLite device</li>
 * <ul>
 * <li>device Id</li>
 * <li>command to be executed</li>
 * <li>message to be sent to the ECHONETLite device</li>
 * </ul>
 *
 * @author aklevy
 * @since 1.0
 */
public class ECHONETLiteBindingConfig implements BindingConfig {

	static final Logger logger = LoggerFactory.getLogger(ECHONETLiteBindingConfig.class);

	/**
	 * The device id
	 */
	private final String deviceId;

	private final String epc;

	/**
	 * The command and the message associated
	 */
	private Map<String, String> commandMessageMap = new HashMap<String, String>();



	/**
	 * Constructor of the ECHONETLiteBindingConfig.
	 *
	 * @param id The device id as specifed in the .items file
	 * @param cmd The command to be executed
	 * @param msg The ECHONETLite message to be sent to the device
	 *
	 */
	public ECHONETLiteBindingConfig(String id, String epc) {
		this.deviceId = id;
		this.epc = epc;
	}

	/**
	 * Adds the command and message in the map of the device configuration
	 */

	public void addCommandMessage(String command, String message) {
		this.commandMessageMap.put(command, message);
	}

	/**
	 * @return The device id that has been declared in the binding configuration.
	 */
	public String getDeviceId() {
		return deviceId;
	}

	public String getEpc() {
		return this.epc;
	}

	/**
	 * @return The message corresponding to the command
	 */
	public String getMessage(String command) {
		return commandMessageMap.get(command);
	}


	public String toString() {
		return "ECHONETLiteBindingConfig: deviceId " + deviceId;
	}
}

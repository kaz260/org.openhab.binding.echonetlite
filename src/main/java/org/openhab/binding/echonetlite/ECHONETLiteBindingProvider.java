/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.echonetlite;

import java.util.List;

import org.openhab.core.binding.BindingProvider;
import org.openhab.core.items.Item;
import org.openhab.core.transform.TransformationException;
import org.openhab.core.types.Command;
import org.openhab.binding.echonetlite.internal.ECHONETLiteBindingConfig;

/**
 * @author aklevy, kazuhiro Matsuda
 * @since 1.8.0
 */
public interface ECHONETLiteBindingProvider extends BindingProvider {
	/**
	 * Returns the command type to the given <code>itemName</code>.
	 *
	 * @param itemName The item for which to find the ECHONETLite message.
	 * @return the corresponding item config to the given <code>itemName</code> .
	 */
	//ECHONETLiteBindingConfig getItemConfig(String itemName);
	
	Class<? extends Item> getItemType(String itemName);

	/**
	 * Returns the device id for this binding provider.
	 *
	 * @param itemName The item for which to fina a device name.
	 * @return the device if to the given <code>itemName</code>.
	 */
	String getDeviceId(String itemName);

	String getDeviceId(String itemName, Command command);

	String getEpc(String itemName);

	String getEpc(String itemName, Command command);

	String getEdt(String itemName);

	String getEdt(String itemName, Command command);

	long getRefreshInterval(String itemName);

	List<String> getInBindingItemNames();

	String getValue(String itemName, Command command);

	String doTransformation(String itemNamem, String value) throws TransformationException;

	String getOutputStateDeviceId(String itemName);

	String getInputStateDeviceId(String itemName);

	String getOutputStateEpc(String itemName);
	
	String getInputStateEpc(String itemName);
}

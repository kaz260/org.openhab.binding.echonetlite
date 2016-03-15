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
import org.apache.commons.lang3.StringUtils;
import org.openhab.binding.echonetlite.ECHONETLiteBindingProvider;
import org.openhab.binding.echonetlite.data.DeviceInfo;
import org.openhab.binding.echonetlite.util.CommonUtils;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.TypeParser;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;


/**
 * Implement this class if you are going create an actively polling service like querying a
 * Website/Device.
 *
 * @author aklevy, Kazuhiro Matsuda
 * @since 1.8.0
 */
public class ECHONETLiteBinding extends AbstractActiveBinding<ECHONETLiteBindingProvider>
	implements ManagedService {

	private static final Logger logger = LoggerFactory.getLogger(ECHONETLiteBinding.class);

	/**
	 * the refresh interval which is used to poll values from the ECHONETLite server (optional,
	 * defaults to 60000ms)
	 */

	private long refreshInterval = TimeUnit.SECONDS.toMillis(60);

	private Map<String, Long> lastUpdateMap = new HashMap<String, Long>();

	private Map<String, DeviceInfo> deviceMap = new HashMap<>();

	private Map<String, State> itemValueMap = new HashMap<>();

	private final static Pattern CONFIG_PATTERN = Pattern.compile(
		"^(.+?)\\.(host|port|ehd|edata_head)$", Pattern.CASE_INSENSITIVE);

	@Override
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		if (config == null || config.isEmpty())
			throw new IllegalArgumentException("config is empty");
		else {
			for (final Enumeration<String> e = config.keys(); e.hasMoreElements();) {
				final String key = e.nextElement();
				final String value = StringUtils.trim((String) config.get(key));

				// skip empty values
				if (StringUtils.isBlank(value)) {
					continue;
				}
				final Matcher matcher = CONFIG_PATTERN.matcher(key);
				if (matcher.matches()) {
					final String device = matcher.group(1);
					final String property = matcher.group(2);
					if (!deviceMap.containsKey(device)) {
						deviceMap.put(device, new DeviceInfo());
					}
					DeviceInfo deviceInfo = deviceMap.get(device);

					// dispatch individual properties
					if (("host").equalsIgnoreCase(property)) {
						deviceInfo.setHost(value);
					} else if (("port").equalsIgnoreCase(property)) {
						deviceInfo.setPort(Integer.parseInt(value));
					} else if (("ehd").equalsIgnoreCase(property)) {
						deviceInfo.setEhd(CommonUtils.hexStringToByteArray(value));
					} else if (("edata_head").equalsIgnoreCase(property)) {
						deviceInfo.setEdataHead(CommonUtils.hexStringToByteArray(value));
					}
				}
			}
		}
	}


	/**
	 * Called by the SCR to activate the component with its configuration read from CAS
	 *
	 * @param bundleContext BundleContext of the Bundle that defines this component
	 * @param configuration Configuration properties for this component obtained from the ConfigAdmin
	 *        service
	 */
	@Override
	public void activate() {
		super.activate();
		setProperlyConfigured(true);
	}

	public void activate(final BundleContext bundleContext,
		final Map<String, Object> configuration) {

		// to override the default refresh interval one has to add a
		// parameter to openhab.cfg like <bindingName>:refresh=<intervalInMs>
		String refreshIntervalString = (String) configuration.get("refresh");
		if (StringUtils.isNotBlank(refreshIntervalString)) {
			refreshInterval = Long.parseLong(refreshIntervalString);
		}

		// read further config parameters here ...

		setProperlyConfigured(true);
	}

	/**
	 * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin
	 * service.
	 *
	 * @param configuration Updated configuration properties
	 */
	public void modified(final Map<String, Object> configuration) {
		// update the internal configuration accordingly
	}

	/**
	 * Called by the SCR to deactivate the component when either the configuration is removed or
	 * mandatory references are no longer satisfied or the component has simply been stopped.
	 *
	 * @param reason Reason code for the deactivation:<br>
	 *        <ul>
	 *        <li>0 ? Unspecified
	 *        <li>1 ? The component was disabled
	 *        <li>2 ? A reference became unsatisfied
	 *        <li>3 ? A configuration was changed
	 *        <li>4 ? A configuration was deleted
	 *        <li>5 ? The component was disposed
	 *        <li>6 ? The bundle was stopped
	 *        </ul>
	 */
	public void deactivate(final int reason) {

	}


	/**
	 * @{inheritDoc
	 */
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	protected String getName() {
		return "ECHONETLite Refresh Service";
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	protected void execute() {
		for (ECHONETLiteBindingProvider provider : providers) {
			for (String itemName : provider.getInBindingItemNames()) {
				String deviceId = provider.getDeviceId(itemName);
				String epc = provider.getEpc(itemName);
				long refreshInterval = provider.getRefreshInterval(itemName);

				Long lastUpdateTimeStamp = lastUpdateMap.get(itemName);
				if (lastUpdateTimeStamp == null) {
					lastUpdateTimeStamp = 0L;
				}

				long age = System.currentTimeMillis() - lastUpdateTimeStamp;
				Boolean needsUpdate = (age >= refreshInterval) && (refreshInterval != 0);

				if (needsUpdate) {
					String response = null;

					DeviceInfo deviceInfo = deviceMap.get(deviceId);
					if (deviceInfo == null) return;

					byte[] edtBytes = CommonUtils.hexStringToByteArray("");
					byte[] dataByte = buildData(deviceInfo, epc, edtBytes);

					try {
						ECHONETLiteConnectingThread connector =
							new ECHONETLiteConnectingThread(itemName, deviceInfo.getPort(), dataByte,
								deviceInfo.getHost(), itemValueMap.get(itemName));

						connector.start();
                                        	connector.setEventPublisher(eventPublisher);

						connector.join();
					} catch (Exception e) {
						logger.error("error occured when update");
					}	

					lastUpdateMap.put(itemName, System.currentTimeMillis());
				}
			}
		}
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	protected void internalReceiveCommand(String itemName, Command command) {
		if (command != null) {
			logger.debug("internalReceiveCommand ({}:{})", itemName, command);

			for (ECHONETLiteBindingProvider provider : this.providers) {
				String deviceId = provider.getDeviceId(itemName, command);
				String epc = provider.getEpc(itemName, command);
				String edt = provider.getEdt(itemName, command);

				DeviceInfo deviceInfo = deviceMap.get(deviceId);
				if (deviceInfo == null) {
					return;
				}

				byte[] edtBytes;

				if (edt != "") {
					if(edt.length() < 2) {
						edt = "0" + edt;
					}
					edtBytes = CommonUtils.hexStringToByteArray(edt);
				} else {
					edtBytes = getData(command);
				}

				byte[] dataByte = buildData(deviceInfo, epc, edtBytes);

				try {
					ECHONETLiteConnectingThread connector =
						new ECHONETLiteConnectingThread(itemName, deviceInfo.getPort(), dataByte,
							deviceInfo.getHost(), itemValueMap.get(itemName));

					// Starts the connector thread
					connector.start();
					connector.setEventPublisher(eventPublisher);

					// Wait for the thread to finish
					connector.join();
				} catch (Exception e) {
					logger.error("error occured when sending ECHONETLite message");
				}
			}
		}
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	protected void internalReceiveUpdate(String itemName, State newState) {
		logger.debug("internalReceiveUpdate ({}:{})", itemName, newState);
		//if (itemValueMap.containsKey(itemName) && itemValueMap.get(itemName).equals(newState)) {
		//	return;
		//}

		ECHONETLiteBindingProvider providerCmd = null;

		for (ECHONETLiteBindingProvider provider : this.providers) {
			String deviceId;
			byte[] edtBytes;
			String epc;

			if (provider.getOutputStateDeviceId(itemName) != null) {
				deviceId = provider.getOutputStateDeviceId(itemName);
				epc = provider.getOutputStateEpc(itemName);
				edtBytes = getData(newState);				
			} else if (provider.getInputStateDeviceId(itemName) != null){
				deviceId = provider.getInputStateDeviceId(itemName);
				epc = provider.getInputStateEpc(itemName);
				edtBytes = CommonUtils.hexStringToByteArray("");
			} else {
				return;
			}

			DeviceInfo deviceInfo = deviceMap.get(deviceId);
			if (deviceInfo == null) {
				return;
			}

			try {
				byte[] dataByte = buildData(deviceInfo, epc, edtBytes);
				ECHONETLiteConnectingThread connector =
					new ECHONETLiteConnectingThread(itemName, deviceInfo.getPort(), dataByte, deviceInfo.getHost(), itemValueMap.get(itemName));
				// Starts the connector thread
				connector.start();
				itemValueMap.put(itemName, newState);
				connector.setEventPublisher(eventPublisher);

				// Wait for the thread to finish
				connector.join();
			} catch (Exception e) {
				logger.error("error occured when receiving internal command");
			}
		}
	}

	private byte[] buildData(DeviceInfo deviceInfo, String epc, byte[] edtBytes) {
		// <EHD1><EHD2>
		byte[] dataByte = deviceInfo.getEhd();

		// <TID>
		byte[] tid = CommonUtils.getNextTid();
		dataByte = ArrayUtils.addAll(dataByte, tid);

		// <SEOJ><DEOJ><ESV><OPC><EPC1>
		dataByte = ArrayUtils.addAll(dataByte, deviceInfo.getEdataHead());

		// <EPC>
		dataByte = ArrayUtils.addAll(dataByte, CommonUtils.hexStringToByteArray(epc));

		// <PDC1>
		dataByte = ArrayUtils.add(dataByte, (byte) edtBytes.length);
		// <EDT1>
		dataByte = ArrayUtils.addAll(dataByte, edtBytes);
		return dataByte;
	}

	private byte[] getData(State newState) {
		byte[] data = null;
		if (newState instanceof DecimalType) {
			data = CommonUtils.long2byte(((DecimalType) newState).longValue());
		} else {
			if (newState != null) {
				data = CommonUtils.hexStringToByteArray(newState.toString());
			}
			if (data == null || data.length == 0) {
				data = new byte[] {0};
			}
		}
		return data;
	}

	private byte[] getData(Command command) {
		byte[] data = null;
		if (command instanceof DecimalType) {
			data = CommonUtils.long2byte(((DecimalType) command).longValue());
		} else {
			if (command != null) {
				data = CommonUtils.hexStringToByteArray(command.toString());
			}
			if (data == null || data.length == 0) {
				data = new byte[] {0};
			}
		}
		return data;
	}
}

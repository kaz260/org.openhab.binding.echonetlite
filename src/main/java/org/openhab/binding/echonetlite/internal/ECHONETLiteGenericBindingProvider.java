/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.echonetlite.internal;

import org.apache.commons.lang3.StringUtils;
import org.openhab.binding.echonetlite.ECHONETLiteBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.StringType;
import org.openhab.core.transform.TransformationException;
import org.openhab.core.transform.TransformationHelper;
import org.openhab.core.transform.TransformationService;
import org.openhab.core.types.Command;
import org.openhab.core.types.TypeParser;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is responsible for parsing the binding configuration.
 * <p>
 * Example configuration:
 * </p>
 *
 *
 *
 * Example items:
 *
 * <pre>
 * Switch AirconditionerSwitch “AirConditionerSwitch” { echonetlite="aircon1:ON:01300105FF016101800130" }
 * </pre>
 *
 *
 * @author aklevy, Kazuhiro Matsuda
 * @since 1.8.0
 */

public class ECHONETLiteGenericBindingProvider extends AbstractGenericBindingProvider
	implements ECHONETLiteBindingProvider {

	static final Logger logger = LoggerFactory
		.getLogger(ECHONETLiteGenericBindingProvider.class);

	/*
	 * Artificial command for the echonetlite-in configuration
	 */
	protected static final Command IN_BINDING_KEY = StringType.valueOf("IN_BINDING");

	/*
	 * Artificial command for the echonetlite-out configuration
	 */
	protected static final Command OUT_BINDING_KEY = StringType.valueOf("OUT_BINDING");

	protected static final Command OUT_BINDING_FOR_UPDATE_KEY = StringType.valueOf("OUT_BINDING_FOR_UPDATE_KEY");

	protected static final Command IN_BINDING_FOR_UPDATE_KEY = StringType.valueOf("IN_BINDING_FOR_UPDATE_KEY");

	protected static final Command CHANGED_COMMAND_KEY = StringType.valueOf("CHANGED");

	protected static final Command WILDCARD_COMMAND_KEY = StringType.valueOf("*");

	private long refreshInterval = 60000;

	/** {@link pattern} which mathes a binding configuration part */
	private static final Pattern BASE_CONFIG_PATTERN = Pattern
		.compile("([<|>|\\*]\\[.*?\\])*");

	/** {@link Pattern* which matches an In-Binding */
	private static final Pattern IN_BINDING_PATTERN = Pattern
		.compile("<\\[([0-9_a-zA-Z]+):([0-9_a-zA-Z]+):([0-9_a-zA-Z]+)\\]");

	private static final Pattern IN_BINDING_PATTERN_REFRESH = Pattern
		.compile("<\\[([0-9_a-zA-Z]+):([0-9_a-zA-Z]+):([0-9_a-zA-Z]+):([0-9]+)\\]");

	private static final Pattern IN_BINDING_PATTERN_WO_COMMAND = Pattern
		.compile("<\\[([0-9_a-zA-Z]+):([0-9_a-zA-Z]+)\\]");

        private static final Pattern IN_BINDING_PATTERN_WO_COMMAND_REFRESH = Pattern
                .compile("<\\[([0-9_a-zA-Z]+):([0-9_a-zA-Z]+):([0-9]+)\\]");

	private static final Pattern OUT_BINDING_PATTERN = Pattern
		.compile(">\\[([0-9_a-zA-Z]+):([0-9_a-zA-Z]+):([0-9_a-zA-Z]+):([0-9_a-zA-Z]+)\\]");

	private static final Pattern OUT_BINDING_PATTERN_WO_COMMAND = Pattern
		.compile(">\\[([0-9_a-zA-Z]+):([0-9_a-zA-Z]+)\\]");

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "echonetlite";
	}

	/**
	 * {@inheritDoc
	 */
	@Override
	public void validateItemType(Item item, String bindingConfig)
		throws BindingConfigParseException {
		if (!(item instanceof StringItem || item instanceof NumberItem || item instanceof SwitchItem)) {
			throw new BindingConfigParseException(
				"Item '"
					+ item.getName()
					+ "' is of type '"
					+ item.getClass().getSimpleName()
					+ "', only StringItems, NumberItems and SwitchItems are allowed - please check your *.items configuration");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig)
		throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);

		if (bindingConfig != null) {
			ECHONETLiteBindingConfig newConfig = new ECHONETLiteBindingConfig();
			Matcher matcher = BASE_CONFIG_PATTERN.matcher(bindingConfig);

			if (!matcher.matches()) {
				throw new BindingConfigParseException("bindingConfig '" + bindingConfig
					+ "' doesn't contain a valid binding configuration");
			}
			matcher.reset();

			while(matcher.find()) {
				String bindingConfigPart = matcher.group(1);
				if(StringUtils.isNotBlank(bindingConfigPart)) {
					parseBindingConfig(newConfig, item, bindingConfigPart);
				}
			}

			addBindingConfig(item, newConfig);
		} else {
			logger.warn("bindingConfig is NULL (item=" + item
				+ ") -> processing bindingConfig aborted!");
		}
	}

	private void parseBindingConfig(ECHONETLiteBindingConfig config, Item item, 
		String bindingConfig) throws BindingConfigParseException {

		config.itemType = item.getClass();

		if (bindingConfig != null) {
			Matcher inMatcher = IN_BINDING_PATTERN.matcher(bindingConfig);
			if (!inMatcher.matches()) {
				inMatcher = IN_BINDING_PATTERN_REFRESH.matcher(bindingConfig);
			}

			if (inMatcher.matches()) {
				ECHONETLiteBindingConfigElement newElement = new ECHONETLiteBindingConfigElement();
				newElement.deviceId = inMatcher.group(1).toString();
				newElement.epc = inMatcher.group(2).toString();
				if (inMatcher.groupCount() == 3) newElement.refreshInterval = Integer.valueOf(inMatcher.group(3)).intValue(); 
				logger.debug("item:{} newElement:{}", item, newElement);
				config.put(IN_BINDING_KEY, newElement);
			} else {
				inMatcher = IN_BINDING_PATTERN_WO_COMMAND.matcher(bindingConfig);
				if(inMatcher.matches()) {
					ECHONETLiteBindingConfigElement newElement = new ECHONETLiteBindingConfigElement();
					newElement.deviceId = inMatcher.group(1).toString();
					newElement.epc = inMatcher.group(2).toString();
					newElement.refreshInterval = refreshInterval;
					logger.debug("item:{} in_binding_update_newElement:{}", item, newElement);
					config.put(IN_BINDING_FOR_UPDATE_KEY, newElement);
				}
			}

			
			Matcher outMatcher = OUT_BINDING_PATTERN.matcher(bindingConfig);
			if (outMatcher.matches()) {
				ECHONETLiteBindingConfigElement newElement = new ECHONETLiteBindingConfigElement();
				Command command = createCommandFromString(item, outMatcher.group(1));
				newElement.deviceId = outMatcher.group(2).toString();
				newElement.epc = outMatcher.group(3).toString();
				newElement.edt = outMatcher.group(4).toString();
				logger.debug("item:{} newElement:{}", item, newElement);
				config.put(command, newElement);
			} else {
				outMatcher = OUT_BINDING_PATTERN_WO_COMMAND.matcher(bindingConfig);
				if(outMatcher.matches()) {
					ECHONETLiteBindingConfigElement newElement = new ECHONETLiteBindingConfigElement();
					newElement.deviceId = outMatcher.group(1).toString();
					newElement.epc = outMatcher.group(2).toString();
					logger.debug("item:{} out_binding_update_newElement:{}", item, newElement);
					config.put(OUT_BINDING_FOR_UPDATE_KEY, newElement);
				}
			}

			if (!inMatcher.matches() && !outMatcher.matches()) {
				throw new BindingConfigParseException(getBindingType()
					+ "binding configuration must consist of two/three [config=" + inMatcher
					+ "] of four parts [config=" + outMatcher + "]");
			}
		} else {
			return;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public List<String> getInBindingItemNames() {
		List<String> inBindings = new ArrayList<String>();
		for (String itemName : bindingConfigs.keySet()) {
			ECHONETLiteBindingConfig ECHONETLiteConfig = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
			if (ECHONETLiteConfig.containsKey(IN_BINDING_KEY)) {
				inBindings.add(itemName);
			}
		}
		return inBindings;
	}

	/**
	 * {@inheritDoc}
	 */
	public List<String> getOutBindingItemNames() {
		List<String> outBindings = new ArrayList<String>();
		for (String itemName : bindingConfigs.keySet()) {
			ECHONETLiteBindingConfig ECHONETLiteConfig = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
			if (ECHONETLiteConfig.containsKey(OUT_BINDING_KEY)) {
				outBindings.add(itemName);
			}
		}
		return outBindings;
	}

	/**
	 * {@inheritDoc
	 */
	@Override
	public Class<? extends Item> getItemType(String itemName) {
		ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
			return config != null ? config.itemType : null;
	}

	static class ECHONETLiteBindingConfig extends HashMap<Command, ECHONETLiteBindingConfigElement>
		implements BindingConfig {

		private static final long serialVersionUID = 4697146075427676117L;
		Class<? extends Item> itemType;
	}

	static class ECHONETLiteBindingConfigElement implements BindingConfig {
		public String command;
		public String deviceId;
		public String epc;
		public String edt;
		public String value;
		public long refreshInterval;
		public TransformationService transformationService;
		public String transformationName;
		public String transformationParam;

		@Override
		public String toString() {
			return "ECHONETLiteConfigElement [deviceId=" + deviceId 
				+ ", epc=" + epc
				+ ", edt=" + edt
				+ ", refreshInterval=" + refreshInterval
				+ "]";
		}

		public boolean setTransformationRule(String rule) {
			int pos = rule.indexOf('(');
			if (pos == -1) return false;

			// split the transformation rule into name and param.
			transformationName = rule.substring(0, pos);
			transformationParam = rule.substring(pos + 1, rule.length() - 1);

			BundleContext context = ECHONETLiteActivator.getContext();

			//get the transfpormation service
			transformationService =
				TransformationHelper.getTransformationService(context, transformationName);
			if(transformationService == null) {
				logger.debug("No transfromation service found for {}", transformationName);
				return false;
			}

			return true;
		}

		public String doTransformation(String value) throws TransformationException {
			if (transformationService == null)
				return value;

			return transformationService.transform(transformationParam, value);
		}
	}

	@Override
	public String doTransformation(String itemName, String value)
		throws TransformationException {
		ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
		if (config == null)
			return value;
		if (config.get(IN_BINDING_KEY) == null)
			return value;
		return config.get(IN_BINDING_KEY).doTransformation(value);
	}

	@Override
	public String getDeviceId(String itemName) {
		ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
		return config != null ? config.get(IN_BINDING_KEY).deviceId : new String("");
	}

        @Override
        public String getDeviceId(String itemName, Command command) {
                ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
                //return config != null ? config.get(command).deviceId : new String("");
		if (config != null) {
			if (config.get(command) != null) {
				return config.get(command).deviceId;
			} else {
				return config.get(OUT_BINDING_FOR_UPDATE_KEY).deviceId;
			}
		} else {
			return new String("");
		}
        }

        @Override
        public String getEpc(String itemName) {
                ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
                return config != null ? config.get(IN_BINDING_KEY).epc : new String("");
        }

        @Override
        public String getEpc(String itemName, Command command) {
                ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
                //return config != null ? config.get(command).epc : new String("");
		if (config != null) {
			if (config.get(command) != null) {
				return config.get(command).epc;
			} else {
				return config.get(OUT_BINDING_FOR_UPDATE_KEY).epc;
			}
		} else {
			return new String("");
		}
        }

        @Override
        public String getEdt(String itemName) {
                ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
                return config != null ? config.get(IN_BINDING_KEY).edt : new String("");
        }

        @Override
        public String getEdt(String itemName, Command command) {
                ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
                //return config != null ? config.get(command).edt : new String("");
		if (config != null) {
			if (config.get(command) != null) {
				return config.get(command).edt;
			} else {
				return StringUtils.substringAfter(command.toString(), "value=");
			}
		} else {
			return new String("");
		}  
        }

        @Override
        public String getValue(String itemName, Command command) {
                ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
                //return config != null ? config.get(command).value : null;
		if (config != null) {
			if (config.get(command) != null) {
				return config.get(command).edt;
			} else {
				return StringUtils.substringAfter(command.toString(), "value=");
			}
		} else {
			return new String("");
		}
        }

        @Override
        public String getOutputStateDeviceId(String itemName) {
                ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
		ECHONETLiteBindingConfigElement outSetting = getOutSetting(config);
                return outSetting != null ? outSetting.deviceId : null;
        }

	@Override
	public String getInputStateDeviceId(String itemName) {
		ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
		ECHONETLiteBindingConfigElement inSetting = getInSetting(config);
		return inSetting != null ? inSetting.deviceId : null;
	}

        @Override
        public String getOutputStateEpc(String itemName) {
                ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
                ECHONETLiteBindingConfigElement outSetting = getOutSetting(config);
                return config != null ? outSetting.epc : new String("");
        }

	@Override
	public String getInputStateEpc(String itemName) {
		ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
		ECHONETLiteBindingConfigElement inSetting = getInSetting(config);
		return config != null ? inSetting.epc : new String("");
	}

	private ECHONETLiteBindingConfigElement getOutSetting(ECHONETLiteBindingConfig config) {
		ECHONETLiteBindingConfigElement outSetting = null;
		if (config != null) {
			outSetting = config.get(OUT_BINDING_FOR_UPDATE_KEY);
		}
		return outSetting;
	}

        private ECHONETLiteBindingConfigElement getInSetting(ECHONETLiteBindingConfig config) {
                ECHONETLiteBindingConfigElement inSetting = null;
                if (config != null) {
                        inSetting = config.get(IN_BINDING_FOR_UPDATE_KEY);
                }
                return inSetting;
        }



	public long getRefreshInterval(String itemName) {
		ECHONETLiteBindingConfig config = (ECHONETLiteBindingConfig) bindingConfigs.get(itemName);
		return config != null && config.get(IN_BINDING_KEY) != null ? config.get(IN_BINDING_KEY).refreshInterval : 0;
	}

	private Command createCommandFromString(Item item, String commandAsString) throws BindingConfigParseException {

		if (CHANGED_COMMAND_KEY.equals(commandAsString)) {
			return CHANGED_COMMAND_KEY;
		}
		else if (WILDCARD_COMMAND_KEY.equals(commandAsString)) {
			return WILDCARD_COMMAND_KEY;
		} else {
			Command command = TypeParser.parseCommand(
				item.getAcceptedCommandTypes(), commandAsString);

			if (command == null) {
				throw new BindingConfigParseException("could not create Command from '" + commandAsString + "' ");
			}
			return command;
		}
	}
}

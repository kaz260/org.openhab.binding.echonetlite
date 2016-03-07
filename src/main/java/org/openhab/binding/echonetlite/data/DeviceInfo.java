package org.openhab.binding.echonetlite.data;

/**
 * <p>
 * Device Information
 * </p>
 *
 * @author Kazuhiro Matsuda
 * @version 1.0
 */
public class DeviceInfo {
	private String host;
	private int port = 3610;
	private byte[] ehd = {0x10, (byte) 0x81};
	private byte[] edataHead;

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public byte[] getEhd() {
		return this.ehd;
	}

	public void setEhd(byte[] ehd) {
		this.ehd = ehd;
	}

	public byte[] getEdataHead() {
		return this.edataHead;
	}

	public void setEdataHead(byte[] edataHead) {
		this.edataHead = edataHead;
	}
}

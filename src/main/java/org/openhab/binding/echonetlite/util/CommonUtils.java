package org.openhab.binding.echonetlite.util;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Common utilities 
 * </p>
 *
 * @author Kazuhiro Matsuda
 * @version 1.0
 */
public class CommonUtils {
	private static int tid = 1;

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] =
				(byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(
					s.charAt(i + 1), 16));
		}
		return data;
	}


	public static byte[] long2byte(long value) {
		if (value == 0) {
			return new byte[] {0};
		}
		byte[] array = Longs.toByteArray(value);
		List<Byte> result = new ArrayList<>();
		boolean skip = true;
		for (byte b : array) {
			if (skip) {
				if (b == 0) {
					continue;
				} else {
					skip = false;
				}
			}

			result.add(b);
		}

		return Bytes.toArray(result);
	}

	public static byte[] getNextTid() {
		int newtime = tid++;
		return new byte[] {(byte) (newtime >> 8), (byte) newtime};
	}
}

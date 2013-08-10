package com.relayrides.pushy.apns.util;

/**
 * <p>A utility class for converting APNS device tokens between byte arrays and hexadecimal strings.</p>
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class TokenUtil {
	private static final String NON_HEX_CHARACTER_PATTERN = "[^0-9a-fA-F]";
	
	private TokenUtil() {}
	
	/**
	 * <p>Converts a string of hexadecimal characters into a byte array. All non-hexadecimal characters (i.e. 0-9 and
	 * A-F) are ignored in the conversion. Note that this means that
	 * {@code TokenUtil.tokenBytesToString(TokenUtil.tokenStringToByteArray(tokenString))} may be different from
	 * {@code tokenString}.</p>
	 * 
	 * <p>As an example, a valid token string may look something like this: {@code <740f4707 61bb78ad>}. This method
	 * would return a byte array with the following values: {@code [0x74 0x0f 0x47 0x07 0x61 0xbb 0x78 0xad]}.</p>
	 * 
	 * @param tokenString a string of hexadecimal characters to interpret as a byte array
	 * 
	 * @return a byte array containing the values represented in the token string
	 */
	public static byte[] tokenStringToByteArray(final String tokenString) {
		
		final String strippedTokenString = tokenString.replaceAll(NON_HEX_CHARACTER_PATTERN, "");
		
		final byte[] tokenBytes = new byte[strippedTokenString.length() / 2];

		for (int i = 0; i < strippedTokenString.length(); i += 2) {
			tokenBytes[i / 2] = (byte) (Integer.parseInt(strippedTokenString.substring(i, i + 2), 16));
		}

		return tokenBytes;
	}
	
	/**
	 * <p>Converts an array of bytes into a string of hexadecimal characters representing the values in the array. For
	 * example, calling this method on a byte array with the values {@code [0x01 0x23 0x45 0x67 0x89 0xab]} would return
	 * {@code "0123456789ab"}. No guarantees are made as to the case of the returned string."
	 *  
	 * @param tokenBytes an array of bytes to represent as a string
	 * 
	 * @return a string of hexadecimal characters representing the values in the given byte array
	 */
	public static String tokenBytesToString(final byte[] tokenBytes) {
		final StringBuilder builder = new StringBuilder();

        for (final byte b : tokenBytes) {
        	final String hexString = Integer.toHexString(b & 0xff);
        	
        	if (hexString.length() == 1) {
        		// We need a leading zero
        		builder.append("0");
        	}
        	
            builder.append(hexString);
        }

        return builder.toString();
	}
}

/**
 * <p>Contains classes for working with APNs tokens and payloads.</p>
 * 
 * <p>Push notification payloads are <a href="http://json.org/">JSON</a> strings that contain information about how the
 * receiving device should handle and display the notification. The @{link ApnsPayloadBuilder} class is a tool to
 * construct payloads that comply with the APNs specification.</p>
 * 
 * <p>Device tokens identify the device to which a push notification is being sent. Ultimately, tokens need to be
 * expressed as an array of bytes, but a common practice is to transmit tokens from a device to a push notification
 * provider as a string of hexadecimal characters (e.g. the output of
 * <a href="https://developer.apple.com/library/mac/documentation/Cocoa/Reference/Foundation/Classes/NSData_Class/Reference/Reference.html#//apple_ref/occ/instm/NSData/description">
 * {@code [NSData describe]}</a>). The {@link com.relayrides.pushy.apns.util.TokenUtil} class provides methods for
 * converting tokens between hexadecimal strings and byte arrays.</p>
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
package com.relayrides.pushy.apns.util;

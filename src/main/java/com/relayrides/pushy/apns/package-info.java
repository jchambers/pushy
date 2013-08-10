/**
 * <p>Contains classes and interfaces for interacting with the Apple Push Notification service (APNs).</p>
 * 
 * <p>The {@link com.relayrides.pushy.apns.PushManager} class is the main public-facing class in Pushy. A
 * {@code PushManager} manages connections to APNs and manages the queue of outbound notifications. Generally, Pushy
 * users should create a single long-lived {@code PushManager} instance per "topic" (or receiving app) and use it
 * throughout the lifetime of their provider application.</p>
 * 
 * <p>The {@link com.relayrides.pushy.apns.ApnsPushNotification} interface represents a single APNs push notification
 * sent to a single device. A simple concrete implementation of the {@code ApnsPushNotification} interface and tools
 * for constructing push notification payloads can be found in the {@code com.relayrides.pushy.apns.util} package.
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
package com.relayrides.pushy.apns;

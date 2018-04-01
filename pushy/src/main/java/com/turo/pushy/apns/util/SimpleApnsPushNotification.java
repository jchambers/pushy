/*
 * Copyright (c) 2013-2017 Turo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.turo.pushy.apns.util;

import com.turo.pushy.apns.ApnsPushNotification;
import com.turo.pushy.apns.DeliveryPriority;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * A simple and immutable implementation of the {@link ApnsPushNotification} interface.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see ApnsPayloadBuilder
 */
public class SimpleApnsPushNotification implements ApnsPushNotification {

    private final String token;
    private final String payload;
    private final Date invalidationTime;
    private final DeliveryPriority priority;
    private final String topic;
    private final String collapseId;
    private final UUID apnsId;

    /**
     * Constructs a new push notification with the given token, topic, and payload. No expiration time is set for the
     * notification, so APNs will not attempt to store the notification for later delivery if the initial attempt fails.
     * An "immediate" delivery priority is used for the notification, and as such the payload should contain an alert,
     * sound, or badge component.
     *
     * @param token the device token to which this push notification should be delivered
     * @param topic the topic to which this notification should be sent
     * @param payload the payload to include in this push notification
     *
     * @see DeliveryPriority#IMMEDIATE
     */
    public SimpleApnsPushNotification(final String token, final String topic, final String payload) {
        this(token, topic, payload, null, DeliveryPriority.IMMEDIATE, null, null);
    }
    /**
     * Constructs a new push notification with the given token, topic, payload, and expiration time. An "immediate"
     * delivery priority is used for the notification, and as such the payload should contain an alert, sound, or badge
     * component.
     *
     * @param token the device token to which this push notification should be delivered
     * @param topic the topic to which this notification should be sent
     * @param payload the payload to include in this push notification
     * @param invalidationTime the time at which Apple's servers should stop trying to deliver this message; if
     * {@code null}, no delivery attempts beyond the first will be made
     *
     * @see DeliveryPriority#IMMEDIATE
     */
    public SimpleApnsPushNotification(final String token, final String topic, final String payload, final Date invalidationTime) {
        this(token, topic, payload, invalidationTime, DeliveryPriority.IMMEDIATE, null, null);
    }

    /**
     * Constructs a new push notification with the given token, topic, payload, delivery expiration time, and delivery
     * priority.
     *
     * @param token the device token to which this push notification should be delivered
     * @param topic the topic to which this notification should be sent
     * @param payload the payload to include in this push notification
     * @param invalidationTime the time at which Apple's servers should stop trying to deliver this message; if
     * {@code null}, no delivery attempts beyond the first will be made
     * @param priority the priority with which this notification should be delivered to the receiving device
     */
    public SimpleApnsPushNotification(final String token, final String topic, final String payload, final Date invalidationTime, final DeliveryPriority priority) {
        this(token, topic, payload, invalidationTime, priority, null, null);
    }

    /**
     * Constructs a new push notification with the given token, topic, payload, delivery expiration time, delivery
     * priority, and "collapse identifier."
     *
     * @param token the device token to which this push notification should be delivered; must not be {@code null}
     * @param topic the topic to which this notification should be sent; must not be {@code null}
     * @param payload the payload to include in this push notification; must not be {@code null}
     * @param invalidationTime the time at which Apple's servers should stop trying to deliver this message; if
     * {@code null}, no delivery attempts beyond the first will be made
     * @param priority the priority with which this notification should be delivered to the receiving device
     * @param collapseId the "collapse identifier" for this notification, which allows it to supersede or be superseded
     */
    public SimpleApnsPushNotification(final String token, final String topic, final String payload, final Date invalidationTime, final DeliveryPriority priority, final String collapseId) {
        this(token, topic, payload, invalidationTime, priority, collapseId, null);
    }

    /**
     * Constructs a new push notification with the given token, topic, payload, delivery expiration time, delivery
     * priority, "collapse identifier," and unique push notification identifier.
     *
     * @param token the device token to which this push notification should be delivered; must not be {@code null}
     * @param topic the topic to which this notification should be sent; must not be {@code null}
     * @param payload the payload to include in this push notification; must not be {@code null}
     * @param invalidationTime the time at which Apple's servers should stop trying to deliver this message; if
     * {@code null}, no delivery attempts beyond the first will be made
     * @param priority the priority with which this notification should be delivered to the receiving device
     * @param collapseId the "collapse identifier" for this notification, which allows it to supersede or be superseded
     * @param apnsId the unique identifier for this notification; may be {@code null}, in which case the APNs server
     * will assign a unique identifier automatically
     */
    public SimpleApnsPushNotification(final String token, final String topic, final String payload, final Date invalidationTime, final DeliveryPriority priority, final String collapseId, final UUID apnsId) {
        Objects.requireNonNull(token, "Destination device token must not be null.");
        Objects.requireNonNull(topic, "Destination topic must not be null.");
        Objects.requireNonNull(payload, "Payload must not be null.");

        this.token = token;
        this.payload = payload;
        this.invalidationTime = invalidationTime;
        this.priority = priority;
        this.topic = topic;
        this.collapseId = collapseId;
        this.apnsId = apnsId;
    }

    /**
     * Returns the token of the device to which this push notification should be delivered.
     *
     * @return the token of the device to which this push notification should be delivered
     */
    @Override
    public String getToken() {
        return this.token;
    }

    /**
     * Returns the payload to include in this push notification.
     *
     * @return the payload to include in this push notification
     */
    @Override
    public String getPayload() {
        return this.payload;
    }

    /**
     * Returns the time at which this push notification is no longer valid and should no longer be delivered.
     *
     * @return the time at which this push notification is no longer valid and should no longer be delivered
     */
    @Override
    public Date getExpiration() {
        return this.invalidationTime;
    }

    /**
     * Returns the priority with which this push notification should be delivered to the receiving device.
     *
     * @return the priority with which this push notification should be delivered to the receiving device
     */
    @Override
    public DeliveryPriority getPriority() {
        return this.priority;
    }

    /**
     * Returns the topic to which this push notification should be sent.
     *
     * @return the topic to which this push notification should be sent
     */
    @Override
    public String getTopic() {
        return this.topic;
    }

    /**
     * Returns the "collapse ID" for this push notification, which allows it to supersede or be superseded by other
     * notifications with the same ID.
     *
     * @return the "collapse ID" for this push notification
     */
    @Override
    public String getCollapseId() {
        return this.collapseId;
    }

    /**
     * Returns the canonical identifier for this push notification. The APNs server will include the given identifier in
     * all responses related to this push notification. If no identifier is provided, the server will assign a unique
     * identifier automatically.
     *
     * @return a unique identifier for this notification; may be {@code null}, in which case the APNs server will assign
     * an identifier automatically
     */
    @Override
    public UUID getApnsId() {
        return this.apnsId;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.invalidationTime == null) ? 0 : this.invalidationTime.hashCode());
        result = prime * result + ((this.payload == null) ? 0 : this.payload.hashCode());
        result = prime * result + ((this.priority == null) ? 0 : this.priority.hashCode());
        result = prime * result + ((this.token == null) ? 0 : this.token.hashCode());
        result = prime * result + ((this.topic == null) ? 0 : this.topic.hashCode());
        result = prime * result + ((this.collapseId == null) ? 0 : this.collapseId.hashCode());
        result = prime * result + ((this.apnsId == null) ? 0 : this.apnsId.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof SimpleApnsPushNotification)) {
            return false;
        }
        final SimpleApnsPushNotification other = (SimpleApnsPushNotification) obj;
        if (this.invalidationTime == null) {
            if (other.invalidationTime != null) {
                return false;
            }
        } else if (!this.invalidationTime.equals(other.invalidationTime)) {
            return false;
        }
        if (this.payload == null) {
            if (other.payload != null) {
                return false;
            }
        } else if (!this.payload.equals(other.payload)) {
            return false;
        }
        if (this.priority != other.priority) {
            return false;
        }
        if (this.token == null) {
            if (other.token != null) {
                return false;
            }
        } else if (!this.token.equals(other.token)) {
            return false;
        }
        if (this.topic == null) {
            if (other.topic != null) {
                return false;
            }
        } else if (!this.topic.equals(other.topic)) {
            return false;
        }
        if (Objects.equals(this.collapseId, null)) {
            if (!Objects.equals(other.collapseId , null)) {
                return false;
            }
        } else if (!this.collapseId.equals(other.collapseId)) {
            return false;
        }
        if (Objects.equals(this.apnsId, null)) {
            if (!Objects.equals(other.apnsId , null)) {
                return false;
            }
        } else if (!this.apnsId.equals(other.apnsId)) {
            return false;
        }
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "SimpleApnsPushNotification{" +
                "token='" + token + '\'' +
                ", payload='" + payload + '\'' +
                ", invalidationTime=" + invalidationTime +
                ", priority=" + priority +
                ", topic='" + topic + '\'' +
                ", collapseId='" + collapseId + '\'' +
                ", apnsId=" + apnsId +
                '}';
    }
}

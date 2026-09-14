/*
 * Copyright 2024-2026 Revetware LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soklet.servlet.javax;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Soklet integration implementation of {@link HttpSession}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class SokletHttpSession implements HttpSession {
	@NonNull
	private static final HttpSessionContext SHARED_HTTP_SESSION_CONTEXT;

	static {
		SHARED_HTTP_SESSION_CONTEXT = SokletHttpSessionContext.fromDefaults();
	}

	@NonNull
	private volatile UUID sessionId;
	@NonNull
	private final Object stateLock;
	@NonNull
	private final Instant createdAt;
	@NonNull
	private volatile Instant lastAccessedAt;
	@NonNull
	private final Map<@NonNull String, @NonNull Object> attributes;
	@NonNull
	private final ServletContext servletContext;
	private volatile boolean invalidated;
	private volatile int maxInactiveInterval;
	private volatile boolean isNew;

	@NonNull
	public static SokletHttpSession fromServletContext(@NonNull ServletContext servletContext) {
		requireNonNull(servletContext);
		return new SokletHttpSession(servletContext);
	}

	private SokletHttpSession(@NonNull ServletContext servletContext) {
		requireNonNull(servletContext);

		this.sessionId = UUID.randomUUID();
		this.stateLock = new Object();
		this.createdAt = Instant.now();
		this.lastAccessedAt = this.createdAt;
		this.attributes = new ConcurrentHashMap<>();
		this.servletContext = servletContext;
		this.invalidated = false;
		this.maxInactiveInterval = 0;
		this.isNew = true;
	}

	public void setSessionId(@NonNull UUID sessionId) {
		requireNonNull(sessionId);
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			this.sessionId = sessionId;
		}
	}

	@NonNull
	private UUID getSessionId() {
		return this.sessionId;
	}

	@NonNull
	private Instant getCreatedAt() {
		return this.createdAt;
	}

	@NonNull
	private Instant getLastAccessedAt() {
		return this.lastAccessedAt;
	}

	@NonNull
	private Map<@NonNull String, @NonNull Object> getAttributes() {
		return this.attributes;
	}

	boolean isInvalidated() {
		return this.invalidated;
	}

	private void ensureNotInvalidated() {
		if (isInvalidated())
			throw new IllegalStateException("Session is invalidated");
	}

	void markAccessed() {
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			this.lastAccessedAt = Instant.now();
		}
	}

	void markNotNew() {
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			this.isNew = false;
		}
	}

	// Implementation of HttpSession methods below:

	@Override
	public long getCreationTime() {
		ensureNotInvalidated();
		return getCreatedAt().toEpochMilli();
	}

	@Override
	@NonNull
	public String getId() {
		ensureNotInvalidated();
		return getSessionId().toString();
	}

	@Override
	public long getLastAccessedTime() {
		ensureNotInvalidated();
		return getLastAccessedAt().toEpochMilli();
	}

	@Override
	@NonNull
	public ServletContext getServletContext() {
		ensureNotInvalidated();
		return this.servletContext;
	}

	@Override
	public void setMaxInactiveInterval(int interval) {
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			this.maxInactiveInterval = interval;
		}
	}

	@Override
	public int getMaxInactiveInterval() {
		ensureNotInvalidated();
		return this.maxInactiveInterval;
	}

	@Override
	@NonNull
	@Deprecated
	public HttpSessionContext getSessionContext() {
		ensureNotInvalidated();
		return SHARED_HTTP_SESSION_CONTEXT;
	}

	@Override
	@Nullable
	public Object getAttribute(@Nullable String name) {
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			return getAttributes().get(name);
		}
	}

	@Override
	@Nullable
	@Deprecated
	public Object getValue(@Nullable String name) {
		ensureNotInvalidated();
		return getAttribute(name);
	}

	@Override
	@NonNull
	public Enumeration<@NonNull String> getAttributeNames() {
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			return Collections.enumeration(new ArrayList<>(getAttributes().keySet()));
		}
	}

	@Override
	@Deprecated
	public @NonNull String @NonNull [] getValueNames() {
		ensureNotInvalidated();
		List<@NonNull String> valueNames = Collections.list(getAttributeNames());
		return valueNames.toArray(new String[0]);
	}

	@Override
	public void setAttribute(@NonNull String name,
													 @Nullable Object value) {
		requireNonNull(name);

		if (value == null) {
			removeAttribute(name);
			return;
		}

		synchronized (this.stateLock) {
			ensureNotInvalidated();
			if (getAttributes().get(name) == value)
				return;
		}

		// Servlet binding notifications precede publication. Application callbacks run
		// outside the state lock and may reenter or invalidate this session.
		Throwable failure = notifyBindingListener(name, value, true, null);
		Object existingValue = null;
		boolean published;
		synchronized (this.stateLock) {
			published = !this.invalidated;
			if (published)
				existingValue = getAttributes().put(name, value);
		}

		if (published) {
			// The replaced object is no longer visible before it is notified.
			if (existingValue != value)
				failure = notifyBindingListener(name, existingValue, false, failure);
		} else {
			// Balance the pre-bind notification without resurrecting an invalidated session.
			failure = notifyBindingListener(name, value, false, failure);
			IllegalStateException invalidatedFailure = new IllegalStateException("Session is invalidated");
			if (failure == null)
				failure = invalidatedFailure;
			else
				failure.addSuppressed(invalidatedFailure);
		}

		rethrowListenerFailure(failure);
	}

	@Override
	@Deprecated
	public void putValue(@NonNull String name,
											 @NonNull Object value) {
		requireNonNull(name);
		requireNonNull(value);

		ensureNotInvalidated();
		setAttribute(name, value);
	}

	@Override
	public void removeAttribute(@NonNull String name) {
		requireNonNull(name);

		Object existingValue;
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			existingValue = getAttributes().remove(name);
		}

		rethrowListenerFailure(notifyBindingListener(name, existingValue, false, null));
	}

	@Override
	@Deprecated
	public void removeValue(@NonNull String name) {
		requireNonNull(name);

		ensureNotInvalidated();
		removeAttribute(name);
	}

	@Override
	public void invalidate() {
		Map<@NonNull String, @NonNull Object> removedAttributes;
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			removedAttributes = Map.copyOf(getAttributes());
			getAttributes().clear();
			this.invalidated = true;
		}

		Throwable failure = null;
		for (Map.Entry<@NonNull String, @NonNull Object> entry : removedAttributes.entrySet())
			failure = notifyBindingListener(entry.getKey(), entry.getValue(), false, failure);

		rethrowListenerFailure(failure);
	}

	@Nullable
	private Throwable notifyBindingListener(@NonNull String name,
																			 @Nullable Object value,
																			 boolean bound,
																			 @Nullable Throwable failure) {
		if (!(value instanceof HttpSessionBindingListener))
			return failure;

		try {
			HttpSessionBindingEvent event = new HttpSessionBindingEvent(this, name, value);
			if (bound)
				((HttpSessionBindingListener) value).valueBound(event);
			else
				((HttpSessionBindingListener) value).valueUnbound(event);
		} catch (RuntimeException | Error e) {
			if (failure == null)
				return e;
			if (failure != e)
				failure.addSuppressed(e);
		}

		return failure;
	}

	private void rethrowListenerFailure(@Nullable Throwable failure) {
		if (failure instanceof RuntimeException)
			throw (RuntimeException) failure;
		if (failure instanceof Error)
			throw (Error) failure;
	}

	@Override
	public boolean isNew() {
		ensureNotInvalidated();
		return this.isNew;
	}
}

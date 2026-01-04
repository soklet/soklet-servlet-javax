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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
		SHARED_HTTP_SESSION_CONTEXT = SokletHttpSessionContext.withDefaults();
	}

	@NonNull
	private volatile UUID sessionId;
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
	public static SokletHttpSession withServletContext(@NonNull ServletContext servletContext) {
		requireNonNull(servletContext);
		return new SokletHttpSession(servletContext);
	}

	private SokletHttpSession(@NonNull ServletContext servletContext) {
		requireNonNull(servletContext);

		this.sessionId = UUID.randomUUID();
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
		this.sessionId = sessionId;
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

	private void setInvalidated(boolean invalidated) {
		this.invalidated = invalidated;
	}

	private void ensureNotInvalidated() {
		if (isInvalidated())
			throw new IllegalStateException("Session is invalidated");
	}

	void markAccessed() {
		this.lastAccessedAt = Instant.now();
	}

	void markNotNew() {
		this.isNew = false;
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
		return this.servletContext;
	}

	@Override
	public void setMaxInactiveInterval(int interval) {
		this.maxInactiveInterval = interval;
	}

	@Override
	public int getMaxInactiveInterval() {
		return this.maxInactiveInterval;
	}

	@Override
	@NonNull
	@Deprecated
	public HttpSessionContext getSessionContext() {
		return SHARED_HTTP_SESSION_CONTEXT;
	}

	@Override
	@Nullable
	public Object getAttribute(@Nullable String name) {
		ensureNotInvalidated();
		return getAttributes().get(name);
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
		ensureNotInvalidated();
		return Collections.enumeration(getAttributes().keySet());
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

		ensureNotInvalidated();

		if (value == null) {
			removeAttribute(name);
		} else {
			Object existingValue = getAttributes().get(name);

			if (existingValue != null && existingValue instanceof HttpSessionBindingListener)
				((HttpSessionBindingListener) existingValue).valueUnbound(new HttpSessionBindingEvent(this, name, existingValue));

			getAttributes().put(name, value);

			if (value instanceof HttpSessionBindingListener)
				((HttpSessionBindingListener) value).valueBound(new HttpSessionBindingEvent(this, name, value));
		}
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

		ensureNotInvalidated();

		Object existingValue = getAttributes().get(name);

		if (existingValue != null && existingValue instanceof HttpSessionBindingListener)
			((HttpSessionBindingListener) existingValue).valueUnbound(new HttpSessionBindingEvent(this, name, existingValue));

		getAttributes().remove(name);
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
		// Copy to prevent modification while iterating
		Set<@NonNull String> namesToRemove = new HashSet<>(getAttributes().keySet());

		for (String name : namesToRemove)
			removeAttribute(name);

		setInvalidated(true);
	}

	@Override
	public boolean isNew() {
		return this.isNew;
	}
}

/*
 * Copyright 2024-2025 Revetware LLC.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class SokletHttpSession implements HttpSession {
	@Nonnull
	private static final HttpSessionContext SHARED_HTTP_SESSION_CONTEXT;

	static {
		SHARED_HTTP_SESSION_CONTEXT = new SokletHttpSessionContext();
	}

	@Nonnull
	private UUID sessionId;
	@Nonnull
	private final Instant createdAt;
	@Nonnull
	private final Map<String, Object> attributes;
	@Nonnull
	private final ServletContext servletContext;
	private boolean invalidated;
	private int maxInactiveInterval;

	public SokletHttpSession(@Nonnull ServletContext servletContext) {
		requireNonNull(servletContext);

		this.sessionId = UUID.randomUUID();
		this.createdAt = Instant.now();
		this.attributes = new HashMap<>();
		this.servletContext = servletContext;
		this.invalidated = false;
		this.maxInactiveInterval = 0;
	}

	public void setSessionId(@Nonnull UUID sessionId) {
		requireNonNull(sessionId);
		this.sessionId = sessionId;
	}

	@Nonnull
	protected UUID getSessionId() {
		return this.sessionId;
	}

	@Nonnull
	protected Instant getCreatedAt() {
		return this.createdAt;
	}

	@Nonnull
	protected Map<String, Object> getAttributes() {
		return this.attributes;
	}

	protected boolean isInvalidated() {
		return this.invalidated;
	}

	protected void setInvalidated(boolean invalidated) {
		this.invalidated = invalidated;
	}

	protected void ensureNotInvalidated() {
		if (isInvalidated())
			throw new IllegalStateException("Session is invalidated");
	}

	// Implementation of HttpSession methods below:

	@Override
	public long getCreationTime() {
		ensureNotInvalidated();
		return getCreatedAt().toEpochMilli();
	}

	@Override
	@Nonnull
	public String getId() {
		return getSessionId().toString();
	}

	@Override
	public long getLastAccessedTime() {
		ensureNotInvalidated();
		return getCreatedAt().toEpochMilli();
	}

	@Override
	@Nonnull
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
	@Nonnull
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
	@Nonnull
	public Enumeration<String> getAttributeNames() {
		ensureNotInvalidated();
		return Collections.enumeration(getAttributes().keySet());
	}

	@Override
	@Nonnull
	@Deprecated
	public String[] getValueNames() {
		ensureNotInvalidated();
		List<String> valueNames = Collections.list(getAttributeNames());
		return valueNames.toArray(new String[0]);
	}

	@Override
	public void setAttribute(@Nonnull String name,
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
	public void putValue(@Nonnull String name,
											 @Nonnull Object value) {
		requireNonNull(name);
		requireNonNull(value);

		ensureNotInvalidated();
		setAttribute(name, value);
	}

	@Override
	public void removeAttribute(@Nonnull String name) {
		requireNonNull(name);

		ensureNotInvalidated();

		Object existingValue = getAttributes().get(name);

		if (existingValue != null && existingValue instanceof HttpSessionBindingListener)
			((HttpSessionBindingListener) existingValue).valueUnbound(new HttpSessionBindingEvent(this, name, existingValue));

		getAttributes().remove(name);
	}

	@Override
	@Deprecated
	public void removeValue(@Nonnull String name) {
		requireNonNull(name);

		ensureNotInvalidated();
		removeAttribute(name);
	}

	@Override
	public void invalidate() {
		setInvalidated(true);

		// Copy to prevent modification while iterating
		Set<String> namesToRemove = new HashSet<>(getAttributes().keySet());

		for (String name : namesToRemove)
			removeAttribute(name);
	}

	@Override
	public boolean isNew() {
		return true;
	}
}
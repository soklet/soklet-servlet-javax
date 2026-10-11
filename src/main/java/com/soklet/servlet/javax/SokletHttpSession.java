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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Soklet integration implementation of {@link HttpSession}.
 * <p>
 * New sessions snapshot the context timeout (minutes) as an interval in seconds.
 * Nonpositive timeouts mean no expiration; values exceeding the seconds range
 * saturate at {@link Integer#MAX_VALUE}. This adapter stores timeout metadata
 * but does not schedule eviction. Applications own session persistence,
 * expiration checks, and invalidation.
 * <p>
 * Concurrent identical attribute setters share the pre-publication binding notification.
 * Ordinary setters wait for its result. A setter invoked by a binding listener joins
 * an already pending identical bind without waiting, allowing listener reentry.
 * Failures from that operation reach its owner and ordinary waiting setters.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class SokletHttpSession implements HttpSession {
	// Listener reentry must not wait for another callback that may depend on it.
	// The outermost callback removes this flag, including on listener failure.
	@NonNull
	private static final ThreadLocal<@Nullable Boolean> BINDING_LISTENER_CALLBACK = new ThreadLocal<>();
	@NonNull
	private static final HttpSessionContext SHARED_HTTP_SESSION_CONTEXT;

	static {
		SHARED_HTTP_SESSION_CONTEXT = SokletHttpSessionContext.fromDefaults();
	}

	@NonNull
	private volatile UUID sessionId;
	@NonNull
	private final Object stateLock;
	// Guarded by stateLock; callbacks never execute while holding that lock.
	@NonNull
	private final Map<@NonNull String, @NonNull IdentityHashMap<@NonNull Object, @NonNull PendingBinding>> pendingBindings;
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
		this.pendingBindings = new HashMap<>();
		this.createdAt = Instant.now();
		this.lastAccessedAt = this.createdAt;
		this.attributes = new ConcurrentHashMap<>();
		this.servletContext = servletContext;
		this.invalidated = false;
		// ServletContext uses minutes; HttpSession uses seconds. Preserve "never
		// expires" for nonpositive values and saturate instead of overflowing.
		this.maxInactiveInterval = (int) Math.min(Integer.MAX_VALUE,
				Math.max(0L, servletContext.getSessionTimeout()) * 60L);
		this.isNew = true;
	}

	void setSessionId(@NonNull UUID sessionId) {
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

		PendingBinding pendingBinding;
		boolean ownsBinding;
		synchronized (this.stateLock) {
			ensureNotInvalidated();
			if (getAttributes().get(name) == value)
				return;
			IdentityHashMap<Object, PendingBinding> bindings = this.pendingBindings.computeIfAbsent(name,
					ignored -> new IdentityHashMap<>());
			pendingBinding = bindings.get(value);
			// A published value may already have been removed while its replacement
			// callback is still running. Coalesce only the pre-publication phase.
			ownsBinding = pendingBinding == null || pendingBinding.published;
			if (ownsBinding) {
				pendingBinding = new PendingBinding();
				bindings.put(value, pendingBinding);
			} else if (pendingBinding.owner == Thread.currentThread() || isBindingListenerCallback()) {
				// Reentrant listener setters join without waiting for publication.
				// Waiting could deadlock two callbacks that bind each other's values.
				// Failures still reach the owner and ordinary waiting setters.
				return;
			}
		}

		if (!ownsBinding) {
			awaitPendingBinding(pendingBinding);
			return;
		}

		Throwable failure = null;
		try {
			// Notify before publication, with no state lock held. Identical concurrent
			// setters share this operation instead of delivering a second pre-bind.
			failure = notifyBindingListener(name, value, true, null);
			Object existingValue = null;
			boolean published;
			synchronized (this.stateLock) {
				published = !this.invalidated;
				if (published) {
					existingValue = getAttributes().put(name, value);
					pendingBinding.published = true;
				}
			}

			if (published) {
				// The replaced object is no longer visible before it is notified.
				if (existingValue != value)
					failure = notifyBindingListener(name, existingValue, false, failure);
			} else {
				// Balance the pre-bind without resurrecting an invalidated session.
				failure = notifyBindingListener(name, value, false, failure);
				IllegalStateException invalidatedFailure = new IllegalStateException("Session is invalidated");
				if (failure == null)
					failure = invalidatedFailure;
				else
					failure.addSuppressed(invalidatedFailure);
			}
		} catch (RuntimeException | Error e) {
			if (failure == null)
				failure = e;
			else if (failure != e)
				failure.addSuppressed(e);
		} finally {
			synchronized (this.stateLock) {
				pendingBinding.failure = failure;
				pendingBinding.completed = true;
				IdentityHashMap<Object, PendingBinding> bindings = this.pendingBindings.get(name);
				if (bindings != null && bindings.remove(value, pendingBinding) && bindings.isEmpty())
					this.pendingBindings.remove(name);
				this.stateLock.notifyAll();
			}
		}

		rethrowListenerFailure(failure);
	}

	private static boolean isBindingListenerCallback() {
		if (Boolean.TRUE.equals(BINDING_LISTENER_CALLBACK.get()))
			return true;
		BINDING_LISTENER_CALLBACK.remove();
		return false;
	}

	private void awaitPendingBinding(@NonNull PendingBinding pendingBinding) {
		boolean interrupted = false;
		Throwable failure;
		try {
			synchronized (this.stateLock) {
				while (!pendingBinding.completed && !this.invalidated) {
					try {
						this.stateLock.wait();
					} catch (InterruptedException e) {
						interrupted = true;
					}
				}
				ensureNotInvalidated();
				failure = pendingBinding.failure;
			}
		} finally {
			if (interrupted)
				Thread.currentThread().interrupt();
		}
		rethrowListenerFailure(failure);
	}

	private static final class PendingBinding {
		private final Thread owner = Thread.currentThread();
		private boolean published;
		private boolean completed;
		@Nullable
		private Throwable failure;
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
			this.stateLock.notifyAll();
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

		Boolean previousCallback = BINDING_LISTENER_CALLBACK.get();
		BINDING_LISTENER_CALLBACK.set(Boolean.TRUE);
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
		} finally {
			if (previousCallback == null)
				BINDING_LISTENER_CALLBACK.remove();
			else
				BINDING_LISTENER_CALLBACK.set(previousCallback);
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

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

import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Binding callbacks may fail, reenter, or overlap another thread's session access.
 */
@Timeout(15)
public class SessionBindingLifecycleTests {
	private SokletHttpSession session() {
		return SokletHttpSession.fromServletContext(SokletServletContext.fromDefaults());
	}

	@Test
	public void bindingVisibilityFollowsServletContract() {
		SokletHttpSession session = session();
		Object old = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				assertNull(session.getAttribute("key"));
			}

			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				assertNotSame(this, session.getAttribute("key"));
			}
		};
		session.setAttribute("key", old);
		Object replacement = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				assertSame(old, session.getAttribute("key"));
			}
		};
		session.setAttribute("key", replacement);
		assertSame(replacement, session.getAttribute("key"));
	}

	@Test
	public void rebindingIdenticalObjectDoesNotEmitSpuriousNotifications() {
		SokletHttpSession session = session();
		AtomicInteger bound = new AtomicInteger();
		AtomicInteger unbound = new AtomicInteger();
		Object listener = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				assertNull(session.getAttribute("key"));
				bound.incrementAndGet();
			}

			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				unbound.incrementAndGet();
			}
		};
		session.setAttribute("key", listener);
		session.setAttribute("key", listener);
		assertSame(listener, session.getAttribute("key"));
		assertEquals(1, bound.get());
		assertEquals(0, unbound.get());
	}

	@Test
	public void removeDetachesBeforeCallbackAndPreservesReentrantReplacement() {
		SokletHttpSession session = session();
		AtomicInteger notifications = new AtomicInteger();
		session.setAttribute("key", new HttpSessionBindingListener() {
			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				assertEquals(1, notifications.incrementAndGet());
				assertNull(session.getAttribute("key"));
				session.setAttribute("key", "replacement");
			}
		});
		session.removeAttribute("key");
		assertEquals("replacement", session.getAttribute("key"));
		assertEquals(1, notifications.get());
	}

	@Test
	public void replacementUnbindCannotRemoveReentrantReplacement() {
		SokletHttpSession session = session();
		AtomicInteger notifications = new AtomicInteger();
		session.setAttribute("key", new HttpSessionBindingListener() {
			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				assertEquals(1, notifications.incrementAndGet());
				assertEquals("outer", session.getAttribute("key"));
				session.setAttribute("key", "inner");
			}
		});
		session.setAttribute("key", "outer");
		assertEquals("inner", session.getAttribute("key"));
	}

	@Test
	public void removalRemainsCompleteWhenUnbindingThrows() {
		SokletHttpSession session = session();
		IllegalArgumentException expected = new IllegalArgumentException("listener failure");
		session.setAttribute("key", new HttpSessionBindingListener() {
			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				throw expected;
			}
		});
		assertSame(expected, assertThrows(IllegalArgumentException.class, () -> session.removeAttribute("key")));
		assertNull(session.getAttribute("key"));
		session.removeAttribute("key");
	}

	@Test
	public void failedBindingStillCompletesReplacementAndOldUnbinding() {
		SokletHttpSession session = session();
		AtomicInteger removed = new AtomicInteger();
		session.setAttribute("key", new HttpSessionBindingListener() {
			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				removed.incrementAndGet();
			}
		});
		IllegalArgumentException expected = new IllegalArgumentException("binding failure");
		Object replacement = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				throw expected;
			}
		};
		assertSame(expected, assertThrows(IllegalArgumentException.class, () -> session.setAttribute("key", replacement)));
		assertSame(replacement, session.getAttribute("key"));
		assertEquals(1, removed.get());
	}

	@Test
	public void invalidationDetachesEverythingAndNotifiesDespiteFailures() {
		SokletHttpSession session = session();
		AtomicInteger removed = new AtomicInteger();
		for (String name : List.of("one", "two")) {
			session.setAttribute(name, new HttpSessionBindingListener() {
				@Override
				public void valueUnbound(HttpSessionBindingEvent event) {
					removed.incrementAndGet();
					assertTrue(session.isInvalidated());
					assertThrows(IllegalStateException.class, () -> session.getAttribute(event.getName()));
					assertThrows(IllegalStateException.class, () -> session.setAttribute("resurrect", "value"));
					if ("one".equals(event.getName()))
						throw new IllegalArgumentException("one");
					throw new AssertionError("two");
				}
			});
		}
		Throwable failure = assertThrows(Throwable.class, session::invalidate);
		assertEquals(2, removed.get());
		assertEquals(1, failure.getSuppressed().length);
		assertEquals(Set.of("one", "two"), Set.of(failure.getMessage(), failure.getSuppressed()[0].getMessage()));
		assertTrue(session.isInvalidated());
		assertThrows(IllegalStateException.class, session::invalidate);
		assertThrows(IllegalStateException.class, () -> session.setAttribute("later", "value"));
	}

	@Test
	public void invalidationDuringBindingPreventsPublicationAndBalancesNotification() {
		SokletHttpSession session = session();
		AtomicInteger removed = new AtomicInteger();
		Object listener = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				assertNull(session.getAttribute("key"));
				session.invalidate();
			}

			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				removed.incrementAndGet();
			}
		};
		assertThrows(IllegalStateException.class, () -> session.setAttribute("key", listener));
		assertTrue(session.isInvalidated());
		assertEquals(1, removed.get());
	}

	@Test
	public void concurrentInvalidationCanCompleteDuringBlockedBinding() throws Exception {
		SokletHttpSession session = session();
		CountDownLatch binding = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger removed = new AtomicInteger();
		ExecutorService executor = executor();
		try {
			Future<?> pending = executor.submit(() -> session.setAttribute("key", new HttpSessionBindingListener() {
				@Override
				public void valueBound(HttpSessionBindingEvent event) {
					binding.countDown();
					await(release);
				}

				@Override
				public void valueUnbound(HttpSessionBindingEvent event) {
					removed.incrementAndGet();
				}
			}));
			assertTrue(binding.await(3, TimeUnit.SECONDS));
			session.invalidate();
			assertTrue(session.isInvalidated());
			release.countDown();
			ExecutionException failure = assertThrows(ExecutionException.class, () -> pending.get(3, TimeUnit.SECONDS));
			assertInstanceOf(IllegalStateException.class, failure.getCause());
			assertEquals(1, removed.get());
		} finally {
			release.countDown();
			shutdown(executor);
		}
	}

	@Test
	public void concurrentReplacementSurvivesBlockedRemovalCallback() throws Exception {
		SokletHttpSession session = session();
		CountDownLatch unbinding = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		session.setAttribute("key", new HttpSessionBindingListener() {
			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				unbinding.countDown();
				await(release);
			}
		});
		ExecutorService executor = executor();
		try {
			Future<?> pending = executor.submit(() -> session.removeAttribute("key"));
			assertTrue(unbinding.await(3, TimeUnit.SECONDS));
			assertNull(session.getAttribute("key"));
			session.setAttribute("key", "replacement");
			release.countDown();
			pending.get(3, TimeUnit.SECONDS);
			assertEquals("replacement", session.getAttribute("key"));
		} finally {
			release.countDown();
			shutdown(executor);
		}
	}

	@Test
	public void invalidationStateIsVisibleBeforeBlockedUnbindingCompletes() throws Exception {
		SokletHttpSession session = session();
		CountDownLatch unbinding = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		session.setAttribute("key", new HttpSessionBindingListener() {
			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				unbinding.countDown();
				await(release);
			}
		});
		ExecutorService executor = executor();
		try {
			Future<?> pending = executor.submit(session::invalidate);
			assertTrue(unbinding.await(3, TimeUnit.SECONDS));
			assertThrows(IllegalStateException.class, () -> session.setAttribute("late", "value"));
			assertThrows(IllegalStateException.class, () -> session.getAttribute("key"));
			release.countDown();
			pending.get(3, TimeUnit.SECONDS);
		} finally {
			release.countDown();
			shutdown(executor);
		}
	}

	@Test
	public void attributeNameEnumerationIsASnapshot() {
		SokletHttpSession session = session();
		session.setAttribute("one", 1);
		Enumeration<String> names = session.getAttributeNames();
		session.removeAttribute("one");
		session.setAttribute("two", 2);
		assertEquals(List.of("one"), Collections.list(names));
	}

	private static ExecutorService executor() {
		return Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "session-binding-regression");
			thread.setDaemon(true);
			return thread;
		});
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback was not released within its bound");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}

	private static void shutdown(ExecutorService executor) throws InterruptedException {
		executor.shutdownNow();
		assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS), "Executor did not terminate");
	}
}

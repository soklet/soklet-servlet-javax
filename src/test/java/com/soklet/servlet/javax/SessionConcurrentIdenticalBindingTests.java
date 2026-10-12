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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Concurrent identical setters share a pre-publication callback and its terminal result. */
@Timeout(15)
public class SessionConcurrentIdenticalBindingTests {
	@Test
	public void identicalConcurrentBindingsDeliverOneBoundAndOneUnboundNotification() throws Exception {
		try (BindingRace race = new BindingRace(null)) {
			Future<Boolean> duplicate = race.startDuplicate();
			assertEquals(1, race.bound.get());
			assertNull(race.session.getAttribute("key"));
			race.release.countDown();
			race.owner.get(3, TimeUnit.SECONDS);
			assertFalse(duplicate.get(3, TimeUnit.SECONDS));
			assertSame(race.listener, race.session.getAttribute("key"));
			race.session.invalidate();
			assertEquals(1, race.bound.get());
			assertEquals(1, race.unbound.get());
		}
	}

	@Test
	public void sharedCallbackFailureReachesBothSettersWithoutDuplicatingNotifications() throws Exception {
		RuntimeException expected = new IllegalArgumentException("binding failure");
		try (BindingRace race = new BindingRace(expected)) {
			Future<Boolean> duplicate = race.startDuplicate();
			race.release.countDown();
			assertSame(expected, assertThrows(ExecutionException.class,
					() -> race.owner.get(3, TimeUnit.SECONDS)).getCause());
			assertSame(expected, assertThrows(ExecutionException.class,
					() -> duplicate.get(3, TimeUnit.SECONDS)).getCause());
			assertSame(race.listener, race.session.getAttribute("key"));
			race.session.invalidate();
			assertEquals(1, race.bound.get());
			assertEquals(1, race.unbound.get());
		}
	}

	@Test
	public void invalidationReleasesDuplicateSetterBeforeBlockedBindingCompletes() throws Exception {
		try (BindingRace race = new BindingRace(null)) {
			Future<Boolean> duplicate = race.startDuplicate();
			race.session.invalidate();
			assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class,
					() -> duplicate.get(3, TimeUnit.SECONDS)).getCause());
			assertFalse(race.owner.isDone(), "Invalidation does not wait for the application callback");
			race.release.countDown();
			assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class,
					() -> race.owner.get(3, TimeUnit.SECONDS)).getCause());
			assertEquals(1, race.bound.get());
			assertEquals(1, race.unbound.get());
			assertThrows(IllegalStateException.class, () -> race.session.getAttribute("key"));
		}
	}

	@Test
	public void interruptedDuplicateSetterCompletesBindingAndPreservesInterruptStatus() throws Exception {
		try (BindingRace race = new BindingRace(null)) {
			Future<Boolean> duplicate = race.startDuplicate();
			race.duplicateThread.get().interrupt();
			race.release.countDown();
			race.owner.get(3, TimeUnit.SECONDS);
			assertTrue(duplicate.get(3, TimeUnit.SECONDS));
			assertSame(race.listener, race.session.getAttribute("key"));
			race.session.invalidate();
			assertEquals(1, race.bound.get());
			assertEquals(1, race.unbound.get());
		}
	}

	@Test
	public void sameThreadIdenticalReentryUsesOuterBindingBeforePublication() {
		SokletHttpSession session = SokletHttpSession.fromServletContext(SokletServletContext.fromDefaults());
		AtomicInteger bound = new AtomicInteger();
		AtomicInteger unbound = new AtomicInteger();
		Object listener = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				assertEquals(1, bound.incrementAndGet());
				assertNull(session.getAttribute("key"));
				session.setAttribute("key", this);
				assertNull(session.getAttribute("key"));
				session.setAttribute("other", "visible");
			}

			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				unbound.incrementAndGet();
			}
		};
		session.setAttribute("key", listener);
		assertSame(listener, session.getAttribute("key"));
		assertEquals("visible", session.getAttribute("other"));
		session.invalidate();
		assertEquals(1, bound.get());
		assertEquals(1, unbound.get());
	}

	@Test
	public void removedPublishedBindingCanBeReboundWhileReplacementNotificationIsBlocked() throws Exception {
		SokletHttpSession session = SokletHttpSession.fromServletContext(SokletServletContext.fromDefaults());
		CountDownLatch replacedEntered = new CountDownLatch(1);
		CountDownLatch releaseReplacement = new CountDownLatch(1);
		HttpSessionBindingListener replaced = new HttpSessionBindingListener() {
			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				replacedEntered.countDown();
				await(releaseReplacement);
			}
		};
		AtomicInteger bound = new AtomicInteger();
		AtomicInteger unbound = new AtomicInteger();
		HttpSessionBindingListener value = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				bound.incrementAndGet();
			}

			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				unbound.incrementAndGet();
			}
		};
		session.setAttribute("key", replaced);
		ExecutorService executor = executor();
		try {
			Future<?> firstBinding = executor.submit(() -> session.setAttribute("key", value));
			assertTrue(replacedEntered.await(3, TimeUnit.SECONDS));
			assertSame(value, session.getAttribute("key"));
			session.removeAttribute("key");
			session.setAttribute("key", value);
			assertSame(value, session.getAttribute("key"));
			assertEquals(2, bound.get());
			assertEquals(1, unbound.get());
			releaseReplacement.countDown();
			firstBinding.get(3, TimeUnit.SECONDS);
			session.invalidate();
			assertEquals(2, unbound.get());
		} finally {
			releaseReplacement.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void mutualListenerReentryCompletesAndBalancesBindings() throws Exception {
		assertMutualListenerReentryCompletes(false, null);
	}

	@Test
	public void mutualListenerReentryPreservesOuterSetterFailure() throws Exception {
		assertMutualListenerReentryCompletes(false, new IllegalArgumentException("outer binding failure"));
	}

	@Test
	public void mutualListenerReentryAcrossSessionsCompletes() throws Exception {
		assertMutualListenerReentryCompletes(true, null);
	}

	@Test
	public void callbackContextIsClearedAfterListenerFailure() throws Exception {
		RuntimeException bindingFailure = new IllegalArgumentException("shared binding failure");
		AssertionError callbackFailure = new AssertionError("earlier callback failure");
		try (BindingRace race = new BindingRace(bindingFailure)) {
			Future<Boolean> duplicate = race.startDuplicate(() -> {
				HttpSessionBindingListener failedListener = new HttpSessionBindingListener() {
					@Override
					public void valueBound(HttpSessionBindingEvent event) {
						throw callbackFailure;
					}
				};
				assertSame(callbackFailure, assertThrows(AssertionError.class,
						() -> race.session.setAttribute("failed", failedListener)));
			});
			race.release.countDown();
			assertSame(bindingFailure, assertThrows(ExecutionException.class,
					() -> race.owner.get(3, TimeUnit.SECONDS)).getCause());
			assertSame(bindingFailure, assertThrows(ExecutionException.class,
					() -> duplicate.get(3, TimeUnit.SECONDS)).getCause());
			race.session.invalidate();
			assertEquals(1, race.bound.get());
			assertEquals(1, race.unbound.get());
		}
	}

	private static void assertMutualListenerReentryCompletes(boolean separateSessions,
			RuntimeException failure) throws Exception {
		SokletHttpSession first = SokletHttpSession.fromServletContext(SokletServletContext.fromDefaults());
		SokletHttpSession[] sessions = new SokletHttpSession[]{first, separateSessions
				? SokletHttpSession.fromServletContext(SokletServletContext.fromDefaults()) : first};
		CountDownLatch entered = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger[] bound = new AtomicInteger[]{new AtomicInteger(), new AtomicInteger()};
		AtomicInteger[] unbound = new AtomicInteger[]{new AtomicInteger(), new AtomicInteger()};
		HttpSessionBindingListener[] values = new HttpSessionBindingListener[2];
		for (int index = 0; index < values.length; index++) {
			int current = index;
			values[index] = new HttpSessionBindingListener() {
				@Override
				public void valueBound(HttpSessionBindingEvent event) {
					assertEquals(1, bound[current].incrementAndGet());
					assertNull(sessions[current].getAttribute("key"));
					entered.countDown();
					await(release);
					sessions[1 - current].setAttribute("key", values[1 - current]);
					if (current == 0 && failure != null)
						throw failure;
				}

				@Override
				public void valueUnbound(HttpSessionBindingEvent event) {
					unbound[current].incrementAndGet();
				}
			};
		}
		ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
			Thread thread = new Thread(task, "session-mutual-reentry-regression");
			thread.setDaemon(true);
			return thread;
		});
		try {
			Future<?> firstSetter = executor.submit(() -> sessions[0].setAttribute("key", values[0]));
			Future<?> secondSetter = executor.submit(() -> sessions[1].setAttribute("key", values[1]));
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			release.countDown();
			if (failure == null)
				firstSetter.get(3, TimeUnit.SECONDS);
			else
				assertSame(failure, assertThrows(ExecutionException.class,
						() -> firstSetter.get(3, TimeUnit.SECONDS)).getCause());
			secondSetter.get(3, TimeUnit.SECONDS);
			assertEquals(1, bound[0].get());
			assertEquals(1, bound[1].get());
		} finally {
			release.countDown();
			first.invalidate();
			if (separateSessions)
				sessions[1].invalidate();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
		}
		assertEquals(1, unbound[0].get());
		assertEquals(1, unbound[1].get());
	}

	private static final class BindingRace implements AutoCloseable {
		private final SokletHttpSession session = SokletHttpSession.fromServletContext(SokletServletContext.fromDefaults());
		private final AtomicInteger bound = new AtomicInteger();
		private final AtomicInteger unbound = new AtomicInteger();
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final ExecutorService ownerExecutor = executor();
		private final ExecutorService duplicateExecutor = executor();
		private final AtomicReference<Thread> duplicateThread = new AtomicReference<>();
		private final Object listener;
		private final Future<?> owner;

		private BindingRace(RuntimeException failure) throws InterruptedException {
			this.listener = new HttpSessionBindingListener() {
				@Override
				public void valueBound(HttpSessionBindingEvent event) {
					assertNull(session.getAttribute("key"));
					bound.incrementAndGet();
					entered.countDown();
					await(release);
					if (failure != null)
						throw failure;
				}

				@Override
				public void valueUnbound(HttpSessionBindingEvent event) {
					unbound.incrementAndGet();
				}
			};
			this.owner = this.ownerExecutor.submit(() -> this.session.setAttribute("key", this.listener));
			assertTrue(this.entered.await(3, TimeUnit.SECONDS));
		}

		private Future<Boolean> startDuplicate() throws InterruptedException {
			return startDuplicate(() -> {});
		}

		private Future<Boolean> startDuplicate(Runnable beforeSet) throws InterruptedException {
			CountDownLatch started = new CountDownLatch(1);
			Future<Boolean> duplicate = this.duplicateExecutor.submit(() -> {
				this.duplicateThread.set(Thread.currentThread());
				beforeSet.run();
				started.countDown();
				this.session.setAttribute("key", this.listener);
				return Thread.currentThread().isInterrupted();
			});
			assertTrue(started.await(3, TimeUnit.SECONDS));
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			while (this.duplicateThread.get().getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline)
				Thread.sleep(1);
			assertEquals(Thread.State.TIMED_WAITING, this.duplicateThread.get().getState());
			assertFalse(duplicate.isDone(), "An overlapping setter waits for publication or invalidation");
			return duplicate;
		}

		@Override
		public void close() throws InterruptedException {
			this.release.countDown();
			this.ownerExecutor.shutdownNow();
			this.duplicateExecutor.shutdownNow();
			assertTrue(this.ownerExecutor.awaitTermination(2, TimeUnit.SECONDS));
			assertTrue(this.duplicateExecutor.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	private static ExecutorService executor() {
		return Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "session-identical-binding-regression");
			thread.setDaemon(true);
			return thread;
		});
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(5, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}
}

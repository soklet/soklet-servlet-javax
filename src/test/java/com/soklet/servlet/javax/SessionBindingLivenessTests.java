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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Application locks, interruption, and unusual listener failures cannot strand a joining setter. */
@Timeout(10)
public class SessionBindingLivenessTests {
	@Test
	public void monitorHoldingFollowerTimesOutWithoutDuplicatingBindingNotifications() throws Exception {
		SokletHttpSession session = newSession();
		Object monitor = new Object();
		CountDownLatch monitorHeld = new CountDownLatch(1);
		CountDownLatch boundEntered = new CountDownLatch(1);
		AtomicInteger bound = new AtomicInteger();
		AtomicInteger unbound = new AtomicInteger();
		HttpSessionBindingListener listener = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				bound.incrementAndGet();
				boundEntered.countDown();
				synchronized (monitor) {
					// The follower must return to release this application-owned monitor.
				}
			}

			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				unbound.incrementAndGet();
			}
		};
		ExecutorService executor = executor();
		try {
			Future<Long> follower = executor.submit(() -> {
				synchronized (monitor) {
					monitorHeld.countDown();
					await(boundEntered);
					long started = System.nanoTime();
					session.setAttribute("key", listener);
					assertNull(session.getAttribute("key"), "The owner still needs this monitor before publication");
					return System.nanoTime() - started;
				}
			});
			assertTrue(monitorHeld.await(3, TimeUnit.SECONDS));
			Future<?> owner = executor.submit(() -> session.setAttribute("key", listener));
			assertTrue(follower.get(2, TimeUnit.SECONDS) < TimeUnit.SECONDS.toNanos(2));
			owner.get(2, TimeUnit.SECONDS);
			assertSame(listener, session.getAttribute("key"));
			session.invalidate();
			assertEquals(1, bound.get());
			assertEquals(1, unbound.get());
		} finally {
			if (!session.isInvalidated())
				session.invalidate();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void interruptedFollowerReturnsBeforeBlockedOwnerAndPreservesInterruptStatus() throws Exception {
		try (BlockedBinding binding = new BlockedBinding(null)) {
			Future<Boolean> follower = binding.startFollower(false);
			binding.followerThread.get().interrupt();
			assertTrue(follower.get(1, TimeUnit.SECONDS));
			assertFalse(binding.owner.isDone());
			assertNull(binding.session.getAttribute("key"));
			binding.finishOwner();
			binding.assertBalanced();
		}
	}

	@Test
	public void previouslyInterruptedFollowerReturnsWithoutClearingItsInterruptStatus() throws Exception {
		try (BlockedBinding binding = new BlockedBinding(null)) {
			Future<Boolean> follower = binding.startFollower(true);
			assertTrue(follower.get(1, TimeUnit.SECONDS));
			assertFalse(binding.owner.isDone());
			assertNull(binding.session.getAttribute("key"));
			binding.finishOwner();
			binding.assertBalanced();
		}
	}

	@Test
	public void unrelatedBindingNotificationsDoNotExtendTheJoinDeadline() throws Exception {
		try (BlockedBinding binding = new BlockedBinding(null)) {
			AtomicBoolean stop = new AtomicBoolean();
			ExecutorService notifier = executor();
			try {
				Future<?> notifications = notifier.submit(() -> {
					while (!stop.get()) {
						binding.session.setAttribute("unrelated", new Object());
						try {
							Thread.sleep(1);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							return;
						}
					}
				});
				Future<Boolean> follower = binding.startFollower(false);
				assertFalse(follower.get(2, TimeUnit.SECONDS));
				assertFalse(binding.owner.isDone());
				stop.set(true);
				notifications.get(1, TimeUnit.SECONDS);
				binding.finishOwner();
				binding.assertBalanced();
			} finally {
				stop.set(true);
				notifier.shutdownNow();
				assertTrue(notifier.awaitTermination(2, TimeUnit.SECONDS));
			}
		}
	}

	@Test
	public void checkedBindingFailureReachesOwnerAndWaitingFollowerWithoutSkippingPublication() throws Exception {
		IOException expected = new IOException("checked binding failure");
		try (BlockedBinding binding = new BlockedBinding(expected)) {
			Future<Boolean> follower = binding.startFollower(false);
			binding.release.countDown();
			assertSame(expected, assertThrows(ExecutionException.class,
					() -> binding.owner.get(2, TimeUnit.SECONDS)).getCause());
			assertSame(expected, assertThrows(ExecutionException.class,
					() -> follower.get(2, TimeUnit.SECONDS)).getCause());
			assertSame(binding.listener, binding.session.getAttribute("key"));
			binding.assertBalanced();
		}
	}

	@Test
	public void checkedUnbindingFailuresDoNotSkipOtherInvalidationNotifications() {
		SokletHttpSession session = newSession();
		IOException first = new IOException("first unbinding failure");
		IOException second = new IOException("second unbinding failure");
		AtomicInteger unbound = new AtomicInteger();
		for (IOException failure : new IOException[]{first, second})
			session.setAttribute(failure.getMessage(), new HttpSessionBindingListener() {
				@Override
				public void valueUnbound(HttpSessionBindingEvent event) {
					unbound.incrementAndGet();
					SessionBindingLivenessTests.<RuntimeException>throwUnchecked(failure);
				}
			});
		IOException thrown = assertThrows(IOException.class, session::invalidate);
		assertTrue(thrown == first || thrown == second);
		assertArrayEquals(new Throwable[]{thrown == first ? second : first}, thrown.getSuppressed());
		assertEquals(2, unbound.get());
		assertThrows(IllegalStateException.class, () -> session.getAttribute("key"));
	}

	private static final class BlockedBinding implements AutoCloseable {
		private final SokletHttpSession session = newSession();
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicInteger bound = new AtomicInteger();
		private final AtomicInteger unbound = new AtomicInteger();
		private final AtomicReference<Thread> followerThread = new AtomicReference<>();
		private final ExecutorService executor = executor();
		private final HttpSessionBindingListener listener;
		private final Future<?> owner;

		private BlockedBinding(Throwable failure) throws InterruptedException {
			this.listener = new HttpSessionBindingListener() {
				@Override
				public void valueBound(HttpSessionBindingEvent event) {
					assertNull(session.getAttribute("key"));
					bound.incrementAndGet();
					entered.countDown();
					await(release);
					if (failure != null)
						SessionBindingLivenessTests.<RuntimeException>throwUnchecked(failure);
				}

				@Override
				public void valueUnbound(HttpSessionBindingEvent event) {
					unbound.incrementAndGet();
				}
			};
			this.owner = this.executor.submit(() -> this.session.setAttribute("key", this.listener));
			assertTrue(this.entered.await(3, TimeUnit.SECONDS));
		}

		private Future<Boolean> startFollower(boolean initiallyInterrupted) throws InterruptedException {
			CountDownLatch started = new CountDownLatch(1);
			Future<Boolean> follower = this.executor.submit(() -> {
				this.followerThread.set(Thread.currentThread());
				if (initiallyInterrupted)
					Thread.currentThread().interrupt();
				started.countDown();
				this.session.setAttribute("key", this.listener);
				return Thread.currentThread().isInterrupted();
			});
			assertTrue(started.await(3, TimeUnit.SECONDS));
			if (!initiallyInterrupted) {
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
				while (!isWaiting(this.followerThread.get().getState()) && System.nanoTime() < deadline)
					Thread.sleep(1);
				assertTrue(isWaiting(this.followerThread.get().getState()));
				assertFalse(follower.isDone());
			}
			return follower;
		}

		private void finishOwner() throws Exception {
			this.release.countDown();
			this.owner.get(2, TimeUnit.SECONDS);
			assertSame(this.listener, this.session.getAttribute("key"));
		}

		private void assertBalanced() {
			this.session.invalidate();
			assertEquals(1, this.bound.get());
			assertEquals(1, this.unbound.get());
		}

		@Override
		public void close() throws InterruptedException {
			this.release.countDown();
			if (!this.session.isInvalidated())
				this.session.invalidate();
			this.executor.shutdownNow();
			assertTrue(this.executor.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	private static boolean isWaiting(Thread.State state) {
		return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
	}

	private static SokletHttpSession newSession() {
		return SokletHttpSession.fromServletContext(SokletServletContext.fromDefaults());
	}

	private static ExecutorService executor() {
		return Executors.newCachedThreadPool(task -> {
			Thread thread = new Thread(task, "session-binding-liveness-regression");
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

	@SuppressWarnings("unchecked")
	private static <T extends Throwable> void throwUnchecked(Throwable failure) throws T {
		throw (T) failure;
	}
}

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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Identical setters restore a superseded binding while its original notification is pending. */
@Timeout(15)
public class SessionBindingRevivalTests {
	@Test
	public void removedPendingBindingCanBeReboundWithoutDuplicateNotifications() throws Exception {
		try (BindingRace race = new BindingRace()) {
			race.session.removeAttribute("key");
			race.session.setAttribute("key", race.listener);
			race.finish();
			assertSame(race.listener, race.session.getAttribute("key"));
			assertTrue(race.believesBound.get());
			assertEquals(1, race.bound.get());
			assertEquals(0, race.unbound.get());
			race.session.invalidate();
			assertEquals(1, race.unbound.get());
		}
	}

	@Test
	public void removedPendingBindingCanBeReboundFromSameOrDifferentSessionCallback() throws Exception {
		for (boolean separateSession : List.of(false, true)) {
			try (BindingRace race = new BindingRace()) {
				race.session.removeAttribute("key");
				SokletHttpSession companionSession = separateSession ? session() : race.session;
				companionSession.setAttribute("companion", new HttpSessionBindingListener() {
					@Override
					public void valueBound(HttpSessionBindingEvent event) {
						race.session.setAttribute("key", race.listener);
					}
				});
				race.finish();
				assertSame(race.listener, race.session.getAttribute("key"));
				assertTrue(race.believesBound.get());
				assertEquals(1, race.bound.get());
				assertEquals(0, race.unbound.get());
			}
		}
	}

	@Test
	public void laterReplacementOrRemovalStillSupersedesRevivedPendingBinding() throws Exception {
		for (boolean remove : List.of(false, true)) {
			try (BindingRace race = new BindingRace()) {
				race.session.removeAttribute("key");
				race.session.setAttribute("key", race.listener);
				if (remove)
					race.session.removeAttribute("key");
				else
					race.session.setAttribute("key", "later");
				race.finish();
				assertEquals(remove ? null : "later", race.session.getAttribute("key"));
				assertFalse(race.believesBound.get());
				assertEquals(1, race.bound.get());
				assertEquals(1, race.unbound.get());
			}
		}
	}

	@Test
	public void invalidationStillSupersedesRevivedPendingBinding() throws Exception {
		try (BindingRace race = new BindingRace()) {
			race.session.removeAttribute("key");
			race.session.setAttribute("key", race.listener);
			race.session.invalidate();
			race.release.countDown();
			assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class,
					() -> race.owner.get(3, TimeUnit.SECONDS)).getCause());
			assertFalse(race.believesBound.get());
			assertEquals(1, race.bound.get());
			assertEquals(1, race.unbound.get());
		}
	}

	@Test
	public void identicalReentryAfterRemovalRevivesOuterBinding() {
		SokletHttpSession session = session();
		AtomicInteger bound = new AtomicInteger();
		AtomicInteger unbound = new AtomicInteger();
		HttpSessionBindingListener listener = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				assertEquals(1, bound.incrementAndGet());
				session.removeAttribute("key");
				session.setAttribute("key", this);
			}

			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				unbound.incrementAndGet();
			}
		};
		session.setAttribute("key", listener);
		assertSame(listener, session.getAttribute("key"));
		assertEquals(1, bound.get());
		assertEquals(0, unbound.get());
	}

	@Test
	public void supersededEarlierBindingCanReplaceTheCurrentValueWithoutDuplicateNotifications() throws Exception {
		try (BindingRace race = new BindingRace()) {
			race.session.setAttribute("key", "replacement");
			race.session.setAttribute("key", race.listener);
			race.finish();
			assertSame(race.listener, race.session.getAttribute("key"));
			assertEquals(1, race.bound.get());
			assertEquals(0, race.unbound.get());
		}
	}

	private static SokletHttpSession session() {
		return SokletHttpSession.fromServletContext(SokletServletContext.fromDefaults());
	}

	private static final class BindingRace implements AutoCloseable {
		private final SokletHttpSession session = session();
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicInteger bound = new AtomicInteger();
		private final AtomicInteger unbound = new AtomicInteger();
		private final AtomicBoolean believesBound = new AtomicBoolean();
		private final HttpSessionBindingListener listener = new HttpSessionBindingListener() {
			@Override
			public void valueBound(HttpSessionBindingEvent event) {
				believesBound.set(true);
				if (bound.incrementAndGet() == 1) {
					entered.countDown();
					try {
						assertTrue(release.await(5, TimeUnit.SECONDS));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new AssertionError(e);
					}
				}
			}

			@Override
			public void valueUnbound(HttpSessionBindingEvent event) {
				believesBound.set(false);
				unbound.incrementAndGet();
			}
		};
		private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "session-binding-revival-regression");
			thread.setDaemon(true);
			return thread;
		});
		private final Future<?> owner;

		private BindingRace() throws InterruptedException {
			this.owner = this.executor.submit(() -> this.session.setAttribute("key", this.listener));
			assertTrue(this.entered.await(3, TimeUnit.SECONDS));
		}

		private void finish() throws Exception {
			this.release.countDown();
			this.owner.get(3, TimeUnit.SECONDS);
		}

		@Override
		public void close() throws InterruptedException {
			this.release.countDown();
			this.executor.shutdownNow();
			assertTrue(this.executor.awaitTermination(2, TimeUnit.SECONDS));
		}
	}
}


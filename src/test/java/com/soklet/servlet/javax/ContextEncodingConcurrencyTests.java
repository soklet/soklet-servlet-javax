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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for concurrent changes to context default encodings. */
@Timeout(15)
public class ContextEncodingConcurrencyTests {
	@Test
	public void requestEncodingMayBeClearedWhileRead() throws Exception {
		checkConcurrentClear(true);
	}

	@Test
	public void responseEncodingMayBeClearedWhileRead() throws Exception {
		checkConcurrentClear(false);
	}

	private void checkConcurrentClear(boolean request) throws Exception {
		var context = SokletServletContext.fromDefaults();
		var started = new CountDownLatch(1);
		var stop = new AtomicBoolean();
		Thread writer = Thread.ofPlatform().daemon().start(() -> {
			started.countDown();
			while (!stop.get()) {
				if (request) {
					context.setRequestCharacterEncoding("UTF-8");
					context.setRequestCharacterEncoding((String) null);
				} else {
					context.setResponseCharacterEncoding("UTF-8");
					context.setResponseCharacterEncoding((String) null);
				}
			}
		});
		try {
			assertTrue(started.await(5, TimeUnit.SECONDS));
			for (int i = 0; i < 2_000_000; i++) {
				String encoding = request ? context.getRequestCharacterEncoding() : context.getResponseCharacterEncoding();
				if (encoding != null)
					assertEquals("UTF-8", encoding);
			}
		} finally {
			stop.set(true);
			writer.join(5000);
			assertFalse(writer.isAlive());
		}
	}
}

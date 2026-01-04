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

import com.soklet.HttpMethod;
import com.soklet.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

/**
 * Tests for requested session ID extraction.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RequestedSessionIdTests {
	@Test
	public void cookieRequestedSessionIdWinsOverUrl() {
		Request request = Request.withRawUrl(HttpMethod.GET, "/path;jsessionid=url123")
				.headers(Map.of("Cookie", Set.of("JSESSIONID=cookie456")))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals("cookie456", http.getRequestedSessionId());
		Assertions.assertTrue(http.isRequestedSessionIdFromCookie());
		Assertions.assertFalse(http.isRequestedSessionIdFromURL());
	}

	@Test
	public void urlRequestedSessionIdUsedWhenNoCookiePresent() {
		Request request = Request.withRawUrl(HttpMethod.GET, "/path;jsessionid=url123/next")
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals("url123", http.getRequestedSessionId());
		Assertions.assertFalse(http.isRequestedSessionIdFromCookie());
		Assertions.assertTrue(http.isRequestedSessionIdFromURL());
		Assertions.assertTrue(http.isRequestedSessionIdFromUrl());
	}

	@Test
	public void requestedSessionIdAbsentReturnsNull() {
		Request request = Request.withRawUrl(HttpMethod.GET, "/path").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertNull(http.getRequestedSessionId());
		Assertions.assertFalse(http.isRequestedSessionIdFromCookie());
		Assertions.assertFalse(http.isRequestedSessionIdFromURL());
	}
}

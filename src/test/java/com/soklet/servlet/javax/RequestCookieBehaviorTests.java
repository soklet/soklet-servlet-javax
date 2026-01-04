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
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/*
 * Verify request cookie behavior matches servlet expectations.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RequestCookieBehaviorTests {
	@Test
	public void getCookiesReturnsNullWhenEmpty() {
		Request req = Request.withPath(HttpMethod.GET, "/x").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertNull(http.getCookies());
	}

	@Test
	public void cookieNamesAreCaseSensitive() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("Cookie", Set.of("a=1; A=2")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Cookie[] cookies = http.getCookies();
		Assertions.assertNotNull(cookies);

		Map<String, String> values = new HashMap<>();
		for (Cookie cookie : cookies)
			values.put(cookie.getName(), cookie.getValue());

		Assertions.assertEquals(Set.of("a", "A"), values.keySet());
		Assertions.assertEquals("1", values.get("a"));
		Assertions.assertEquals("2", values.get("A"));
	}

	@Test
	public void cookieValuesPreservePercentEncoding() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("Cookie", Set.of("token=a%2Fb%3B%20c")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Cookie[] cookies = http.getCookies();
		Assertions.assertNotNull(cookies);

		Map<String, String> values = new HashMap<>();
		for (Cookie cookie : cookies)
			values.put(cookie.getName(), cookie.getValue());

		Assertions.assertEquals("a%2Fb%3B%20c", values.get("token"));
	}

	@Test
	public void cookieEmptyValuesArePreserved() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("Cookie", Set.of("empty=")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Cookie[] cookies = http.getCookies();
		Assertions.assertNotNull(cookies);

		Map<String, String> values = new HashMap<>();
		for (Cookie cookie : cookies)
			values.put(cookie.getName(), cookie.getValue());

		Assertions.assertTrue(values.containsKey("empty"));
		Assertions.assertEquals("", values.get("empty"));
	}
}

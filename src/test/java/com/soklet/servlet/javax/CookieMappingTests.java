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

import com.soklet.MarshaledResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.Cookie;
import java.util.Collection;

/*
 * Verify cookies added via HttpServletResponse are emitted into Soklet MarshaledResponse cookies.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class CookieMappingTests {
	@Test
	public void responseCookiesAppearInMarshaledResponse() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		Cookie c = new Cookie("sid", "abc123");
		c.setHttpOnly(true);
		c.setSecure(true);
		c.setPath("/");
		c.setMaxAge(60);
		resp.addCookie(c);

		MarshaledResponse mr = resp.toMarshaledResponse();

		boolean any = mr.getCookies().stream().anyMatch(rc ->
				rc.getName().equals("sid") &&
						rc.getValue().get().equals("abc123") &&
						rc.getSecure() &&
						rc.getHttpOnly() &&
						rc.getPath().orElse("").equals("/") &&
						rc.getMaxAge().get().toSeconds() == 60L
		);

		Assertions.assertTrue(any, "Cookie 'sid' does not have correct values in marshaled response");
	}

	@Test
	public void setCookieHeadersExposeAddedCookies() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());

		Cookie first = new Cookie("a", "1");
		first.setPath("/");
		Cookie second = new Cookie("b", "2");

		resp.addCookie(first);
		resp.addCookie(second);

		Assertions.assertTrue(resp.containsHeader("Set-Cookie"));
		Assertions.assertNotNull(resp.getHeader("Set-Cookie"));

		Collection<String> values = resp.getHeaders("Set-Cookie");
		Assertions.assertEquals(2, values.size());
		Assertions.assertTrue(values.stream().anyMatch(value -> value.startsWith("a=1")));
		Assertions.assertTrue(values.stream().anyMatch(value -> value.startsWith("b=2")));
		Assertions.assertTrue(resp.getHeaderNames().stream().anyMatch(name -> "Set-Cookie".equalsIgnoreCase(name)));
	}
	@Test
	public void replacingSetCookieClearsPreviouslyAddedCookies() {
		var response = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		response.addHeader("set-cookie", "raw=old");
		response.addCookie(new Cookie("session", "old"));
		response.setHeader("sEt-CoOkIe", "session=; Max-Age=0; Path=/");
		Assertions.assertTrue(response.containsHeader("SET-COOKIE"));
		Assertions.assertEquals("session=; Max-Age=0; Path=/", response.getHeader("set-cookie"));
		Assertions.assertEquals(java.util.List.of("session=; Max-Age=0; Path=/"),
				new java.util.ArrayList<>(response.getHeaders("SET-cookie")));
		Assertions.assertTrue(response.toMarshaledResponse().getCookies().isEmpty());
		Assertions.assertTrue(response.toResponse().getCookies().isEmpty());
		Assertions.assertEquals(response.toMarshaledResponse().getHeaders(), response.toResponse().getHeaders());
	}

	@Test
	public void alternatingRawAndStructuredCookiesKeepTheirOccurrenceOrder() throws Exception {
		var response = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		response.addCookie(new Cookie("same", "first"));
		response.addHeader("set-cookie", "same=second");
		response.addCookie(new Cookie("same", "third"));
		response.addHeader("sEt-CoOkIe", "same=fourth");
		response.addCookie(new Cookie("same", "fifth"));
		var expected = java.util.List.of("same=first", "same=second", "same=third", "same=fourth", "same=fifth");
		Assertions.assertEquals(expected, new java.util.ArrayList<>(response.getHeaders("Set-Cookie")));
		Assertions.assertEquals("same=first", response.getHeader("Set-Cookie"));
		response.getOutputStream().write(1);
		response.resetBuffer();
		assertCookieOrder(response, expected);
		response.flushBuffer();
		response.setHeader("Set-Cookie", "ignored=value");
		response.addHeader("Set-Cookie", "ignored=value");
		response.addCookie(new Cookie("ignored", "value"));
		assertCookieOrder(response, expected);
	}

	@Test
	public void resetClearsBothCookieRepresentations() {
		var response = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		response.addCookie(new Cookie("same", "first"));
		response.addHeader("Set-Cookie", "same=second");
		response.addCookie(new Cookie("same", "third"));
		response.reset();
		Assertions.assertFalse(response.containsHeader("Set-Cookie"));
		Assertions.assertNull(response.getHeader("Set-Cookie"));
		Assertions.assertTrue(response.getHeaders("Set-Cookie").isEmpty());
		Assertions.assertFalse(response.getHeaderNames().stream().anyMatch("Set-Cookie"::equalsIgnoreCase));
		assertCookieOrder(response, java.util.List.of());
	}

	private static void assertCookieOrder(SokletHttpServletResponse response, java.util.List<String> expected) {
		var marshaled = response.toMarshaledResponse();
		var serialized = new java.util.ArrayList<String>();
		marshaled.getHeaders().entrySet().stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase("Set-Cookie"))
				.forEach(entry -> serialized.addAll(entry.getValue()));
		marshaled.getCookies().forEach(cookie -> serialized.add(cookie.toSetCookieHeaderRepresentation()));
		Assertions.assertEquals(expected, serialized);
		Assertions.assertEquals(marshaled.getHeaders(), response.toResponse().getHeaders());
		Assertions.assertEquals(marshaled.getCookies(), response.toResponse().getCookies());
	}

}

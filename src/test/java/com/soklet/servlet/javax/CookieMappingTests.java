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
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
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
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());

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
}

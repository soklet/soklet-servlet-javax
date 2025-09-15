/*
 * Copyright 2024-2025 Revetware LLC.
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

import com.soklet.core.MarshaledResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.Cookie;

/*
 * Verify cookies added via HttpServletResponse are emitted into Soklet MarshaledResponse cookies.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class CookieMappingTests {
	@Test
	public void responseCookiesAppearInMarshaledResponse() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequestPath("/x");
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
}

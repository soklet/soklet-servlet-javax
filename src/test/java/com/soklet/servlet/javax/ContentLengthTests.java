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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/*
 * Verify request content-length semantics.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ContentLengthTests {
	@Test
	public void contentLengthUsesHeaderWhenPresent() {
		Request request = Request.withPath(HttpMethod.POST, "/x")
				.headers(Map.of("Content-Length", Set.of("123")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals(123, http.getContentLength());
		Assertions.assertEquals(123L, http.getContentLengthLong());
	}

	@Test
	public void contentLengthReturnsMinusOneWhenMissing() {
		Request request = Request.withPath(HttpMethod.POST, "/x")
				.body("abc".getBytes(StandardCharsets.US_ASCII))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals(-1, http.getContentLength());
		Assertions.assertEquals(-1L, http.getContentLengthLong());
	}

	@Test
	public void contentLengthIntReturnsMinusOneOnOverflow() {
		Request request = Request.withPath(HttpMethod.POST, "/x")
				.headers(Map.of("Content-Length", Set.of("9999999999")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(request).build();

		Assertions.assertEquals(-1, http.getContentLength());
		Assertions.assertEquals(9_999_999_999L, http.getContentLengthLong());
	}
}

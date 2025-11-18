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

import com.soklet.HttpMethod;
import com.soklet.MarshaledResponse;
import com.soklet.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Set;

/*
 * Additional tests to cover servlet interop odds-and-ends semantics that were not exercised elsewhere.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class AdditionalInteropTests {
	@Test
	public void encodeUrlPassThrough() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequestPath("/x");
		String in = "http://example.com/a?b=c";
		Assertions.assertEquals(in, resp.encodeURL(in));
		Assertions.assertEquals(in, resp.encodeRedirectURL(in));
		Assertions.assertNull(resp.encodeURL(null));
		Assertions.assertNull(resp.encodeRedirectURL(null));
	}

	@Test
	public void containsHeaderIsCaseInsensitive() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequestPath("/x");
		resp.addHeader("X-Test", "1");
		Assertions.assertTrue(resp.containsHeader("x-test"));
		Assertions.assertTrue(resp.containsHeader("X-TEST"));
		Assertions.assertFalse(resp.containsHeader("missing"));
	}

	@Test
	public void changeSessionIdBehavior() {
		HttpServletRequest http = SokletHttpServletRequest.withRequest(Request.with(HttpMethod.GET, "/x").build()).build();
		// Without a session, changeSessionId should throw
		Assertions.assertThrows(IllegalStateException.class, http::changeSessionId);

		// With a session, id should change and be reflected on the session
		HttpSession session = http.getSession(true);
		String originalId = session.getId();
		String newId = http.changeSessionId();
		Assertions.assertNotEquals(originalId, newId);
		Assertions.assertEquals(newId, session.getId());
	}

	@Test
	public void getServerPortDefaultsFromScheme() {
		// https without explicit port -> 443
		Request httpsReq = Request.with(HttpMethod.GET, "/p")
				.headers(Map.of("X-Forwarded-Proto", Set.of("https"), "Host", Set.of("example.com")))
				.build();
		HttpServletRequest https = SokletHttpServletRequest.withRequest(httpsReq).build();
		Assertions.assertEquals(443, https.getServerPort());

		// http without explicit port -> 80
		Request httpReq = Request.with(HttpMethod.GET, "/p")
				.headers(Map.of("X-Forwarded-Proto", Set.of("http"), "Host", Set.of("example.com")))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(httpReq).build();
		Assertions.assertEquals(80, http.getServerPort());
	}

	@Test
	public void headerNamesAreUnmodifiable() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequestPath("/x");
		resp.addHeader("A", "1");
		var names = resp.getHeaderNames();
		Assertions.assertThrows(UnsupportedOperationException.class, () -> names.add("B"));
	}

	@Test
	public void flushBufferCommitsResponse() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequestPath("/x");
		resp.flushBuffer();
		Assertions.assertThrows(IllegalStateException.class, () -> resp.setHeader("X", "1"));
	}

	@Test
	public void contentLengthHeadersAreSet() {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequestPath("/x");
		resp.setContentLength(42);
		resp.setContentLengthLong(43L);
		MarshaledResponse mr = resp.toMarshaledResponse();
		// The later call should have replaced the value
		Assertions.assertEquals(Set.of("43"), mr.getHeaders().get("Content-Length"));
	}

	@Test
	public void getResourcePathsCurrentlyNotEmpty() {
		var ctx = SokletServletContext.withDefaults();
		Assertions.assertFalse(ctx.getResourcePaths("/").isEmpty());
	}

	@Test
	public void forwardedProtoControlsSchemeAndIsSecure() {
		var req = Request.with(HttpMethod.GET, "/p")
				.headers(Map.of("X-Forwarded-Proto", Set.of("https"), "Host", Set.of("example.com")))
				.build();
		var http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertEquals("https", http.getScheme());
		Assertions.assertTrue(http.isSecure());
	}
}

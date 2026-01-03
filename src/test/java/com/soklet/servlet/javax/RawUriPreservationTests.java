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
import com.soklet.Utilities.EffectiveOriginResolver.TrustPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

/*
 * Verify raw URI encoding is preserved for request URL/URI accessors.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RawUriPreservationTests {
	@Test
	public void requestUriPreservesRawEncoding() {
		Request req = Request.withRawUrl(HttpMethod.GET, "/a%20b%3Fc?x=1").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals("/a%20b%3Fc", http.getRequestURI());
		Assertions.assertTrue(http.getRequestURL().toString().endsWith("/a%20b%3Fc"));
	}

	@Test
	public void requestUrlPreservesRawEncoding() {
		Request req = Request.withRawUrl(HttpMethod.GET, "/a%20b%3Fc?x=1")
				.headers(Map.of(
						"Host", Set.of("example.com"),
						"X-Forwarded-Proto", Set.of("https")
				))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		String url = http.getRequestURL().toString();
		Assertions.assertTrue(url.startsWith("https://example.com/"));
		Assertions.assertTrue(url.contains("/a%20b%3Fc"));
		Assertions.assertFalse(url.contains("?x=1"));
	}
}

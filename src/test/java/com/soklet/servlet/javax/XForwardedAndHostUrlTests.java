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
import com.soklet.Request;
import com.soklet.Utilities.EffectiveOriginResolver.TrustPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;

/*
 * Verify absolute URL building from Host and X-Forwarded-Proto headers.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class XForwardedAndHostUrlTests {
	@Test
	public void buildsAbsoluteUrlFromForwardedProtoAndHost() {
		Request req = Request.withRawUrl(HttpMethod.GET, "/path?q=1")
				.headers(Map.of(
						"Host", Set.of("www.soklet.com:8443"),
						"X-Forwarded-Proto", Set.of("https")
				))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();

		StringBuffer url = http.getRequestURL();
		Assertions.assertTrue(url.toString().startsWith("https://www.soklet.com:8443/path"));
		Assertions.assertEquals("/path", http.getRequestURI());
		Assertions.assertEquals("q=1", http.getQueryString());
	}

	@Test
	public void forwardedProtoIsIgnoredWithoutTrust() {
		Request req = Request.withRawUrl(HttpMethod.GET, "/path?q=1")
				.headers(Map.of(
						"Host", Set.of("www.soklet.com:8443"),
						"X-Forwarded-Proto", Set.of("https")
				))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();

		StringBuffer url = http.getRequestURL();
		Assertions.assertTrue(url.toString().startsWith("http://www.soklet.com:8443/path"));
	}
}

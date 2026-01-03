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
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

/*
 * Tests for getRemoteAddr() and getRemoteHost() using Forwarded and X-Forwarded-For.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RemoteAddressParsingTests {
	@Test
	public void picksFirstAddressFromXff() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("X-Forwarded-For", Set.of("203.0.113.195, 198.51.100.178")))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals("203.0.113.195", http.getRemoteAddr());
	}

	@Test
	public void picksFirstAddressFromForwarded() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("Forwarded", Set.of("for=203.0.113.195, for=198.51.100.178")))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals("203.0.113.195", http.getRemoteAddr());
	}

	@Test
	public void forwardedIpv6WithPortIsParsed() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("Forwarded", Set.of("for=\"[2001:db8::1]:4711\"")))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals("2001:db8::1", http.getRemoteAddr());
	}

	@Test
	public void xffIgnoredWithoutTrustPolicy() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("X-Forwarded-For", Set.of("203.0.113.195, 198.51.100.178")))
				.remoteAddress(new InetSocketAddress("203.0.113.50", 1234))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertEquals("203.0.113.50", http.getRemoteAddr());
		Assertions.assertEquals("203.0.113.50", http.getRemoteHost());
	}

	@Test
	public void fallsBackToRemoteAddressWhenXffMissing() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.remoteAddress(new InetSocketAddress("203.0.113.50", 1234))
				.build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertEquals("203.0.113.50", http.getRemoteAddr());
		Assertions.assertEquals("203.0.113.50", http.getRemoteHost());
		Assertions.assertEquals(1234, http.getRemotePort());
	}

	@Test
	public void returnsNullWhenXffMissing() {
		Request req = Request.withPath(HttpMethod.GET, "/x").build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(req).build();
		Assertions.assertNull(http.getRemoteAddr());
		Assertions.assertNull(http.getRemoteHost());
	}
}

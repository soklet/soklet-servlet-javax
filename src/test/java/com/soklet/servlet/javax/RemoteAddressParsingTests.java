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
import com.soklet.EffectiveClientIpResolver;
import com.soklet.Request;
import com.soklet.EffectiveOriginResolver.TrustPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.net.InetSocketAddress;
import java.net.InetAddress;
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
				.headers(Map.of("X-Forwarded-For", List.of("203.0.113.195, 198.51.100.178")))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals("203.0.113.195", http.getRemoteAddr());
	}

	@Test
	public void picksFirstAddressFromForwarded() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("Forwarded", List.of("for=203.0.113.195, for=198.51.100.178")))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals("203.0.113.195", http.getRemoteAddr());
	}

	@Test
	public void forwardedIpv6WithPortIsParsed() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("Forwarded", List.of("for=\"[2001:db8::1]:4711\"")))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals(EffectiveClientIpResolver.withRequest(req, TrustPolicy.TRUST_ALL).resolve().orElseThrow().getHostAddress(), http.getRemoteAddr());
		Assertions.assertEquals(4711, http.getRemotePort());
	}

	@Test
	public void xffIgnoredWithoutTrustPolicy() {
		Request req = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("X-Forwarded-For", List.of("203.0.113.195, 198.51.100.178")))
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
	@Test
	public void allowlistClientAddressAndPortAgreeWithCoreAtTheUntrustedBoundary() throws Exception {
		for (String name : List.of("Forwarded", "X-Forwarded-For")) {
			for (List<String> chain : List.of(List.of("192.0.2.99:9999, 198.51.100.7:4711, 10.0.0.2:2222"),
					List.of("192.0.2.99:9999", "198.51.100.7:4711", "10.0.0.2:2222"),
					List.of("198.51.100.7:9999, 198.51.100.7:4711, 10.0.0.2:2222"))) {
				List<String> values = name.equals("Forwarded") ? chain.stream()
						.map(value -> "for=" + value.replace(", ", ", for=")).toList() : chain;
				Request request = Request.withPath(HttpMethod.GET, "/x").headers(Map.of(name, values))
						.remoteAddress(new InetSocketAddress("10.0.0.3", 3333)).build();
				Set<InetAddress> trusted = Set.of(InetAddress.getByName("10.0.0.2"), InetAddress.getByName("10.0.0.3"));
				HttpServletRequest http = SokletHttpServletRequest.withRequest(request)
						.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_PROXY_ALLOWLIST).trustedProxyAddresses(trusted).build();
				Assertions.assertEquals(EffectiveClientIpResolver.withRequest(request, TrustPolicy.TRUST_PROXY_ALLOWLIST)
						.trustedProxyAddresses(trusted).resolve().orElseThrow().getHostAddress(), http.getRemoteAddr());
				Assertions.assertEquals("198.51.100.7", http.getRemoteAddr());
				Assertions.assertEquals(4711, http.getRemotePort());
			}
		}
	}

	@Test
	public void untrustedPeerAndNonNumericForwardingFallBackToSocket() throws Exception {
		for (Map<String, List<String>> headers : List.of(Map.of("Forwarded", List.of("for=attacker.example:9999")),
				Map.of("X-Forwarded-For", List.of("attacker.example:9999")),
				Map.of("Forwarded", List.of("for=unknown"), "X-Forwarded-For", List.of("_hidden")))) {
			Request request = Request.withPath(HttpMethod.GET, "/x").headers(headers)
					.remoteAddress(new InetSocketAddress("10.0.0.3", 3333)).build();
			HttpServletRequest http = SokletHttpServletRequest.withRequest(request)
					.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_PROXY_ALLOWLIST)
					.trustedProxyAddresses(Set.of(InetAddress.getByName("10.0.0.3"))).build();
			Assertions.assertEquals("10.0.0.3", http.getRemoteAddr());
			Assertions.assertEquals(3333, http.getRemotePort());
		}
		Request request = Request.withPath(HttpMethod.GET, "/x").headers(Map.of("Forwarded", List.of("for=192.0.2.99:9999")))
				.remoteAddress(new InetSocketAddress("198.51.100.7", 4711)).build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(request)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_PROXY_ALLOWLIST)
				.trustedProxyAddresses(Set.of(InetAddress.getByName("10.0.0.3"))).build();
		Assertions.assertEquals("198.51.100.7", http.getRemoteAddr());
		Assertions.assertEquals(4711, http.getRemotePort());
	}

	@Test
	public void forwardedClientWithoutPortDoesNotBorrowProxyOrSpoofedPort() throws Exception {
		Request request = Request.withPath(HttpMethod.GET, "/x")
				.headers(Map.of("Forwarded", List.of("for=198.51.100.7:9999, for=198.51.100.7, for=10.0.0.2:2222")))
				.remoteAddress(new InetSocketAddress("10.0.0.3", 3333)).build();
		HttpServletRequest http = SokletHttpServletRequest.withRequest(request)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_PROXY_ALLOWLIST)
				.trustedProxyAddresses(Set.of(InetAddress.getByName("10.0.0.2"), InetAddress.getByName("10.0.0.3"))).build();
		Assertions.assertEquals("198.51.100.7", http.getRemoteAddr());
		Assertions.assertEquals(0, http.getRemotePort());
	}

}

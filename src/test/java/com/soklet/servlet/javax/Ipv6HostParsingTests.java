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
 * Verify IPv6 host/port parsing and request URL building.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class Ipv6HostParsingTests {
	@Test
	public void ipv6HostAndPortAreParsed() {
		Request req = Request.withPath(HttpMethod.GET, "/v6")
				.headers(Map.of(
						"Host", Set.of("[2001:db8::1]:8443"),
						"X-Forwarded-Proto", Set.of("https")
				))
				.build();

		HttpServletRequest http = SokletHttpServletRequest.withRequest(req)
				.forwardedHeaderTrustPolicy(TrustPolicy.TRUST_ALL)
				.build();
		Assertions.assertEquals("2001:db8::1", http.getServerName());
		Assertions.assertEquals(8443, http.getServerPort());
		Assertions.assertTrue(http.getRequestURL().toString().startsWith("https://[2001:db8::1]:8443/v6"));
	}
}

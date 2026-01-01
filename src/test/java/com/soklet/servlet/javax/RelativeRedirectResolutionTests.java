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
import java.util.Map;
import java.util.Set;

/*
 * Preferred behavior per RFC 3986: relative redirect is resolved against the parent path.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RelativeRedirectResolutionTests {
	@Test
	public void relativeRedirectResolvesAgainstParentPath() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRequest(
				Request.withPath(HttpMethod.GET, "/a/b/c")
						.headers(Map.of(
								"Host", Set.of("example.com"),
								"X-Forwarded-Proto", Set.of("https")
						))
						.build());
		resp.sendRedirect("d"); // relative, no leading '/'

		MarshaledResponse mr = resp.toMarshaledResponse();
		// Expected: /a/b/d (parent of /a/b/c is /a/b)
		Assertions.assertEquals(Set.of("https://example.com/a/b/d"), mr.getHeaders().get("Location"));
	}
}

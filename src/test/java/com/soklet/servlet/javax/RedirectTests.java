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

import com.soklet.core.HttpMethod;
import com.soklet.core.MarshaledResponse;
import com.soklet.core.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class RedirectTests {
	@Test
	public void relativeRedirectWithoutSlashUsesRequestPath() throws IOException {
		Request request = Request.with(HttpMethod.GET, "/root/path").build();
		SokletHttpServletResponse response = SokletHttpServletResponse.withRequest(request);
		response.sendRedirect("next");

		MarshaledResponse mr = response.toMarshaledResponse();
		Assertions.assertEquals(HttpServletResponse.SC_FOUND, (int) mr.getStatusCode());
		Set<String> locations = mr.getHeaders().get("Location");
		Assertions.assertTrue(locations != null && !locations.isEmpty(), "Location header missing");
		Assertions.assertTrue(locations.contains("/root/path/next"), "Location header wrong");
	}

	@Test
	public void absoluteRedirectSetsLocation() throws IOException {
		Request request = Request.with(HttpMethod.GET, "/root/path").build();
		SokletHttpServletResponse response = SokletHttpServletResponse.withRequest(request);
		response.sendRedirect("https://example.com/where");

		MarshaledResponse mr = response.toMarshaledResponse();
		Assertions.assertTrue(mr.getHeaders().get("Location").contains("https://example.com/where"));
	}

	@Test
	public void rootedRedirectSetsLocation() throws IOException {
		Request request = Request.with(HttpMethod.GET, "/root/path").build();
		SokletHttpServletResponse response = SokletHttpServletResponse.withRequest(request);
		response.sendRedirect("/rooted");

		MarshaledResponse mr = response.toMarshaledResponse();
		Assertions.assertTrue(mr.getHeaders().get("Location").contains("/rooted"));
	}
}

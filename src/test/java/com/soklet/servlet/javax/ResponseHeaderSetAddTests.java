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

import com.soklet.MarshaledResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Set;

/*
 * Verify setHeader vs addHeader semantics and date header formatting.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ResponseHeaderSetAddTests {
	@Test
	public void setHeaderReplacesValues() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setHeader("X-Alpha", "one");
		resp.setHeader("X-Alpha", "two"); // replaces

		MarshaledResponse mr = resp.toMarshaledResponse();
		Set<String> values = mr.getHeaders().get("X-Alpha");
		Assertions.assertEquals(Set.of("two"), values);
	}

	@Test
	public void addHeaderAppendsValues() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.addHeader("X-Beta", "one");
		resp.addHeader("X-Beta", "two");

		MarshaledResponse mr = resp.toMarshaledResponse();
		Set<String> values = mr.getHeaders().get("X-Beta");
		Assertions.assertEquals(Set.of("one", "two"), values);
	}

	@Test
	public void setDateHeaderFormatsAsRfc1123() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x", SokletServletContext.withDefaults());
		resp.setDateHeader("Date", 1_725_000_000_000L);
		MarshaledResponse mr = resp.toMarshaledResponse();
		String header = mr.getHeaders().get("Date").iterator().next();
		// Should look like "EEE, dd MMM yyyy HH:mm:ss GMT"
		Assertions.assertTrue(header.endsWith(" GMT"));
		Assertions.assertEquals(29, header.length());
	}
}

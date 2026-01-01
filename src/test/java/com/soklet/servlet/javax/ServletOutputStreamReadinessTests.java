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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.ServletOutputStream;
import java.nio.charset.StandardCharsets;

/*
 * Verify that output stream readiness reflects non-blocking semantics.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ServletOutputStreamReadinessTests {
	@Test
	public void outputStreamIsAlwaysReady() throws Exception {
		SokletHttpServletResponse resp = SokletHttpServletResponse.withRawPath("/x");
		ServletOutputStream out = resp.getOutputStream();
		Assertions.assertTrue(out.isReady());
		out.write("ok".getBytes(StandardCharsets.ISO_8859_1));
		out.flush();
		Assertions.assertTrue(out.isReady());
	}
}

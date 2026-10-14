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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.ServletOutputStream;
import java.io.ByteArrayOutputStream;
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
		SokletHttpServletResponse resp = SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
		ServletOutputStream out = resp.getOutputStream();
		Assertions.assertTrue(out.isReady());
		out.write("ok".getBytes(StandardCharsets.ISO_8859_1));
		out.flush();
		Assertions.assertTrue(out.isReady());
	}
	@Test
	public void zeroLengthWriteStillValidatesTheArrayRangeWithoutReportingOutput() throws Exception {
		var writes = new java.util.concurrent.atomic.AtomicInteger();
		var captured = new ByteArrayOutputStream();
		var stream = SokletServletOutputStream.withOutputStream(captured)
				.onWriteOccurred((ignored, count) -> writes.incrementAndGet()).build();
		byte[] bytes = new byte[1];
		Assertions.assertThrows(IndexOutOfBoundsException.class, () -> stream.write(bytes, -1, 0));
		Assertions.assertThrows(IndexOutOfBoundsException.class, () -> stream.write(bytes, 2, 0));
		Assertions.assertThrows(IndexOutOfBoundsException.class, () -> stream.write(bytes, 0, -1));
		Assertions.assertThrows(IndexOutOfBoundsException.class, () -> stream.write(bytes, 1, 1));
		stream.write(bytes, 1, 0);
		Assertions.assertEquals(0, writes.get());
		Assertions.assertEquals(0, captured.size());
		stream.write(bytes, 0, 1);
		Assertions.assertEquals(1, writes.get());
		Assertions.assertEquals(1, captured.size());
	}

}

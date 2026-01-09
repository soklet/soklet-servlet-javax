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
import javax.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;

/*
 * Verify ServletInputStream isFinished semantics.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class ServletInputStreamFinishedTests {
	@Test
	public void emptyStreamIsFinishedImmediately() throws Exception {
		ServletInputStream inputStream = SokletServletInputStream.fromInputStream(new ByteArrayInputStream(new byte[]{}));

		Assertions.assertTrue(inputStream.isFinished());
		Assertions.assertEquals(-1, inputStream.read());
		Assertions.assertTrue(inputStream.isFinished());
	}

	@Test
	public void streamIsFinishedAfterReadingToEof() throws Exception {
		ServletInputStream inputStream = SokletServletInputStream.fromInputStream(new ByteArrayInputStream(new byte[]{1, 2}));

		Assertions.assertFalse(inputStream.isFinished());
		Assertions.assertEquals(1, inputStream.read());
		Assertions.assertFalse(inputStream.isFinished());
		Assertions.assertEquals(2, inputStream.read());
		Assertions.assertTrue(inputStream.isFinished());
		Assertions.assertEquals(-1, inputStream.read());
		Assertions.assertTrue(inputStream.isFinished());
	}
}

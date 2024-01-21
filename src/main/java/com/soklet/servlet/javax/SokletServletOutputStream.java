/*
 * Copyright 2024 Revetware LLC.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
class SokletServletOutputStream extends ServletOutputStream {
	@Nonnull
	private final ByteArrayOutputStream internalOutputStream;

	public SokletServletOutputStream() {
		this.internalOutputStream = new ByteArrayOutputStream();
	}

	@Nonnull
	byte[] getBytesWrittenToOutputStream() {
		return getInternalOutputStream().toByteArray();
	}

	@Nonnull
	protected ByteArrayOutputStream getInternalOutputStream() {
		return this.internalOutputStream;
	}

	// Implementation of ServletOutputStream methods below:

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public void flush() throws IOException {
		getInternalOutputStream().flush();
	}

	@Override
	public void close() throws IOException {
		// A no-op for ByteArrayOutputStream
	}

	@Override
	public void setWriteListener(@Nullable WriteListener writeListener) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void write(int b) throws IOException {
		getInternalOutputStream().write(b);
	}
}

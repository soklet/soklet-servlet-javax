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

import org.jspecify.annotations.NonNull;

import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Soklet integration implementation of {@link ServletInputStream}.
 *
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public final class SokletServletInputStream extends ServletInputStream {
	@NonNull
	private final InputStream inputStream;
	@NonNull
	private Boolean finished;
	@NonNull
	private Boolean closed;

	@NonNull
	public static SokletServletInputStream fromInputStream(@NonNull InputStream inputStream) {
		requireNonNull(inputStream);
		return new SokletServletInputStream(inputStream);
	}

	private SokletServletInputStream(@NonNull InputStream inputStream) {
		super();
		requireNonNull(inputStream);

		this.inputStream = inputStream;
		this.finished = false;
		this.closed = false;

		if (inputStream instanceof ByteArrayInputStream && ((ByteArrayInputStream) inputStream).available() == 0)
			this.finished = true;
	}

	@NonNull
	private InputStream getInputStream() {
		return this.inputStream;
	}

	@NonNull
	private Boolean getFinished() {
		return this.finished;
	}

	private void setFinished(@NonNull Boolean finished) {
		requireNonNull(finished);
		this.finished = finished;
	}

	@NonNull
	private Boolean getClosed() {
		return this.closed;
	}

	private void setClosed(@NonNull Boolean closed) {
		requireNonNull(closed);
		this.closed = closed;
	}

	private void ensureOpen() throws IOException {
		if (getClosed())
			throw new IOException("Stream is closed");
	}

	private void updateFinishedAfterRead(int bytesRead,
																			 boolean bytesConsumed) {
		if (bytesRead == -1) {
			setFinished(true);
			return;
		}

		if (bytesConsumed && getInputStream() instanceof ByteArrayInputStream
				&& ((ByteArrayInputStream) getInputStream()).available() == 0) {
			setFinished(true);
		}
	}

	// Implementation of ServletInputStream methods below:

	@Override
	public boolean isFinished() {
		return getFinished();
	}

	@Override
	public boolean isReady() {
		return !getClosed();
	}

	@Override
	public int available() throws IOException {
		ensureOpen();
		return getInputStream().available();
	}

	@Override
	public void close() throws IOException {
		if (getClosed())
			return;

		try {
			super.close();
			getInputStream().close();
		} finally {
			setClosed(true);
			setFinished(true);
		}
	}

	@Override
	public void setReadListener(@NonNull ReadListener readListener) {
		requireNonNull(readListener);
		throw new IllegalStateException(format("%s functionality is not supported", ReadListener.class.getSimpleName()));
	}

	@Override
	public int read() throws IOException {
		ensureOpen();
		int data = getInputStream().read();
		updateFinishedAfterRead(data, data != -1);

		return data;
	}

	@Override
	public int read(@NonNull byte[] b,
									int off,
									int len) throws IOException {
		requireNonNull(b);
		ensureOpen();
		int count = getInputStream().read(b, off, len);
		updateFinishedAfterRead(count, count > 0);

		return count;
	}
}

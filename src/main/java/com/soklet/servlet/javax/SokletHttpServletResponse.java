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

import com.soklet.core.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class SokletHttpServletResponse implements HttpServletResponse {
	@Nonnull
	private final List<Cookie> cookies;
	@Nonnull
	private final ServletOutputStream servletOutputStream;
	@Nullable
	private Locale locale;

	public SokletHttpServletResponse() {
		this.cookies = new ArrayList<>();
		this.servletOutputStream = new SokletServletOutputStream();
	}

	@Nonnull
	public Response toResponse() {
		throw new UnsupportedOperationException("TODO");
	}

	@Nonnull
	protected List<Cookie> getCookies() {
		return this.cookies;
	}

	// Implementation of HttpServletResponse methods below:

	@Override
	public void addCookie(@Nullable Cookie cookie) {
		if (cookie != null)
			getCookies().add(cookie);
	}

	@Override
	public boolean containsHeader(String s) {
		return false;
	}

	@Override
	public String encodeURL(String s) {
		return null;
	}

	@Override
	public String encodeRedirectURL(String s) {
		return null;
	}

	@Override
	public String encodeUrl(String s) {
		return null;
	}

	@Override
	public String encodeRedirectUrl(String s) {
		return null;
	}

	@Override
	public void sendError(int i, String s) throws IOException {

	}

	@Override
	public void sendError(int i) throws IOException {

	}

	@Override
	public void sendRedirect(String s) throws IOException {

	}

	@Override
	public void setDateHeader(String s, long l) {

	}

	@Override
	public void addDateHeader(String s, long l) {

	}

	@Override
	public void setHeader(String s, String s1) {

	}

	@Override
	public void addHeader(String s, String s1) {

	}

	@Override
	public void setIntHeader(String s, int i) {

	}

	@Override
	public void addIntHeader(String s, int i) {

	}

	@Override
	public void setStatus(int i) {

	}

	@Override
	public void setStatus(int i, String s) {

	}

	@Override
	public int getStatus() {
		return 0;
	}

	@Override
	public String getHeader(String s) {
		return null;
	}

	@Override
	public Collection<String> getHeaders(String s) {
		return null;
	}

	@Override
	public Collection<String> getHeaderNames() {
		return null;
	}

	@Override
	public String getCharacterEncoding() {
		return null;
	}

	@Override
	public String getContentType() {
		return null;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return this.servletOutputStream;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		return null;
	}

	@Override
	public void setCharacterEncoding(String s) {

	}

	@Override
	public void setContentLength(int i) {

	}

	@Override
	public void setContentLengthLong(long l) {

	}

	@Override
	public void setContentType(String s) {

	}

	@Override
	public void setBufferSize(int i) {

	}

	@Override
	public int getBufferSize() {
		return 0;
	}

	@Override
	public void flushBuffer() throws IOException {

	}

	@Override
	public void resetBuffer() {

	}

	@Override
	public boolean isCommitted() {
		return false;
	}

	@Override
	public void reset() {
		// No-op
	}

	@Override
	public void setLocale(@Nullable Locale locale) {
		this.locale = locale;
	}

	@Override
	public Locale getLocale() {
		return this.locale;
	}
}
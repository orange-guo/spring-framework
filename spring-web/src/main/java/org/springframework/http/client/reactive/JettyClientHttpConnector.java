/*
 * Copyright 2002-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http.client.reactive;

import java.net.URI;
import java.util.function.Function;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.JettyDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

/**
 * {@link ClientHttpConnector} for the Jetty Reactive Streams HttpClient.
 *
 * @author Sebastien Deleuze
 * @since 5.1
 * @see <a href="https://github.com/jetty-project/jetty-reactive-httpclient">Jetty ReactiveStreams HttpClient</a>
 */
public class JettyClientHttpConnector implements ClientHttpConnector {

	private final HttpClient httpClient;

	private JettyDataBufferFactory bufferFactory = new JettyDataBufferFactory();


	/**
	 * Default constructor that creates a new instance of {@link HttpClient}.
	 */
	public JettyClientHttpConnector() {
		this(new HttpClient());
	}

	/**
	 * Constructor with an initialized {@link HttpClient}.
	 */
	public JettyClientHttpConnector(HttpClient httpClient) {
		this(httpClient, null);
	}

	/**
	 * Constructor with an initialized {@link HttpClient} and configures it
	 * with the given {@link JettyResourceFactory}.
	 * @param httpClient the {@link HttpClient} to use
	 * @param resourceFactory the {@link JettyResourceFactory} to use
	 * @since 5.2
	 */
	public JettyClientHttpConnector(HttpClient httpClient, @Nullable JettyResourceFactory resourceFactory) {
		Assert.notNull(httpClient, "HttpClient is required");
		if (resourceFactory != null) {
			httpClient.setExecutor(resourceFactory.getExecutor());
			httpClient.setByteBufferPool(resourceFactory.getByteBufferPool());
			httpClient.setScheduler(resourceFactory.getScheduler());
		}
		this.httpClient = httpClient;
	}


	/**
	 * Set the buffer factory to use.
	 */
	public void setBufferFactory(JettyDataBufferFactory bufferFactory) {
		this.bufferFactory = bufferFactory;
	}


	@Override
	public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
			Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {

		if (!uri.isAbsolute()) {
			return Mono.error(new IllegalArgumentException("URI is not absolute: " + uri));
		}

		if (!this.httpClient.isStarted()) {
			try {
				this.httpClient.start();
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		}

		Request jettyRequest = this.httpClient.newRequest(uri).method(method.toString());
		JettyClientHttpRequest request = new JettyClientHttpRequest(jettyRequest, this.bufferFactory);

		return requestCallback.apply(request).then(execute(request));
	}

	private Mono<ClientHttpResponse> execute(JettyClientHttpRequest request) {
		return Mono.fromDirect(request.toReactiveRequest()
				.response((reactiveResponse, chunkPublisher) -> {
					Flux<DataBuffer> content = Flux.from(chunkPublisher).map(this.bufferFactory::wrap);
					return Mono.just(new JettyClientHttpResponse(reactiveResponse, content));
				}));
	}

}

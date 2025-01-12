/*
  <<<<<<< hkj-error-handling
 * Copyright 2019 Google Inc.
  =======
 * Copyright 2021 Google Inc.
  >>>>>>> master
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
  <<<<<<< hkj-error-handling
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.JsonParser;
import com.google.common.io.CharStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseHttpResponse;
import java.io.IOException;
import java.io.InputStreamReader;

  =======
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.common.io.CharStreams;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * An HTTP client implementation that handles any errors that may occur during HTTP calls, and
 * converts them into an instance of FirebaseException.
 */
  >>>>>>> master
public final class ErrorHandlingHttpClient<T extends FirebaseException> {

  private final HttpRequestFactory requestFactory;
  private final JsonFactory jsonFactory;
  private final HttpErrorHandler<T> errorHandler;

  <<<<<<< hkj-error-handling
  public ErrorHandlingHttpClient(
      FirebaseApp app, HttpErrorHandler<T> errorHandler, RetryConfig retryConfig) {
    this(
        ApiClientUtils.newAuthorizedRequestFactory(app, retryConfig),
        app.getOptions().getJsonFactory(),
        errorHandler);
  }

  public ErrorHandlingHttpClient(HttpRequestFactory requestFactory,
      JsonFactory jsonFactory, HttpErrorHandler<T> errorHandler) {
  =======
  private HttpResponseInterceptor interceptor;

  public ErrorHandlingHttpClient(
      HttpRequestFactory requestFactory,
      JsonFactory jsonFactory,
      HttpErrorHandler<T> errorHandler) {
  >>>>>>> master
    this.requestFactory = checkNotNull(requestFactory, "requestFactory must not be null");
    this.jsonFactory = checkNotNull(jsonFactory, "jsonFactory must not be null");
    this.errorHandler = checkNotNull(errorHandler, "errorHandler must not be null");
  }

  <<<<<<< hkj-error-handling
  public <V> V sendAndParse(HttpRequestInfo requestInfo, Class<V> responseType) throws T {
    HttpResponseInfo responseInfo = this.sendRequest(requestInfo);

    try {
      return this.parseResponse(responseInfo, responseType);
    } finally {
      responseInfo.disconnect();
    }
  }

  private HttpResponseInfo sendRequest(HttpRequestInfo requestInfo) throws T {
    HttpRequest request = newHttpRequest(requestInfo);

    try {
      return new HttpResponseInfo(request.execute());
    } catch (HttpResponseException e) {
      FirebaseHttpResponse response = new FirebaseHttpResponse(e, request);
      throw errorHandler.handleHttpResponseException(e, response);
    } catch (IOException e) {
      throw errorHandler.handleIOException(e);
    }
  }

  private HttpRequest newHttpRequest(HttpRequestInfo requestInfo) throws T {
    try {
      HttpRequest request = requestInfo.newHttpRequest(requestFactory);
      request.setParser(new JsonObjectParser(jsonFactory));
      return request;
    } catch (IOException e) {
      throw errorHandler.handleIOException(e);
    }
  }

  private <V> V parseResponse(HttpResponseInfo responseInfo, Class<V> responseType) throws T {
    try {
      JsonParser parser = jsonFactory.createJsonParser(responseInfo.content);
      return parser.parseAndClose(responseType);
    } catch (IOException e) {
      throw errorHandler.handleParseException(e, responseInfo.toFirebaseHttpResponse());
    }
  }

  private static class HttpResponseInfo {
    private final HttpResponse response;
    private final String content;

    HttpResponseInfo(HttpResponse response) throws IOException {
      this.response = response;
      // Read and buffer the content here. Otherwise if a parse error occurs,
      // we lose the content.
      this.content = CharStreams.toString(
          new InputStreamReader(response.getContent(), response.getContentCharset()));
    }

    void disconnect() {
      ApiClientUtils.disconnectQuietly(response);
    }

    FirebaseHttpResponse toFirebaseHttpResponse() {
      return new FirebaseHttpResponse(response, content);
  =======
  public ErrorHandlingHttpClient<T> setInterceptor(HttpResponseInterceptor interceptor) {
    this.interceptor = interceptor;
    return this;
  }

  /**
   * Sends the given HTTP request to the target endpoint, and parses the response while handling
   * any errors that may occur along the way.
   *
   * @param requestInfo Outgoing request configuration.
   * @param responseType Class to parse the response into.
   * @param <V> Parsed response type.
   * @return Parsed response object.
   * @throws T If any error occurs while making the request.
   */
  public <V> V sendAndParse(HttpRequestInfo requestInfo, Class<V> responseType) throws T {
    IncomingHttpResponse response = send(requestInfo);
    return parse(response, responseType);
  }

  /**
   * Sends the given HTTP request to the target endpoint, and parses the response while handling
   * any errors that may occur along the way. This method can be used when the response should
   * be parsed into an instance of a private or protected class, which cannot be instantiated
   * outside the call-site.
   *
   * @param requestInfo Outgoing request configuration.
   * @param destination Object to parse the response into.
   * @throws T If any error occurs while making the request.
   */
  public void sendAndParse(HttpRequestInfo requestInfo, Object destination) throws T {
    IncomingHttpResponse response = send(requestInfo);
    parse(response, destination);
  }

  public IncomingHttpResponse send(HttpRequestInfo requestInfo) throws T {
    HttpRequest request = createHttpRequest(requestInfo);

    HttpResponse response = null;
    try {
      response = request.execute();
      // Read and buffer the content. Otherwise if a parse error occurs later,
      // we lose the content stream.
      String content = null;
      InputStream stream = response.getContent();
      if (stream != null) {
        // Stream is null when the response body is empty (e.g. 204 No Content responses).
        content = CharStreams.toString(new InputStreamReader(stream, response.getContentCharset()));
      }

      return new IncomingHttpResponse(response, content);
    } catch (HttpResponseException e) {
      throw errorHandler.handleHttpResponseException(e, new IncomingHttpResponse(e, request));
    } catch (IOException e) {
      throw errorHandler.handleIOException(e);
    } finally {
      ApiClientUtils.disconnectQuietly(response);
    }
  }

  public <V> V parse(IncomingHttpResponse response, Class<V> responseType) throws T {
    checkNotNull(responseType, "responseType must not be null");
    try {
      JsonParser parser = jsonFactory.createJsonParser(response.getContent());
      return parser.parseAndClose(responseType);
    } catch (IOException e) {
      throw errorHandler.handleParseException(e, response);
    }
  }

  public void parse(IncomingHttpResponse response, Object destination) throws T {
    try {
      JsonParser parser = jsonFactory.createJsonParser(response.getContent());
      parser.parse(destination);
    } catch (IOException e) {
      throw errorHandler.handleParseException(e, response);
    }
  }

  private HttpRequest createHttpRequest(HttpRequestInfo requestInfo) throws T {
    try {
      return requestInfo.newHttpRequest(requestFactory, jsonFactory)
          .setResponseInterceptor(interceptor);
    } catch (IOException e) {
      // Handle request initialization errors (credential loading and other config errors)
      throw errorHandler.handleIOException(e);
  >>>>>>> master
    }
  }
}

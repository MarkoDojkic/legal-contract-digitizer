package dev.markodojkic.legalcontractdigitizer.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for sending HTTP requests using OkHttp and Jackson.
 * Supports common HTTP verbs (GET, POST, DELETE, PATCH) with optional headers and bodies.
 */
@Component
@RequiredArgsConstructor
public class HttpClientUtil {

	/** Shared OkHttp client with generous timeouts */
	public static final OkHttpClient client = new OkHttpClient.Builder()
			.connectTimeout(60, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(60, TimeUnit.SECONDS)
			.build();

	private final ObjectMapper objectMapper;

	/**
	 * Merges authorization headers with any custom headers and builds OkHttp-compatible headers.
	 *
	 * @param customHeaders additional headers to include
	 * @return OkHttp Headers object
	 */
	private static Headers buildHeaders(HttpHeaders customHeaders) {
		HttpHeaders authHeaders = AuthSession.createAuthHeaders();
		HttpHeaders mergedHeaders = new HttpHeaders();
		mergedHeaders.putAll(authHeaders);
		if (customHeaders != null) {
			mergedHeaders.putAll(customHeaders);
		}

		Headers.Builder builder = new Headers.Builder();
		for (Map.Entry<String, java.util.List<String>> entry : mergedHeaders.entrySet()) {
			for (String value : entry.getValue()) {
				builder.add(entry.getKey(), value);
			}
		}
		return builder.build();
	}

	/**
	 * Internal method to send the HTTP request and parse the response.
	 *
	 * @param request      the OkHttp request
	 * @param responseType expected Java type of the response body
	 * @param <T>          return type
	 * @return ResponseEntity with parsed response body and status code
	 * @throws IOException if the request fails
	 */
	private <T> ResponseEntity<T> sendRequest(Request request, Type responseType) throws IOException {
		try (Response response = client.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : null;
			T bodyObj = null;

			if (responseBody != null && !responseBody.isEmpty() && responseType != Void.class) {
				if (responseType == String.class) {
					bodyObj = (T) responseBody;
				} else {
					// Handle array types separately
					if (responseType instanceof Class && ((Class<?>) responseType).isArray()) {
						bodyObj = objectMapper.readValue(responseBody,
								objectMapper.getTypeFactory().constructArrayType(((Class<?>) responseType).getComponentType()));
					} else {
						// For regular objects, use TypeReference or the normal responseType
						bodyObj = objectMapper.readValue(responseBody, objectMapper.constructType(responseType));
					}
				}
			}

			return new ResponseEntity<>(bodyObj, org.springframework.http.HttpStatus.valueOf(response.code()));
		}
	}

	/**
	 * Sends a GET request to the given URL with optional headers.
	 *
	 * @param url          endpoint URL
	 * @param headers      optional HTTP headers
	 * @param responseType expected Java type of the response body
	 * @param <T>          response type
	 * @return ResponseEntity with parsed body
	 * @throws IOException if the request fails
	 */
	public <T> ResponseEntity<T> get(String url, HttpHeaders headers, Type responseType) throws IOException {
		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.get()
				.build();
		return sendRequest(request, responseType);
	}

	/**
	 * Sends a DELETE request to the given URL with optional headers.
	 *
	 * @param url          endpoint URL
	 * @param headers      optional HTTP headers
	 * @param responseType expected Java type of the response body
	 * @param <T>          response type
	 * @return ResponseEntity with parsed body
	 * @throws IOException if the request fails
	 */
	public <T> ResponseEntity<T> delete(String url, HttpHeaders headers, Type responseType) throws IOException {
		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.delete()
				.build();
		return sendRequest(request, responseType);
	}

	/**
	 * Sends a POST request with a JSON body (or form) to the given URL.
	 *
	 * @param url          endpoint URL
	 * @param headers      optional HTTP headers
	 * @param body         request body (can be a POJO or FormBody)
	 * @param responseType expected Java type of the response body
	 * @param <T>          response type
	 * @return ResponseEntity with parsed body
	 * @throws IOException if the request fails
	 */
	public <T> ResponseEntity<T> post(String url, HttpHeaders headers, Object body, Type responseType) throws IOException {
		RequestBody requestBody = (body != null)
				? (body instanceof FormBody ? (FormBody) body : RequestBody.create(objectMapper.writeValueAsString(body), MediaType.get("application/json; charset=utf-8")))
				: RequestBody.create(new byte[0]);

		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.post(requestBody)
				.build();

		return sendRequest(request, responseType);
	}

	/**
	 * Sends a PATCH request with a JSON body (or form) to the given URL.
	 *
	 * @param url          endpoint URL
	 * @param headers      optional HTTP headers
	 * @param body         request body (can be a POJO or FormBody)
	 * @param responseType expected Java type of the response body
	 * @param <T>          response type
	 * @return ResponseEntity with parsed body
	 * @throws IOException if the request fails
	 */
	public <T> ResponseEntity<T> patch(String url, HttpHeaders headers, Object body, Type responseType) throws IOException {
		RequestBody requestBody = (body != null)
				? (body instanceof FormBody ? (FormBody) body : RequestBody.create(objectMapper.writeValueAsString(body), MediaType.get("application/json; charset=utf-8")))
				: RequestBody.create(new byte[0]);

		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.patch(requestBody)
				.build();

		return sendRequest(request, responseType);
	}

	/**
	 * Sends a POST request with a file upload (multipart/form-data) to the given URL.
	 *
	 * @param url           endpoint URL
	 * @param headers       optional HTTP headers
	 * @param formFieldName name of the form field (e.g., "file")
	 * @param file          the file to upload
	 * @param responseType  expected Java type of the response body
	 * @param <T>           response type
	 * @return ResponseEntity with parsed body
	 * @throws IOException if the request fails
	 */
	public <T> ResponseEntity<T> postWithFile(
			String url,
			HttpHeaders headers,
			String formFieldName,
			File file,
			Type responseType
	) throws IOException {
		MediaType mediaType = MediaType.parse("application/octet-stream");
		RequestBody fileBody = RequestBody.create(file, mediaType);

		MultipartBody multipartBody = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart(formFieldName, file.getName(), fileBody)
				.build();

		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.post(multipartBody)
				.build();

		return sendRequest(request, responseType);
	}
}
package dev.markodojkic.legalcontractdigitizer.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class HttpClientUtil {

	private static final OkHttpClient client = new OkHttpClient.Builder()
			.connectTimeout(60, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(60, TimeUnit.SECONDS)
			.build();

	private final ObjectMapper objectMapper;

	@Autowired
	public HttpClientUtil(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

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

	// Generic request helper (internal)
	private <T> ResponseEntity<T> sendRequest(
			Request request,
			Type responseType
	) throws IOException {
		try (Response response = client.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : null;
			T bodyObj = null;

			if (responseBody != null && !responseBody.isEmpty() && responseType != Void.class) {
				if (responseType == String.class) {
					bodyObj = (T) responseBody;
				} else {
					// If the responseType is an array type, use TypeReference to handle it correctly
					if (responseType instanceof Class && ((Class<?>) responseType).isArray()) {
						// Deserialize array
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

	// GET
	public <T> ResponseEntity<T> get(
			String url,
			HttpHeaders headers,
			Type responseType
	) throws IOException {
		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.get()
				.build();

		return sendRequest(request, responseType);
	}

	// DELETE
	public <T> ResponseEntity<T> delete(
			String url,
			HttpHeaders headers,
			Type responseType
	) throws IOException {
		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.delete()
				.build();

		return sendRequest(request, responseType);
	}

	// POST with JSON body (body can be null)
	public <T> ResponseEntity<T> post(
			String url,
			HttpHeaders headers,
			Object body,
			Type responseType
	) throws IOException {
		RequestBody requestBody;
		if (body != null) {
			String json = objectMapper.writeValueAsString(body);
			requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
		} else {
			requestBody = RequestBody.create(new byte[0]);
		}

		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.post(requestBody)
				.build();

		return sendRequest(request, responseType);
	}

	// PATCH with optional JSON body (null body sends empty body)
	public <T> ResponseEntity<T> patch(
			String url,
			HttpHeaders headers,
			Object body,
			Type responseType
	) throws IOException {
		RequestBody requestBody;
		if (body != null) {
			String json = objectMapper.writeValueAsString(body);
			requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
		} else {
			requestBody = RequestBody.create(new byte[0]);
		}

		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.patch(requestBody)
				.build();

		return sendRequest(request, responseType);
	}

	// POST with File upload (multipart/form-data)
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
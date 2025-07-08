package dev.markodojkic.legalcontractdigitizer.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.markodojkic.legalcontractdigitizer.model.ContractPartiesAddressDataResponseDTO;
import dev.markodojkic.legalcontractdigitizer.model.GasEstimateResponseDTO;
import dev.markodojkic.legalcontractdigitizer.model.WalletInfo;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
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
	 * Sends a POST request with a JSON body to the given URL.
	 * If the body is {@code null}, an empty JSON string is sent.
	 *
	 * @param url          the endpoint URL
	 * @param headers      optional HTTP headers
	 * @param body         the request body (can be {@code null} or any serializable object)
	 * @param responseType the expected Java type of the response body
	 * @param <T>          the response type
	 * @return ResponseEntity containing the parsed response body and status
	 * @throws IOException if the request fails
	 */
	public <T> ResponseEntity<T> post(String url, HttpHeaders headers, Object body, Type responseType) throws IOException {
		String json = (body == null) ? "" : objectMapper.writeValueAsString(body);
		RequestBody requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

		Request request = new Request.Builder()
				.url(url)
				.headers(buildHeaders(headers))
				.post(requestBody)
				.build();

		return sendRequest(request, responseType);
	}

	/**
	 * Sends a PATCH request with a JSON body to the given URL.
	 * If the body is {@code null}, an empty JSON string is sent.
	 *
	 * @param url          the endpoint URL
	 * @param headers      optional HTTP headers
	 * @param body         the request body (can be {@code null} or any serializable object)
	 * @param responseType the expected Java type of the response body
	 * @param <T>          the response type
	 * @return ResponseEntity containing the parsed response body and status
	 * @throws IOException if the request fails
	 */
	public <T> ResponseEntity<T> patch(String url, HttpHeaders headers, Object body, Type responseType) throws IOException {
		String json = (body == null) ? "" : objectMapper.writeValueAsString(body);
		RequestBody requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

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
	public <T> ResponseEntity<T> postWithFile(String url, HttpHeaders headers, String formFieldName, File file, Type responseType) throws IOException {
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

	/**
			* Merges authorization headers with any custom headers and builds OkHttp-compatible headers.
			*
			* @param customHeaders additional headers to include
	 * @return OkHttp Headers object
	 */
	private static Headers buildHeaders(HttpHeaders customHeaders) {
		HttpHeaders mergedHeaders = new HttpHeaders();
		mergedHeaders.putAll(AuthSession.createAuthHeaders());
		if (customHeaders != null) mergedHeaders.putAll(customHeaders);

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
	@SuppressWarnings("unchecked")
	private <T> ResponseEntity<T> sendRequest(Request request, Type responseType) throws IOException {
		try (Response response = client.newCall(request).execute()) {

			String responseBody = response.body().string();
			T bodyObj = null;

			if(response.code() == 401) responseBody = unauthorizedRequestBody(responseType);

			if (responseBody != null && !responseBody.isEmpty()) {
				if (responseType == String.class) bodyObj = (T) responseBody;
				else {
					// Handle array types separately
					if (responseType instanceof Class && ((Class<?>) responseType).isArray()) bodyObj = objectMapper.readValue(responseBody,
								objectMapper.getTypeFactory().constructArrayType(((Class<?>) responseType).getComponentType()));
					else bodyObj = objectMapper.readValue(responseBody, objectMapper.constructType(responseType));
				}
			}

			return new ResponseEntity<>(bodyObj, org.springframework.http.HttpStatus.valueOf(response.code()));
		}
	}

	private String unauthorizedRequestBody (Type responseType) throws IOException {
		String unauthorizedMessage = "Unauthorized access. Please login again.";
        return switch (responseType.getTypeName()) {
            case "dev.markodojkic.legalcontractdigitizer.util.Either<dev.markodojkic.legalcontractdigitizer.model.DigitalizedContract, java.lang.String>",
                 "dev.markodojkic.legalcontractdigitizer.util.Either<java.util.List<dev.markodojkic.legalcontractdigitizer.model.DigitalizedContract>, java.lang.String>" ->
                    objectMapper.writeValueAsString(Either.right(unauthorizedMessage));
            case "dev.markodojkic.legalcontractdigitizer.model.GasEstimateResponseDTO" ->
                    objectMapper.writeValueAsString(new GasEstimateResponseDTO(unauthorizedMessage, null, null));
            case "dev.markodojkic.legalcontractdigitizer.model.ContractPartiesAddressDataResponseDTO" ->
                    objectMapper.writeValueAsString(new ContractPartiesAddressDataResponseDTO(Map.of("", unauthorizedMessage)));
            case "dev.markodojkic.legalcontractdigitizer.model.WalletInfo" ->
                    objectMapper.writeValueAsString(new WalletInfo(unauthorizedMessage, null, null, null));
            case "java.util.List<dev.markodojkic.legalcontractdigitizer.model.WalletInfo>" ->
                    objectMapper.writeValueAsString(Collections.singletonList(new WalletInfo(unauthorizedMessage, null, null, null)));
            default -> unauthorizedMessage;
        };
	}
}
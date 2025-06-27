package dev.markodojkic.legalcontractdigitizer.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.markodojkic.legalcontractdigitizer.exception.InvalidFunctionCallException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.AbiDefinition;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Web3jUtil {

	@Autowired
	private static ObjectMapper objectMapper;

	private Web3jUtil() {
		throw new UnsupportedOperationException("Utility class should not be instantiated");
	}

	public static List<Type> convertToAbiTypes(List<Object> constructorParams) {
		List<Type> abiTypes = new ArrayList<>();

		for (Object param : constructorParams) {
			switch (param) {
				case String strParam -> {
					if (strParam.matches("^0x[a-fA-F0-9]{40}$")) {
						// Ethereum address
						abiTypes.add(new Address(strParam));
					} else if (strParam.matches("^\\d+$")) {
						// Numeric string
						abiTypes.add(new Uint256(new BigInteger(strParam)));
					} else {
						// Fallback to string
						abiTypes.add(new Utf8String(strParam));
					}
				}
				case Number num -> abiTypes.add(new Uint256(BigInteger.valueOf(num.longValue())));
				default -> throw new IllegalArgumentException("Unsupported constructor parameter type: " + param);
			}
		}

		return abiTypes;
	}

	/**
	 * Parses ABI JSON and returns list of all definitions.
	 */
	public static List<AbiDefinition> parseAbi(String abiJson) {
		try {
			return objectMapper.readValue(abiJson, new TypeReference<>() {});
		} catch (Exception e) {
			throw new InvalidFunctionCallException("Failed to parse ABI: " + e.getMessage(), e);
		}
	}

	/**
	 * Finds a function definition in the ABI by name.
	 */
	public static AbiDefinition findFunctionDefinition(String abiJson, String functionName) {
		return parseAbi(abiJson).stream()
				.filter(abi -> "function".equals(abi.getType()) && functionName.equals(abi.getName()))
				.findFirst()
				.orElse(null);
	}
}
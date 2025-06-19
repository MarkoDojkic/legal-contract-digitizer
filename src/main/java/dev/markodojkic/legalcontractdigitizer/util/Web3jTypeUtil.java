package dev.markodojkic.legalcontractdigitizer.util;

import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Web3jTypeUtil {

	/**
	 * Convert a List of Strings and/or BigIntegers to a List of ABI Types.
	 * You can extend this to support other types as needed.
	 */
	public static List<Type> convertToAbiTypes(List<Object> params) {
		List<Type> abiTypes = new ArrayList<>();

		for (Object param : params) {
			if (param instanceof String) {
				abiTypes.add(new Utf8String((String) param));
			} else if (param instanceof Integer) {
				abiTypes.add(new Uint256(BigInteger.valueOf((Integer) param)));
			} else if (param instanceof Long) {
				abiTypes.add(new Uint256(BigInteger.valueOf((Long) param)));
			} else if (param instanceof BigInteger) {
				abiTypes.add(new Uint256((BigInteger) param));
			} else if (param instanceof Boolean) {
				abiTypes.add(new Bool((Boolean) param));
			} else {
				throw new IllegalArgumentException("Unsupported parameter type: " + param.getClass());
			}
		}

		return abiTypes;
	}
}
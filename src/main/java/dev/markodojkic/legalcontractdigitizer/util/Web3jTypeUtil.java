package dev.markodojkic.legalcontractdigitizer.util;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Web3jTypeUtil {

	public static List<Type> convertToAbiTypes(List<Object> constructorParams) {
		List<Type> abiTypes = new ArrayList<>();

		for (Object param : constructorParams) {
			if (param instanceof String strParam) {
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
			} else if (param instanceof Number num) {
				abiTypes.add(new Uint256(BigInteger.valueOf(num.longValue())));
			} else {
				throw new IllegalArgumentException("Unsupported constructor parameter type: " + param);
			}
		}

		return abiTypes;
	}
}
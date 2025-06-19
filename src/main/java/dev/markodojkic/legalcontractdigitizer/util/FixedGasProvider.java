package dev.markodojkic.legalcontractdigitizer.util;

import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;

public class FixedGasProvider implements ContractGasProvider {
    private final BigInteger gasPrice;
    private final BigInteger gasLimit;

    public FixedGasProvider(BigInteger gasPrice, BigInteger gasLimit) {
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
    }

    @Override
    public BigInteger getGasPrice(String contractFunc) {
        return gasPrice;
    }

    @Override
    public BigInteger getGasPrice() {
        return gasPrice;
    }

    @Override
    public BigInteger getGasLimit(String contractFunc) {
        return gasLimit;
    }

    @Override
    public BigInteger getGasLimit() {
        return gasLimit;
    }
}

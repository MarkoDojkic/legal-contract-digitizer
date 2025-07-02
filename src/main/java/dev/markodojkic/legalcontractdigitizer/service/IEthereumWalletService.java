package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.exception.WalletNotFoundException;
import dev.markodojkic.legalcontractdigitizer.model.WalletInfo;
import org.web3j.crypto.Credentials;

import java.util.List;

public interface IEthereumWalletService {
	WalletInfo createWallet(String label);
	List<WalletInfo> listWallets();
	Credentials loadCredentials(String address) throws WalletNotFoundException;
}
package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.dto.WalletInfo;
import org.web3j.crypto.Credentials;

import java.util.List;

public interface IEthereumWalletService {
	WalletInfo createWallet(String label);
	List<WalletInfo> listWallets();
	Credentials loadCredentials(String address);
}
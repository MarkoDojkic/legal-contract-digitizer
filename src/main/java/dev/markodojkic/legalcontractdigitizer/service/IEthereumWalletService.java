package dev.markodojkic.legalcontractdigitizer.service;

import dev.markodojkic.legalcontractdigitizer.exception.WalletNotFoundException;
import dev.markodojkic.legalcontractdigitizer.model.WalletInfo;
import org.web3j.crypto.Credentials;

import java.util.List;

/**
 * Service interface for managing Ethereum wallets.
 */
public interface IEthereumWalletService {

	/**
	 * Creates a new wallet with the given label.
	 *
	 * @param label the user-friendly label for the wallet
	 * @return information about the created wallet
	 */
	WalletInfo createWallet(String label);

	/**
	 * Lists all wallets available for the current user.
	 *
	 * @return list of wallet information
	 */
	List<WalletInfo> listWallets();

	/**
	 * Loads the credentials for a wallet given its Ethereum address.
	 *
	 * @param address the Ethereum address of the wallet
	 * @return the loaded credentials
	 * @throws WalletNotFoundException if the wallet for the given address is not found
	 */
	Credentials loadCredentials(String address) throws WalletNotFoundException;
}
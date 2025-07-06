package dev.markodojkic.legalcontractdigitizer.service.impl;

import dev.markodojkic.legalcontractdigitizer.exception.EthereumConnectionException;
import dev.markodojkic.legalcontractdigitizer.model.WalletInfo;
import dev.markodojkic.legalcontractdigitizer.exception.WalletCreationException;
import dev.markodojkic.legalcontractdigitizer.exception.WalletNotFoundException;
import dev.markodojkic.legalcontractdigitizer.service.IEthereumService;
import dev.markodojkic.legalcontractdigitizer.service.IEthereumWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
@Slf4j
@RequiredArgsConstructor
public class EthereumWalletServiceImpl implements IEthereumWalletService {

	private static final String KEYSTORE_DIR = "ethWallets", WALLETS = "wallets";
	// Regex pattern to match files like "MyWallet-0x1234567890abcdef1234567890abcdef12345678"
	private static final Pattern WALLET_FILENAME_PATTERN = Pattern.compile("([a-zA-Z0-9\\-_]+)-([0-9a-fA-F]{40}).json");

	@Value("${ethereum.walletKeystore.password}")
	private String walletKeystorePassword;

	private final Cache<String, List<WalletInfo>> walletsCache = Caffeine.newBuilder().maximumSize(1).build(); // In-memory cache for wallets list
	private final IEthereumService ethereumService;

	private String lastDirHash;

	@Override
	public WalletInfo createWallet(String label) {
		try {
			File dir = new File(KEYSTORE_DIR);
			if (!dir.exists() && !dir.mkdirs()) throw new IOException("Failed to create keystore directory: " + dir.getAbsolutePath());

			// Generate wallet file and load credentials
			File walletFile = new File(dir, WalletUtils.generateNewWalletFile(walletKeystorePassword, dir, false));
			Credentials credentials = WalletUtils.loadCredentials(walletKeystorePassword, walletFile);

			// Rename file to the format "label-address.json"
			File renamedFile = new File(dir, label + "-" + credentials.getAddress() + ".json");
			if (!renamedFile.toPath().normalize().startsWith(dir.toPath().normalize()) || !walletFile.renameTo(renamedFile)) throw new WalletCreationException("Failed to rename wallet file to the correct format.");

			WalletInfo wallet = new WalletInfo(label, "0x" + credentials.getAddress(), ethereumService.getBalance(credentials.getAddress()), renamedFile.getName());
			updateCache(wallet);

			return wallet;
		} catch (Exception e) {
			throw new WalletCreationException("Failed to create wallet:\n" + e.getLocalizedMessage());
		}
	}

	@Override
	public List<WalletInfo> listWallets() {
		// If directory content has changed, reload wallets; otherwise, use cache
		if (isDirectoryModified()) loadWalletsFromDirectory();
		return walletsCache.getIfPresent(WALLETS);
	}

	@Override
	public Credentials loadCredentials(String address) throws WalletNotFoundException {
		List<WalletInfo> wallets = walletsCache.getIfPresent(WALLETS);
		if (wallets == null) throw new WalletNotFoundException("Requested credentials not found");
		return wallets.stream()
				.filter(wallet -> wallet.address().equalsIgnoreCase(address))
				.findFirst()
				.map(wallet -> {
					try {
						return WalletUtils.loadCredentials(walletKeystorePassword, new File(KEYSTORE_DIR, wallet.keystoreFile()));
					} catch (Exception e) {
						throw new WalletNotFoundException("Requested credentials failed to load");
					}
				})
				.orElseThrow(() -> new WalletNotFoundException("Wallet not found for address: " + address));
	}

	private boolean isDirectoryModified() {
		File dir = new File(KEYSTORE_DIR);
		if (!dir.exists() || !dir.isDirectory()) return false;

		// Generate a hash of the directory's contents (file names)
		String currentDirHash = generateDirectoryHash(dir);

		// Compare the current directory hash with the stored one
		if (!currentDirHash.equals(lastDirHash)) {
			lastDirHash = currentDirHash;
			return true;  // Directory has changed
		}
		return false;  // No change detected
	}

	private String generateDirectoryHash(File dir) {
		// List and hash all wallet filenames in the directory
		List<String> currentFilenames = Arrays.stream(Objects.requireNonNull(dir.listFiles()))
				.filter(file -> file.isFile() && WALLET_FILENAME_PATTERN.matcher(file.getName()).matches())
				.map(File::getName)
				.sorted().toList();

		return String.join(",", currentFilenames);
	}

	private void loadWalletsFromDirectory() {
		File dir = new File(KEYSTORE_DIR);
		if (!dir.exists() || !dir.isDirectory()) return;

		List<WalletInfo> loadedWallets = Arrays.stream(Objects.requireNonNull(dir.listFiles()))
				.filter(file -> file.isFile() && WALLET_FILENAME_PATTERN.matcher(file.getName()).matches())
				.map(file -> {
					Matcher matcher = WALLET_FILENAME_PATTERN.matcher(file.getName());
					if (matcher.matches()) {
						String label = matcher.group(1);
						String address = "0x" + matcher.group(2);
						BigDecimal balance = BigDecimal.valueOf(-1);
						try {
							balance = ethereumService.getBalance(address);
						} catch (EthereumConnectionException e) {
							log.error("Cannot get balance for wallet, will fallback to -1.0 Sepolia ETH", e);
						}
						return new WalletInfo(label, address, balance, file.getName());
					}
					return null;
				})
				.filter(Objects::nonNull).toList();

		walletsCache.put(WALLETS, loadedWallets);
	}

	private void updateCache(WalletInfo wallet) {
		List<WalletInfo> wallets = walletsCache.getIfPresent(WALLETS);
		if (wallets == null) wallets = new ArrayList<>();
		wallets.add(wallet);
		walletsCache.put(WALLETS, wallets);
	}
}
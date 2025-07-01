package dev.markodojkic.legalcontractdigitizer.service.impl;

import dev.markodojkic.legalcontractdigitizer.dto.WalletInfo;
import dev.markodojkic.legalcontractdigitizer.exception.WalletCreationException;
import dev.markodojkic.legalcontractdigitizer.exception.WalletNotFoundException;
import dev.markodojkic.legalcontractdigitizer.service.IEthereumWalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
@Slf4j
public class EthereumWalletServiceImpl implements IEthereumWalletService {

	private static final String KEYSTORE_DIR = "ethWallets";
	// Regex pattern to match files like "MyWallet-0x1234567890abcdef1234567890abcdef12345678"
	private static final Pattern WALLET_FILENAME_PATTERN = Pattern.compile("([a-zA-Z0-9\\-_]+)-([0-9a-fA-F]{40}).json");

	@Value("${ethereum.walletKeystore.password}")
	private String walletKeystorePassword;

	// In-memory cache for wallets list
	private Cache<String, List<WalletInfo>> walletsCache = Caffeine.newBuilder()
			.expireAfterWrite(60, TimeUnit.MINUTES)  // Cache expires after 60 minutes
			.maximumSize(1)  // Only store one wallet list in cache
			.build();

	private long lastModifiedTime = 0;  // Track last modified time of the directory

	@Override
	public WalletInfo createWallet(String label) {
		try {
			File dir = new File(KEYSTORE_DIR);
			dir.mkdirs();

			// Generate the wallet file (but don't worry about the file name yet)
			String fileName = WalletUtils.generateNewWalletFile(walletKeystorePassword, dir, false);
			File walletFile = new File(dir, fileName);

			// Load the credentials from the generated file
			Credentials credentials = WalletUtils.loadCredentials(walletKeystorePassword, walletFile);

			// Build the new file name in "label-address" format
			String newFileName = label + "-" + credentials.getAddress() + ".json";

			// Rename the file to the desired format
			File renamedFile = new File(dir, newFileName);
			if (!walletFile.renameTo(renamedFile)) {
				throw new WalletCreationException("Failed to rename wallet file to the correct format.", null);
			}

			// Create WalletInfo object with the new filename and address
			WalletInfo wallet = new WalletInfo(label, "0x" + credentials.getAddress(), BigDecimal.ZERO, newFileName);

			// Update cache
			loadWalletsFromDirectory();

			return wallet;
		} catch (Exception e) {
			throw new WalletCreationException("Failed to create wallet", e);
		}
	}

	@Override
	public List<WalletInfo> listWallets() {
		// Check cache first and only load from disk if needed
		if (walletsCache.getIfPresent("wallets") != null && !isDirectoryModified()) {
			return walletsCache.getIfPresent("wallets");
		}

		// If no cache or directory has changed, reload wallets from disk
		loadWalletsFromDirectory();
		return walletsCache.getIfPresent("wallets");
	}

	private boolean isDirectoryModified() {
		File dir = new File(KEYSTORE_DIR);
		if (!dir.exists() || !dir.isDirectory()) {
			return false;  // Directory doesn't exist or isn't valid
		}

		// Check if the last modified time of any file has changed
		long latestModifiedTime = 0;
		File[] files = dir.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isFile() && WALLET_FILENAME_PATTERN.matcher(file.getName()).matches()) {
					long fileModifiedTime = file.lastModified();
					if (fileModifiedTime > latestModifiedTime) {
						latestModifiedTime = fileModifiedTime;
					}
				}
			}
		}

		// Compare with the last cached modification time
		if (latestModifiedTime > lastModifiedTime) {
			lastModifiedTime = latestModifiedTime;
			return true;  // Directory has been modified
		}
		return false;  // No modification
	}

	private void loadWalletsFromDirectory() {
		File dir = new File(KEYSTORE_DIR);
		if (!dir.exists() || !dir.isDirectory()) {
			return;  // Return if the directory doesn't exist or isn't a directory
		}

		File[] files = dir.listFiles();
		if (files == null) {
			return; // Return if no files exist in the directory
		}

		List<WalletInfo> loadedWallets = new ArrayList<>();
		for (File file : files) {
			if (file.isFile() && WALLET_FILENAME_PATTERN.matcher(file.getName()).matches()) {
				// Extract label and address from filename
				Matcher matcher = WALLET_FILENAME_PATTERN.matcher(file.getName());
				if (matcher.matches()) {
					String label = matcher.group(1);  // Label is the first part of the filename
					String address = "0x" + matcher.group(2);  // Address is the second part

					try {
						loadedWallets.add(new WalletInfo(label, address, BigDecimal.ZERO, file.getName()));
					} catch (Exception e) {
						log.error("Failed to load wallet from file: {}", file.getName());
						loadedWallets.add(new WalletInfo("Failed to load wallet from file: " + file.getName(), null, null, file.getName()));
					}
				}
			}
		}

		// Cache the loaded wallets
		walletsCache.put("wallets", loadedWallets);
	}

	@Override
	public Credentials loadCredentials(String address) {
		return walletsCache.getIfPresent("wallets").stream()
				.filter(w -> w.getAddress().equalsIgnoreCase(address))
				.findFirst()
				.map(w -> {
					try {
						return WalletUtils.loadCredentials(walletKeystorePassword, new File(KEYSTORE_DIR, w.getKeystoreFile()));
					} catch (Exception e) {
						throw new WalletCreationException("Failed to load credentials", e);
					}
				})
				.orElseThrow(() -> new WalletNotFoundException(address));
	}
}
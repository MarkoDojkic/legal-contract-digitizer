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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
@Slf4j
@RequiredArgsConstructor
public class EthereumWalletServiceImpl implements IEthereumWalletService {

	private static final String KEYSTORE_DIR = "ethWallets", WALLETS = "wallets";
	private static final Pattern WALLET_FILENAME_PATTERN = Pattern.compile("(.*)-([0-9a-fA-F]{40}).json");

	@Value("${ethereum.walletKeystore.password}")
	private String walletKeystorePassword;

	private final Cache<String, List<WalletInfo>> walletsCache = Caffeine.newBuilder().maximumSize(1).build();
	private final IEthereumService ethereumService;

	private String lastDirHash;

	@Override
	public WalletInfo createWallet(String label) {
		try {
			Path keystoreDir = Paths.get(System.getProperty("user.home"), "dev.markodojkic", "legal_contract_digitizer", "1.0.0").resolve(KEYSTORE_DIR);
			String walletFilename = WalletUtils.generateNewWalletFile(walletKeystorePassword, keystoreDir.toFile(), false);
			Path walletFile = keystoreDir.resolve(walletFilename);

			Credentials credentials = WalletUtils.loadCredentials(walletKeystorePassword, walletFile.toFile());

			// Rename wallet file
			Path renamedFile = keystoreDir.resolve(label + "-" + credentials.getAddress() + ".json");
			if (!walletFile.getParent().equals(renamedFile.getParent()) || !Files.move(walletFile, renamedFile, StandardCopyOption.REPLACE_EXISTING).toFile().exists()) throw new WalletCreationException("Failed to rename wallet file to the correct format.");

			WalletInfo wallet = new WalletInfo(label, "0x" + credentials.getAddress(), ethereumService.getBalance(credentials.getAddress()), renamedFile.getFileName().toString());
			updateCache(wallet);

			return wallet;
		} catch (Exception e) {
			throw new WalletCreationException("Failed to create wallet:\n" + e.getLocalizedMessage());
		}
	}

	@Override
	public List<WalletInfo> listWallets() {
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
						return WalletUtils.loadCredentials(walletKeystorePassword, Paths.get(System.getProperty("user.home"), "dev.markodojkic", "legal_contract_digitizer", "1.0.0").resolve(KEYSTORE_DIR).resolve(wallet.keystoreFile()).toFile());
					} catch (Exception e) {
						throw new WalletNotFoundException("Requested credentials failed to load");
					}
				})
				.orElseThrow(() -> new WalletNotFoundException("Wallet not found for address: " + address));
	}

	private boolean isDirectoryModified() {
		Path dir = Paths.get(System.getProperty("user.home"), "dev.markodojkic", "legal_contract_digitizer", "1.0.0").resolve(KEYSTORE_DIR);
		if (!Files.exists(dir) || !Files.isDirectory(dir)) return false;

		String currentDirHash = generateDirectoryHash(dir);

		if (!currentDirHash.equals(lastDirHash)) {
			lastDirHash = currentDirHash;
			return true;
		}
		return false;
	}

	private String generateDirectoryHash(Path dir) {
		try (Stream<Path> paths = Files.walk(dir)) {
			return paths
					.filter(Files::isRegularFile)
					.map(path -> path.getFileName().toString())
					.filter(filename -> WALLET_FILENAME_PATTERN.matcher(filename).matches())
					.sorted()
					.reduce("", (acc, filename) -> acc + filename);
		} catch (IOException e) {
			log.error("Error reading wallet directory", e);
			return "";
		}
	}

	private void loadWalletsFromDirectory() {
		Path dir = Paths.get(System.getProperty("user.home"), "dev.markodojkic", "legal_contract_digitizer", "1.0.0").resolve(KEYSTORE_DIR);
		if (!Files.exists(dir) || !Files.isDirectory(dir)) return;

		List<WalletInfo> loadedWallets = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path file : stream) {
				if (Files.isRegularFile(file) && WALLET_FILENAME_PATTERN.matcher(file.getFileName().toString()).matches()) {
					Matcher matcher = WALLET_FILENAME_PATTERN.matcher(file.getFileName().toString());
					if (matcher.matches()) {
						String label = matcher.group(1);
						String address = "0x" + matcher.group(2);
						BigDecimal balance = BigDecimal.valueOf(-1);
						try {
							balance = ethereumService.getBalance(address);
						} catch (EthereumConnectionException e) {
							log.error("Cannot get balance for wallet, will fallback to -1.0 ETH", e);
						}
						loadedWallets.add(new WalletInfo(label, address, balance, file.getFileName().toString()));
					}
				}
			}
		} catch (IOException e) {
			log.error("Error loading wallets from directory", e);
		}

		walletsCache.put(WALLETS, loadedWallets);
	}

	private void updateCache(WalletInfo wallet) {
		List<WalletInfo> wallets = walletsCache.getIfPresent(WALLETS);
		if (wallets == null) wallets = new ArrayList<>();
		wallets.add(wallet);
		walletsCache.put(WALLETS, wallets);
	}
}
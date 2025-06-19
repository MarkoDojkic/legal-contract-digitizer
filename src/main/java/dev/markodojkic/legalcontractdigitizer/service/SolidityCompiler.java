package dev.markodojkic.legalcontractdigitizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.markodojkic.legalcontractdigitizer.dto.CompilationResultDTO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SolidityCompiler {

	public CompilationResultDTO compile(String soliditySource) throws IOException, InterruptedException {
		Path sourceFile = Files.createTempFile("contract", ".sol");
		Files.writeString(sourceFile, soliditySource);

		ProcessBuilder pb = new ProcessBuilder(
				"solc",
				"--combined-json", "abi,bin",
				sourceFile.toAbsolutePath().toString()
		);
		Process process = pb.start();

		String output = new String(process.getInputStream().readAllBytes());
		int exitCode = process.waitFor();

		if (exitCode != 0) {
			String err = new String(process.getErrorStream().readAllBytes());
			throw new RuntimeException("Solidity compilation failed: " + err);
		}

		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(output);
		JsonNode contractsNode = root.path("contracts");
		if (contractsNode.isEmpty()) {
			throw new RuntimeException("No contracts found in compilation output");
		}

		// Get first contract (assume single contract in file)
		String contractKey = contractsNode.fieldNames().next();
		JsonNode contractNode = contractsNode.get(contractKey);

		String abi = contractNode.get("abi").toString();
		String bin = contractNode.get("bin").asText();

		Files.deleteIfExists(sourceFile);

		return CompilationResultDTO.builder().abi(abi).bin(bin).build();
	}
}

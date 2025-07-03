package dev.markodojkic.legalcontractdigitizer.model;

/**
 * Represents the result of a smart contract compilation.
 *
 * @param bin The compiled contract bytecode (binary).
 * @param abi The contract's Application Binary Interface (ABI).
 */
public record CompilationResult(String bin, String abi) { }
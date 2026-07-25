package com.hilo.rewards.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Service
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final String wrapperAddress;

    public BlockchainService(Web3j web3j, Credentials credentials, String wrapperAddress) {
        this.web3j = web3j;
        this.credentials = credentials;
        this.wrapperAddress = wrapperAddress;
    }

    public String processReward(String rewardId, List<String> recipients, BigDecimal amountPerRecipient) throws Exception {
        BigInteger amountWei = amountPerRecipient.toBigInteger();

        // Encode function call: processReward(bytes32, address[], uint256)
        String encodedFunction = encodeProcessReward(rewardId, recipients, amountWei);

        // Get nonce
        BigInteger nonce = web3j.ethGetTransactionCount(
            credentials.getAddress(),
            DefaultBlockParameterName.LATEST
        ).send().getTransactionCount();

        // Estimate gas
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        org.web3j.protocol.core.methods.request.Transaction tx =
            org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                credentials.getAddress(),
                nonce,
                gasPrice,
                BigInteger.valueOf(3_100_000), // gas limit
                wrapperAddress,
                encodedFunction
            );

        EthSendTransaction ethSendTx = web3j.ethSendTransaction(tx).send();

        if (ethSendTx.hasError()) {
            throw new RuntimeException("Transaction failed: " + ethSendTx.getError().getMessage());
        }

        String txHash = ethSendTx.getResult();
        log.info("Transaction sent: {}", txHash);
        return txHash;
    }

    public BigInteger getBalance() {
        try {
            return web3j.ethGetBalance(
                credentials.getAddress(),
                DefaultBlockParameterName.LATEST
            ).send().getBalance();
        } catch (Exception e) {
            log.error("Failed to get balance", e);
            return BigInteger.ZERO;
        }
    }

    public TransactionReceipt waitForReceipt(String txHash) throws Exception {
        return web3j.ethGetTransactionReceipt(txHash)
            .send()
            .getResult();
    }

    private String encodeProcessReward(String rewardId, List<String> recipients, BigInteger amount) {
        String selector = "0x2b67b574";

        String paddedRewardId = rewardId.startsWith("0x") ? rewardId.substring(2) : rewardId;
        paddedRewardId = leftPad(paddedRewardId, 64, '0');

        String offset = "0000000000000000000000000000000000000000000000000000000000000060";

        String length = String.format("%064x", recipients.size());

        StringBuilder addresses = new StringBuilder();
        for (String addr : recipients) {
            String padded = addr.startsWith("0x") ? addr.substring(2) : addr;
            addresses.append(leftPad(padded, 64, '0'));
        }

        String paddedAmount = String.format("%064x", amount);

        return selector + paddedRewardId + offset + length + addresses + paddedAmount;
    }

    private static String leftPad(String value, int length, char padChar) {
        if (value.length() >= length) return value;
        StringBuilder sb = new StringBuilder(length);
        for (int i = value.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(value);
        return sb.toString();
    }
}

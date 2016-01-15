package org.libdohj.cate.util;

import org.bitcoinj.core.TransactionOutput;
import org.libdohj.cate.controller.MainController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.libdohj.cate.controller.WalletTransaction;

/**
 * Created by maxke on 15.01.2016.
 * Contains utility methods to format certain aspects of transactions
 */
public class TransactionFormatter {

    /**
     * Gets a list of all relevant outputs for a given {@link org.libdohj.cate.controller.MainController.WalletTransaction}
     * Relevant can mean: In case of an incoming transaction, all outputs paying our wallet;
     * in case of an outgoing transaction, all outputs that do NOT pay our wallet (change).
     * @param wtx The WalletTransaction to filter the outputs from
     * @return All relevant outputs
     */
    public static List<TransactionOutput> getRelevantOutputs(WalletTransaction wtx) {
        List<TransactionOutput> outputs = new ArrayList<>();
        if (wtx.getBalanceChange().isPositive()) {
            // We received coins, so only use outputs concerning our wallet.
            outputs.addAll(wtx.getTransaction().getWalletOutputs(wtx.getNetwork().wallet()));
        } else {
            // We sent coins, so use that output we actually sent to. We need to filter out a possible change output
            outputs.addAll(wtx.getTransaction().getOutputs());
            outputs.removeAll(wtx.getTransaction().getWalletOutputs(wtx.getNetwork().wallet()));
        }

        return outputs;
    }

    /**
     * Formats relevant outputs {@link TransactionFormatter#getRelevantOutputs(MainController.WalletTransaction)}
     * as a String of the paid addresses joined by "delimiter".
     * @param wtx The WalletTransaction to format
     * @param delimiter String to use a delimiter for the address list
     * @return a String as described above
     */
    public static String getRelevantOutputsAsString(WalletTransaction wtx, String delimiter) {
        return getRelevantOutputs(wtx).stream()
                .map(output -> output.getScriptPubKey().getToAddress(wtx.getParams()).toString())
                .collect(Collectors.joining(delimiter));
    }
}

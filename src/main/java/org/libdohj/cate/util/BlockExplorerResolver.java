package org.libdohj.cate.util;

import org.libdohj.cate.controller.MainController;
import org.libdohj.cate.controller.WalletTransaction;

/**
 * Created by maxke on 13.01.2016.
 * Finds an URL to call to show a certain transaction on a block explorer website
 */
public class BlockExplorerResolver {

    private static final String CHAINSO_BASE_URL = "https://chain.so/";
    private static final String CHAINSO_PATH_TX = "tx/";
    private static final String CHAINSO_PATH_BTC = "BTC/";
    private static final String CHAINSO_PATH_LTC = "LTC/";
    private static final String CHAINSO_PATH_DOGE = "DOGE/";
    private static final String CHAINSO_PATH_BTCTEST = "BTCTEST/";
    private static final String CHAINSO_PATH_LTCTEST = "LTCTEST/";
    private static final String CHAINSO_PATH_DOGETEST = "DOGETEST/";

    public static String getUrl(WalletTransaction wtx) {
        // TODO: This should take into account a setting the user can make for the explorer to use.
        return getChainSoUrl(wtx);
    }

    public static String getChainSoUrl(WalletTransaction wtx) {
        StringBuilder sb = new StringBuilder(CHAINSO_BASE_URL);
        sb.append(CHAINSO_PATH_TX);
        sb.append(networkCodeToPath(NetworkResolver.getCode(wtx.getParams())));
        sb.append(wtx.getTransaction().getHashAsString());
        return sb.toString();
    }

    private static String networkCodeToPath(NetworkResolver.NetworkCode code) {
        switch (code) {
            case BTC:
                return CHAINSO_PATH_BTC;
            case BTCTEST:
                return CHAINSO_PATH_BTCTEST;
            case LTC:
                return CHAINSO_PATH_LTC;
            case LTCTEST:
                return CHAINSO_PATH_LTCTEST;
            case DOGE:
                return CHAINSO_PATH_DOGE;
            case DOGETEST:
                return CHAINSO_PATH_DOGETEST;
            default:
                return "";
        }
    }
}

package org.libdohj.cate.util;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.libdohj.params.DogecoinMainNetParams;
import org.libdohj.params.DogecoinTestNet3Params;
import org.libdohj.params.LitecoinMainNetParams;

import java.util.LinkedHashMap;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Resolves NetworkParameters into names and names into NetworkParameters
 */
public class NetworkResolver {

    private static final LinkedHashMap<NetworkParameters, String> networkNames = new LinkedHashMap<>();
    private static final LinkedHashMap<String, NetworkParameters> networksByName = new LinkedHashMap<>();
    private static final LinkedHashMap<NetworkParameters, NetworkCode> networkCodes = new LinkedHashMap<>();
    private static final LinkedHashMap<NetworkCode, NetworkParameters> networksByCode = new LinkedHashMap<>();

    public enum NetworkCode { // This is way more type safe than some String
        BTC,
        BTCTEST,
        LTC,
        LTCTEST,
        DOGE,
        DOGETEST
    }

    static {
        registerNetwork(MainNetParams.get(), "Bitcoin", NetworkCode.BTC);
        registerNetwork(TestNet3Params.get(), "Bitcoin test", NetworkCode.BTCTEST);
        registerNetwork(LitecoinMainNetParams.get(), "Litecoin", NetworkCode.LTC);
        registerNetwork(DogecoinMainNetParams.get(), "Dogecoin", NetworkCode.DOGE);
        registerNetwork(DogecoinTestNet3Params.get(), "Dogecoin test", NetworkCode.DOGETEST);
    }

    private static void registerNetwork(final NetworkParameters params, final String name, NetworkCode code) {
        networkNames.put(params, name);
        networksByName.put(name, params);
        networkCodes.put(params, code);
        networksByCode.put(code, params);
    }

    /**
     * Get all network names the resolver knows about.
     *
     * @return a set of human readable network names.
     */
    public static Set<String> getNames() {
        return networksByName.keySet();
    }

    /**
     * Get all network codes the resolver knows about.
     * @return a set of network explicit network codes
     */
    public static Set<NetworkCode> getCodes() {
        return networksByCode.keySet();
    }

    /**
     * Get all networks the resolver knows about.
     *
     * @return a set of network parameters.
     */
    public static Set<NetworkParameters> getParameters() {
        return networkNames.keySet();
    }

    public static String getName(NetworkParameters params) {
        return networkNames.get(params);
    }

    public static NetworkCode getCode(NetworkParameters params) {
        return networkCodes.get(params);
    }

    public static NetworkParameters getParameter(String name) {
        return networksByName.get(name);
    }

    public static NetworkParameters getParameter(NetworkCode code) {
        return networksByCode.get(code);
    }
}

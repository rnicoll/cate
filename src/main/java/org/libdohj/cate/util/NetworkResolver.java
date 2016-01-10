package org.libdohj.cate.util;

import java.util.Collection;
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
    public static ResourceBundle resource;

    static {
        registerNetwork(MainNetParams.get(), "Bitcoin");
        registerNetwork(TestNet3Params.get(), "Bitcoin test");
        registerNetwork(LitecoinMainNetParams.get(), "Litecoin");
        registerNetwork(DogecoinMainNetParams.get(), "Dogecoin");
        registerNetwork(DogecoinTestNet3Params.get(), "Dogecoin test");
    }

    private static void registerNetwork(final NetworkParameters params, final String name) {
        networkNames.put(params, name);
        networksByName.put(name, params);
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

    public static NetworkParameters getParameter(String name) {
        return networksByName.get(name);
    }
}

package org.libdohj.cate;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.libdohj.params.DogecoinMainNetParams;
import org.libdohj.params.DogecoinTestNet3Params;
import org.libdohj.params.LitecoinMainNetParams;

import java.util.LinkedHashMap;

/**
 * Resolves NetworkParameters into names and names into NetworkParameters
 */
public class NetworkResolver {
    private static final LinkedHashMap<NetworkParameters, String> networkNames = new LinkedHashMap<>();
    private static final LinkedHashMap<String, NetworkParameters> networksByName = new LinkedHashMap<>();

    static {
        // TODO: Localize
        networkNames.put(MainNetParams.get(), "Bitcoin");
        networkNames.put(TestNet3Params.get(), "Bitcoin test");
        networkNames.put(LitecoinMainNetParams.get(), "Litecoin");
        networkNames.put(DogecoinMainNetParams.get(), "Dogecoin");
        networkNames.put(DogecoinTestNet3Params.get(), "Dogecoin test");

        for (NetworkParameters params: networkNames.keySet()) {
            networksByName.put(networkNames.get(params), params);
        }
    }

    public static String getName(NetworkParameters params) {
        return networkNames.get(params);
    }

    public static NetworkParameters getParams(String name) {
        return networksByName.get(name);
    }
}

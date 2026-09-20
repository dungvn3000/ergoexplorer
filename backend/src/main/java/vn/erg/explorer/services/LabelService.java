package vn.erg.explorer.services;

import com.typesafe.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

/** Human labels for well-known addresses (pools, protocol contracts) from {@code ergo.labels} in application.conf. */
@Singleton
public class LabelService {

    private final Map<String, String> labels = new HashMap<>();

    @Inject
    public LabelService(Config config) {
        if (config.hasPath("ergo.labels")) {
            Config section = config.getConfig("ergo.labels");
            section.root().unwrapped().forEach((address, name) -> labels.put(address, String.valueOf(name)));
        }
        labels.putIfAbsent(vn.erg.explorer.utils.ErgoConstants.FEE_ADDRESS, "Mining fee contract");
        labels.putIfAbsent(vn.erg.explorer.utils.ErgoConstants.EMISSION_ADDRESS, "Emission contract");
        labels.putIfAbsent(vn.erg.explorer.utils.ErgoConstants.REEMISSION_ADDRESS, "Re-emission contract (EIP-27)");
        labels.putIfAbsent(vn.erg.explorer.utils.ErgoConstants.PAY_TO_REEMISSION_ADDRESS, "Pay-to-reemission (EIP-27)");
    }

    public String label(String address) {
        return labels.get(address);
    }

}

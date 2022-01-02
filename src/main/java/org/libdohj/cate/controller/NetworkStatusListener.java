package org.libdohj.cate.controller;

import com.google.common.util.concurrent.Service;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.libdohj.cate.Network;

import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;

/**
 * NetworkStatusListener wraps a string property which is updated as the network changes status
 * (starting, running, stopping, etc.)
 */
class NetworkStatusListener extends Service.Listener {
    private final MainController controller;
    private final ResourceBundle resources;
    private final Network network;
    private final SimpleStringProperty status;

    public NetworkStatusListener(MainController controller,
                                 ResourceBundle resources,
                                 ExecutorService executor,
                                 Network network) {
        this.controller = controller;
        this.resources = resources;
        this.network = network;
        this.status = new SimpleStringProperty("Starting");
    }

    @Override
    public void starting() {
        this.status.setValue(resources.getString("walletList.networkStatus.starting"));
    }

    @Override
    public void running() {
        this.status.setValue(resources.getString("walletList.networkStatus.running"));
    }

    @Override
    public void stopping(Service.State from) {
        this.status.setValue(resources.getString("walletList.networkStatus.stopping"));
    }

    @Override
    public void terminated(Service.State from) {
        this.controller.removeNetwork(this.network);
        this.status.setValue(resources.getString("walletList.networkStatus.terminated"));
    }

    @Override
    public void failed(Service.State from, Throwable failure) {
        this.status.setValue(resources.getString("walletList.networkStatus.failed"));
    }

    public StringProperty getStatus() {
        return status;
    }
}

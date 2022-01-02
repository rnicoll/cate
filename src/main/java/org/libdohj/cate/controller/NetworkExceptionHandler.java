package org.libdohj.cate.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.libdohj.cate.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread;
import java.util.ResourceBundle;

class NetworkExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Logger logger = LoggerFactory.getLogger(NetworkExceptionHandler.class);
    private final MainController controller;
    private final ResourceBundle resources;
    private final Network network;

    NetworkExceptionHandler(final MainController controller,
                            final ResourceBundle resources,
                            final Network network) {
        this.controller = controller;
        this.resources = resources;
        this.network = network;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable thrwbl) {
        logger.error("Internal error from network "
                + network.getParams().getId(), thrwbl);
        if (thrwbl instanceof Exception) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(resources.getString("internalError.title"));
                alert.setContentText(thrwbl.getMessage());
                alert.showAndWait();
                // TODO: Shut down and de-register the wallet from currently
                // running
            });
        } else {
            // Fatal, begin shutdown
            this.controller.stop();
        }
    }
}
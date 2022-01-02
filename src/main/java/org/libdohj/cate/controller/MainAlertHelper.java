package org.libdohj.cate.controller;

import javafx.scene.control.Alert;
import org.bitcoinj.crypto.KeyCrypterException;

import java.util.ResourceBundle;

public class MainAlertHelper {
    private final ResourceBundle resources;

    MainAlertHelper(final ResourceBundle resources) {
        this.resources = resources;
    }

    void showEncryptionErrorModalDialogOnUIThread(KeyCrypterException t) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                t.getMessage());
        alert.setTitle(resources.getString("alert.encryptWallet.errorTitle"));
        alert.showAndWait();
    }

    void showWalletNotEncryptedModalDialogOnUIThread() {
        Alert alert = new Alert(Alert.AlertType.WARNING,
                resources.getString("alert.encryptWallet.noticeMsg"));
        alert.setTitle(resources.getString("alert.encryptWallet.noticeTitle"));
        alert.showAndWait();
    }

    void showSuccessModalDialogOnUIThread() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                resources.getString("alert.encryptWallet.successMsg"));
        alert.setTitle(resources.getString("alert.encryptWallet.successTitle"));
        alert.showAndWait();
    }
}

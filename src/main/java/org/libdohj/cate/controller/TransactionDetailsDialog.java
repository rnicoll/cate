package org.libdohj.cate.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.bitcoinj.core.Coin;
import org.libdohj.cate.util.GenericUtils;
import org.libdohj.cate.util.TransactionFormatter;

import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Created by maxke on 14.01.2016.
 * Shows a bunch of details about a selected {@link org.libdohj.cate.controller.MainController.WalletTransaction}
 */
public class TransactionDetailsDialog extends Stage {

    @FXML // ResourceBundle that was given to the FXMLLoader
    private ResourceBundle resources;
    @FXML
    private Label lbStatus;
    @FXML
    private Label lbTime;
    @FXML
    private Label lbTo;
    @FXML
    private Label lbAmount;
    @FXML
    private Label lbFee;
    @FXML
    private Label lbGross;
    @FXML
    private Label lbID;
    @FXML
    private Button btnClose;
    @FXML
    private Button btnCopyId;
    @FXML
    private Button btnCopyTo;
    @FXML
    private Label valStatus;
    @FXML
    private Label valTime;
    @FXML
    private Label valTo;
    @FXML
    private Label valAmount;
    @FXML
    private Label valFee;
    @FXML
    private Label valGross;
    @FXML
    private Label valID;
    @FXML
    private RowConstraints rowFee;
    @FXML
    private RowConstraints rowGross;

    private MainController.WalletTransaction wtx;

    public TransactionDetailsDialog(MainController.WalletTransaction transaction) {
        this.wtx = transaction;
        ResourceBundle i18nBundle = ResourceBundle.getBundle("i18n.Bundle");

        FXMLLoader loader = new FXMLLoader(TransactionDetailsDialog.class.getResource("/txDetailsDialog.fxml"), i18nBundle);
        loader.setController(this);

        try {
            setScene(new Scene(loader.load()));
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, resources.getString("alert.txDetailsError")
                    + e.getMessage());
            alert.setTitle(resources.getString("internalError.title"));
            alert.showAndWait();
            return;
        }

        setTitle(resources.getString("txDetails.title"));
        initModality(Modality.APPLICATION_MODAL);
        setResizable(false);
        getIcons().add(new Image("/org/libdohj/cate/cate.png"));

        valStatus.setText(MessageFormat.format(resources.getString("txDetails.conf"), wtx.getTransaction().getConfidence().getDepthInBlocks()));

        final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        valTime.setText(dateFormat.format(wtx.getTransaction().getUpdateTime()));

        final Coin amount;

        if (wtx.getBalanceChange().isPositive()) {
            // We don't know about the fee for receiving transactions, so remove the next two lines
            hideFeeAndGross();
            amount = wtx.getBalanceChange();
        } else {
            // Here we know the fee, so show it
            final Coin fee = wtx.getTransaction().getFee();
            valFee.setText(wtx.getNetwork().format(fee).toString());

            final Coin gross = wtx.getBalanceChange();
            valGross.setText(wtx.getNetwork().format(gross).toString());

            amount = gross.add(fee); // gross is negative so add the fee back to get the actual amount.
        }

        valTo.setText(TransactionFormatter.getRelevantOutputsAsString(wtx, ", "));

        valAmount.setText(wtx.getNetwork().format(amount).toString());

        valID.setText(wtx.getTransaction().getHashAsString());
    }

    @FXML
    void onBtnCloseAction(ActionEvent event) {
        close();
    }

    @FXML
    void onBtnCopyIdAction(ActionEvent event) {
        GenericUtils.copyToClipboard(wtx.getTransaction().getHashAsString());
    }

    @FXML
    void onBtnCopyToAction(ActionEvent event) {
        GenericUtils.copyToClipboard(
                TransactionFormatter.getRelevantOutputs(wtx).get(0).getScriptPubKey().getToAddress(
                        wtx.getParams()).toString());
    }

    private void hideFeeAndGross() {
        valFee.visibleProperty().set(false);
        lbFee.visibleProperty().set(false);
        valGross.visibleProperty().set(false);
        lbGross.visibleProperty().set(false);

        rowFee.minHeightProperty().set(0);
        rowFee.maxHeightProperty().set(0);
        rowFee.prefHeightProperty().set(0);
        rowGross.minHeightProperty().set(0);
        rowGross.maxHeightProperty().set(0);
        rowGross.prefHeightProperty().set(0);
    }
}
/*
 * Copyright 2016 jrn.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.libdohj.cate.controller;

import java.util.Objects;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import org.libdohj.cate.CATE;
import static org.libdohj.cate.controller.MainController.DIALOG_HGAP;
import static org.libdohj.cate.controller.MainController.DIALOG_VGAP;

/**
 * @author Qu3ntin0
 */
public class DualPasswordInputDialog extends Dialog<String> {
    private final GridPane grid;
    private final Label newLabel;
    private final Label repeatLabel;
    private final PasswordField newPass;
    private final PasswordField repeatPass;

    public DualPasswordInputDialog(final ResourceBundle resources) {
        super();

        newLabel = new Label(resources.getString("dialogEncrypt.passNew"));
        repeatLabel = new Label(resources.getString("dialogEncrypt.passRepeat"));
        newPass = new PasswordField();
        repeatPass = new PasswordField();

        newLabel.getStyleClass().add("label-heading");
        repeatLabel.getStyleClass().add("label-heading");

        grid = new GridPane();
        grid.setHgap(DIALOG_HGAP);
        grid.setVgap(DIALOG_VGAP);
        grid.addRow(0, newLabel, newPass);
        grid.addRow(1, repeatLabel, repeatPass);

        getDialogPane().getStylesheets().add(CATE.DEFAULT_STYLESHEET);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(grid);

        Platform.runLater(newPass::requestFocus);

        setResultConverter(dialogButton -> {
            if (dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                if (!newPass.getText().trim().isEmpty() && !repeatPass.getText().trim().isEmpty()) {
                    if (Objects.equals(newPass.getText(), repeatPass.getText())) {
                        return newPass.getText();
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            return null;
        });
    }
}

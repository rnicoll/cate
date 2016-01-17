/*
 * Copyright 2016 Qu3ntin0.
 * Copyright 2016 Ross Nicoll.
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

import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import org.libdohj.cate.CATE;

/**
 * @author Qu3ntin0
 */
public class PasswordInputDialog extends Dialog<String> {
    private final GridPane grid;
    private final PasswordField pass;
    private final Label heading;

    public PasswordInputDialog() {
        super();
        pass = new PasswordField();
        grid = new GridPane();
        heading = new Label();

        heading.getStyleClass().add("label-heading");
        contentTextProperty().addListener((observable, oldVal, newVal) -> {
            heading.setText(newVal);
        });

        grid.setHgap(MainController.DIALOG_HGAP);
        grid.setVgap(MainController.DIALOG_VGAP);
        grid.addRow(0, heading, pass);

        getDialogPane().getStylesheets().add(CATE.DEFAULT_STYLESHEET);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(grid);

        Platform.runLater(pass::requestFocus);

        setResultConverter(dialogButton -> {
            if (dialogButton.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return pass.getText();
            }
            return null;
        });
    }
}

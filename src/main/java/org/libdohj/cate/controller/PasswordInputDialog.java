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

/**
 * @author Qu3ntin0
 */
public class PasswordInputDialog extends Dialog<String> {
    final GridPane grid;
    final PasswordField pass;
    final Label contentLabel;

    public PasswordInputDialog() {
        super();
        pass = new PasswordField();
        grid = new GridPane();
        contentLabel = new Label();

        contentTextProperty().addListener((observable, oldVal, newVal) -> {
            contentLabel.setText(newVal);
        });

        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(contentLabel, 0, 0);
        grid.add(pass, 1, 0);

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

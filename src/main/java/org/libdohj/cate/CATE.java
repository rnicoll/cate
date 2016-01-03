/*
 * Copyright 2015 Ross Nicoll.
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
package org.libdohj.cate;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.libdohj.cate.controller.MainController;
import org.libdohj.cate.util.DataDirFactory;

/**
 * CATE: Cross-chain Atomic Trading Engine
 *
 * @author Ross Nicoll
 */
public class CATE extends Application {
    private static final String APPLICATION_NAME_FOLDER = "CATE";
    private static Logger logger  = Logger.getLogger(CATE.class.getName());
    private static final DataDirFactory dataDirFactory = new DataDirFactory(APPLICATION_NAME_FOLDER);

    private static File dataDir = null;

    private MainController controller;

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Parent root = loader.load();

        this.controller = (MainController) loader.getController();

        this.controller.connectTo("Dogecoin", dataDir);
        this.controller.connectTo("Dogecoin test", dataDir);

        primaryStage.setTitle("CATE");
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.show();
    }

    @Override
    public void stop() {
        this.controller.stop();
    }

    public static void main(String[] args) {
        try {
            dataDir = dataDirFactory.get();
        } catch (DataDirFactory.UnableToDetermineDataDirException ex) {
            logger.log(Level.SEVERE, "Unable to determine path to Data Directory", ex);
            System.exit(1);
        }

        launch(args);
    }

}
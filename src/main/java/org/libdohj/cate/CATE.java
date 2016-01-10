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

import com.google.common.util.concurrent.Service;
import java.io.File;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import org.libdohj.cate.controller.MainController;
import org.libdohj.cate.util.DataDirFactory;
import org.libdohj.cate.util.NetworkResolver;

/**
 * CATE: Cross-chain Atomic Trading Engine
 *
 * @author Ross Nicoll
 */
public class CATE extends Application {

    private static final String APPLICATION_NAME_FOLDER = "CATE";
    private static Logger logger = Logger.getLogger(CATE.class.getName());
    private static final DataDirFactory dataDirFactory = new DataDirFactory(APPLICATION_NAME_FOLDER);

    private static File dataDir = null;

    private MainController controller;
    private ExecutorService listenerExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void start(Stage primaryStage) throws Exception {
        final Image cateIcon = new Image(CATE.class.getResourceAsStream("cate.png"));
        primaryStage.getIcons().add(cateIcon);

        // This line to resolve keys against the environment locale properties
        ResourceBundle i18nBundle = ResourceBundle.getBundle("i18n.Bundle");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"), i18nBundle);
        Parent root = loader.load();

        this.controller = (MainController) loader.getController();
        this.controller.connectTo(NetworkResolver.getParameter("Dogecoin"), dataDir);
        this.controller.connectTo(NetworkResolver.getParameter("Dogecoin test"), dataDir);

        primaryStage.setTitle(i18nBundle.getString("application.title"));
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.show();
    }

    @Override
    public void stop() {
        // Wait until all the services stop before we shut down the state change
        // executor
        // TODO: We should have the listenerExecutor actually shut itself down
        // instead of blocking here
        this.controller.stop().stream().forEach(service -> service.awaitTerminated());
        listenerExecutor.shutdown();
    }

    public static void main(String[] args) {
        try {
            dataDir = dataDirFactory.get();
        } catch (DataDirFactory.UnableToDetermineDataDirException ex) {
            ResourceBundle i18nBundle = ResourceBundle.getBundle("i18n.Bundle");
            logger.log(Level.SEVERE, i18nBundle.getString("alert.datadirError"), ex);
            System.exit(1);
        }

        launch(args);
    }

}

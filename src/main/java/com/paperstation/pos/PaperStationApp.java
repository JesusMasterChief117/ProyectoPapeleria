package com.paperstation.pos;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Aplicación principal de JavaFX para PaperStation POS.
 */
public class PaperStationApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(PaperStationApp.class);

    public static final double DEFAULT_WIDTH = 1280;
    public static final double DEFAULT_HEIGHT = 800;
    public static final double MIN_WIDTH = 1100;
    public static final double MIN_HEIGHT = 720;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Iniciando PaperStation POS...");

        try {
            URL fxmlLocation = getClass().getResource("/com/paperstation/pos/ui/view/PosView.fxml");
            if (fxmlLocation == null) {
                throw new IllegalStateException("No se encontró el archivo FXML principal en /com/paperstation/pos/ui/view/PosView.fxml");
            }

            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();

            Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);

            // Cargar hoja de estilo global del sistema de diseño (pos-theme.css)
            URL cssLocation = getClass().getResource("/com/paperstation/pos/ui/css/pos-theme.css");
            if (cssLocation == null) {
                cssLocation = getClass().getResource("/com/paperstation/pos/ui/css/pos.css");
            }
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            } else {
                logger.warn("No se encontró la hoja de estilos pos-theme.css");
            }

            // Configuración de ventana estable y responsiva
            primaryStage.setTitle("PaperStation POS - Terminal de Ventas");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(MIN_WIDTH);
            primaryStage.setMinHeight(MIN_HEIGHT);
            primaryStage.setWidth(DEFAULT_WIDTH);
            primaryStage.setHeight(DEFAULT_HEIGHT);
            
            // Centrar ventana en pantalla de forma suave
            primaryStage.centerOnScreen();

            primaryStage.show();
            logger.info("Ventana principal cargada exitosamente ({}x{})", DEFAULT_WIDTH, DEFAULT_HEIGHT);

        } catch (IOException e) {
            logger.error("Error al cargar la interfaz inicial de PaperStation", e);
            throw new RuntimeException("Fallo al inicializar PaperStation POS", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

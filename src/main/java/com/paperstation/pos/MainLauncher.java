package com.paperstation.pos;

/**
 * Lanzador de entrada estándar para PaperStation POS.
 * Evita problemas de inicialización del runtime de JavaFX en entornos no modulares o ejecutables empaquetados.
 */
public class MainLauncher {

    public static void main(String[] args) {
        PaperStationApp.main(args);
    }
}

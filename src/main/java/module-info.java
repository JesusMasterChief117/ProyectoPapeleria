module com.paperstation.pos {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires transitive javafx.base; // la API de modelo/servicio expone tipos observables de javafx.base
    requires org.slf4j;

    opens com.paperstation.pos to javafx.graphics, javafx.fxml;
    opens com.paperstation.pos.ui.controller to javafx.fxml;
    opens com.paperstation.pos.ui.component to javafx.fxml, javafx.graphics;
    opens com.paperstation.pos.model to javafx.base;

    exports com.paperstation.pos;
    exports com.paperstation.pos.model;
    exports com.paperstation.pos.service;
    exports com.paperstation.pos.ui.controller;
    exports com.paperstation.pos.ui.component;
}

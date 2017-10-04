package com.cisco.trex.stl.gui.controllers.capture;

import com.cisco.trex.stl.gui.services.capture.PktCaptureServiceException;
import com.exalttech.trex.application.TrexApp;
import com.exalttech.trex.ui.dialog.DialogView;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class PacketCaptureDashboardController extends DialogView implements Initializable {

    @FXML
    private Label startMonitorBtn;
    
    @FXML
    private Label stopMonitorBtn;
    
    @FXML
    private Label clearMonitorBtn;
    
    @FXML
    private Label startWiresharkBtn;
    
    @FXML
    private Label startRecorderBtn;
    
    @FXML
    private TabPane captureTabPane;
    
    @FXML
    private HBox buttonBar;
    
    @FXML
    private MonitorController monitorController;
    
    @FXML
    private RecordController recordController;

    private boolean monitorIsActive = false;

    @Override
    public void onEnterKeyPressed(Stage stage) {
        // Nothing to do
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        monitorController.setStartHandler(() -> {
            startMonitorBtn.setDisable(true);
            stopMonitorBtn.setDisable(false);
            monitorIsActive = true;
        });

        monitorController.setStopHandler(() -> {
            stopMonitorBtn.setDisable(true);
            startMonitorBtn.setDisable(false);
            monitorIsActive = false;
        });

        startMonitorBtn.setOnMouseClicked(event -> {
            monitorController.startCapture();
        });
        stopMonitorBtn.setOnMouseClicked(event -> {
            monitorController.stopCapture();
        });

        clearMonitorBtn.setOnMouseClicked(event -> monitorController.clearCapture());
        startWiresharkBtn.setOnMouseClicked(event -> monitorController.startWireshark());
        startRecorderBtn.setOnMouseClicked(event -> {
            Dialog<AddRecordPojo> dialog = createAddRecorderDialog("Add Recorder", new AddRecorderController());
            Optional<AddRecordPojo> addRecordPojo = dialog.showAndWait();
            addRecordPojo.ifPresent(pojo -> {
                try {
                    monitorController.startRecorder(pojo.rxPorts, pojo.txPorts, pojo.filter, pojo.pktLimit);
                } catch (PktCaptureServiceException e) {
                    // TODO: Make this part common
                    Text text = new Text("Unable to start a recorder: " + e.getLocalizedMessage());
                    text.setWrappingWidth(350);

                    HBox container = new HBox();
                    container.setSpacing(10);
                    container.getChildren().add(text);

                    Alert alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
                    alert.getDialogPane().setContent(container);
                    alert.showAndWait();
                }
            });

        });

        captureTabPane.getSelectionModel().selectedItemProperty().addListener((o, oldVal, newVal) -> {
            boolean isMonitorTabSelected = newVal.getText().equalsIgnoreCase("Monitor");
            updateButtonBar(isMonitorTabSelected);
        });
        updateButtonBar(true);
    }

    @Override
    public void setupStage(Stage stage) {
        stage.setOnCloseRequest((e) ->{
            if(monitorController.isRunning()) {
                monitorController.stopCapture();
            }
        });
        super.setupStage(stage);

    }

    private void updateButtonBar(boolean isMonitorTabSelected) {
        if (isMonitorTabSelected) {
            startMonitorBtn.setDisable(monitorIsActive);
            stopMonitorBtn.setDisable(!monitorIsActive);
            clearMonitorBtn.setDisable(false);
            startWiresharkBtn.setDisable(false);
            startRecorderBtn.setDisable(true);
        } else {
            startMonitorBtn.setDisable(true);
            stopMonitorBtn.setDisable(true);
            clearMonitorBtn.setDisable(true);
            startWiresharkBtn.setDisable(true);
            startRecorderBtn.setDisable(false);
        }
    }

    private Dialog<AddRecordPojo> createAddRecorderDialog(String title, AddRecorderController addRecorerView) {
        Dialog<AddRecordPojo> dialog = new Dialog<>();

        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.getScene().getStylesheets().add(TrexApp.class.getResource("/styles/mainStyle.css").toExternalForm());

        dialog.setWidth(400);
        dialog.setHeight(300);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(addRecorerView);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return addRecorerView.getData();
            } else {
                return null;
            }
        });

        return dialog;
    }

}

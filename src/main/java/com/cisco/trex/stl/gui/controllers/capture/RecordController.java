package com.cisco.trex.stl.gui.controllers.capture;

import com.cisco.trex.stateless.model.capture.CaptureInfo;
import com.cisco.trex.stateless.model.capture.CapturedPackets;
import com.cisco.trex.stateless.model.capture.CapturedPkt;
import com.cisco.trex.stl.gui.models.Recorder;
import com.cisco.trex.stl.gui.services.capture.PktCaptureService;
import com.cisco.trex.stl.gui.services.capture.PktCaptureServiceException;
import com.exalttech.trex.ui.PortsManager;
import com.exalttech.trex.ui.models.PortModel;
import com.exalttech.trex.ui.util.AlertUtils;
import com.exalttech.trex.util.Initialization;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.apache.log4j.Logger;
import org.pcap4j.core.*;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.namednumber.DataLinkType;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class RecordController extends BorderPane {

    private static Logger LOG = Logger.getLogger(RecordController.class);
    
    @FXML
    private TableView<Recorder> activeRecorders;

    @FXML
    private TableColumn<Recorder, String> id;
    
    @FXML
    private TableColumn<Recorder, String> status;
    
    @FXML
    private TableColumn<Recorder, String> packets;
    
    @FXML
    private TableColumn<Recorder, String> bytes;
    
    @FXML
    private TableColumn<Recorder, String> rxFilter;
    
    @FXML
    private TableColumn<Recorder, String> txFilter;

    @FXML
    private TableColumn<Recorder, String> bpfFilter;

    @FXML
    private TableColumn<Recorder, String> actions;

    @FXML
    private TableColumn<Recorder, String> type;

    
    private PktCaptureService pktCaptureService;

    private RecorderService recorderService = new RecorderService();

    FileChooser fileChooser = new FileChooser();

    public RecordController() {
        Initialization.initializeFXML(this, "/fxml/pkt_capture/Record.fxml");

        id.setCellValueFactory(cellData -> cellData.getValue().idProperty().asString());
        status.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        packets.setCellValueFactory(cellData -> cellData.getValue().packetsProperty());
        bytes.setCellValueFactory(cellData -> cellData.getValue().bytesProperty().asString());
        rxFilter.setCellValueFactory(cellData -> cellData.getValue().rxFilterProperty());
        txFilter.setCellValueFactory(cellData -> cellData.getValue().txFilterProperty());
        bpfFilter.setCellValueFactory(cellData -> cellData.getValue().bpfFilterProperty());
        type.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        actions.setCellValueFactory(new PropertyValueFactory<>(""));
        actions.setCellFactory(column -> new TableCell<Recorder, String>() {

            ImageView removeIcon = new ImageView("icons/delete_record.png");
            ImageView stopIcon = new ImageView("icons/stop_record.png");
            ImageView saveIcon = new ImageView("icons/export_recorder.png");

            HBox recordActionsPane = new HBox(10, saveIcon, stopIcon, removeIcon);

            {
                removeIcon.getStyleClass().addAll("recordActionBtn");
                stopIcon.getStyleClass().addAll("recordActionBtn");
                saveIcon.getStyleClass().addAll("recordActionBtn");

                recordActionsPane.setPadding(new Insets(3));
            }

            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {

                    Recorder recorder = getTableView().getItems().get(getIndex());

                    removeIcon.setOnMouseClicked(e -> pktCaptureService.removeRecorder(recorder.getId()));

                    saveIcon.setOnMouseClicked(e -> handleSavePkts((recorder.getId())));

                    stopIcon.setOnMouseClicked(e -> handleStopRecorder(recorder.getId()));

                    setGraphic(recordActionsPane);
                    setText(null);
                }
            }
        });

        recorderService.setOnSucceeded(this::handleOnRecorderReceived);
        recorderService.setPeriod(new Duration(1000));

        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Pcap Files", "*.pcap", "*.cap"));
    }

    public void setPktCaptureService(PktCaptureService svc) {
        pktCaptureService = svc;
        recorderService.start();
    }

    private void handleOnRecorderReceived(WorkerStateEvent workerStateEvent) {
        List<CaptureInfo> monitors = recorderService.getValue();
        ObservableList<Recorder> currentRecorders = activeRecorders.getItems();
        monitors.stream()
                .filter(monitor -> {
                    final String mode = monitor.getMode();
                    if (mode == null) return false;

                    final boolean isModeSuitable = mode.equalsIgnoreCase("fixed")
                            || mode.equalsIgnoreCase("cyclic");
                    return isModeSuitable && monitor.getId() != pktCaptureService.getCurrentActiveMonitorId();
                })
                .map(this::captureInfo2Recorder)
                .collect(toList()).forEach(newRecorder -> {
                    Optional<Recorder> existed =
                            currentRecorders.stream()
                                            .filter(recorder -> recorder.getId() == newRecorder.getId())
                                            .findFirst();
                    if(existed.isPresent()) {
                        Recorder recorder = existed.get();
                        recorder.setBytes(newRecorder.getBytes());
                        recorder.setPackets(newRecorder.getPackets());
                        recorder.setStatus(newRecorder.getStatus());
                    } else {
                        currentRecorders.add(newRecorder);
                    }
                });
        currentRecorders.removeIf(recorder ->
                !monitors.stream().anyMatch(newRecorder -> newRecorder.getId() == recorder.getId())
        );
    }

    private Recorder captureInfo2Recorder(CaptureInfo captureInfo) {
        final Map<String, String> modeMapping = new HashMap<>();
        modeMapping.put("fixed", "Recorder");
        modeMapping.put("cyclic", "Monitor");

        final String mode = modeMapping.containsKey(captureInfo.getMode())
                ? modeMapping.get(captureInfo.getMode())
                : "Unknown";

        return new Recorder(
            captureInfo.getId(),
            captureInfo.getState(),
            String.format("%s/%s", captureInfo.getCount(), captureInfo.getLimit()),
            captureInfo.getBytes(),
            parseFilterMask(captureInfo.getFilter().getRxPortMask()),
            parseFilterMask(captureInfo.getFilter().getTxPortMask()),
            captureInfo.getFilter().getBpfFilter(),
            mode
        );
    }
    
    private String parseFilterMask(int portMask) {
        String mask = new StringBuilder(Integer.toBinaryString(portMask)).reverse().toString();
        String[] bits = mask.split("");
        List<Integer> enabledPorts = new ArrayList<>();
        for(int i = 0; i<bits.length; i++) {
            if (bits[i].equals("1")) {
                enabledPorts.add(i);
            }
        }
        if (enabledPorts.size() > 0) {
            return enabledPorts.stream().map(Object::toString).collect(Collectors.joining(", "));
        } else {
            return "Not selected";
        }
    }

    private void handleStopRecorder(int monitorId) {
        try {
            pktCaptureService.stopRecorder(monitorId);
        } catch (PktCaptureServiceException e) {
            LOG.error("Unable to stop recorder.", e);

            AlertUtils.construct(
                    Alert.AlertType.ERROR,
                    "Recorder error",
                    "Unable to stop the recorder",
                    e.getLocalizedMessage())
                    .showAndWait();
        }
    }

    private List<Integer> guardEnabledServiceMode(List<Integer> rxPorts, List<Integer> txPorts) {
        Set<Integer> invalidPorts = new HashSet<>();
        
        invalidPorts.addAll(filterPortsWihtDisabledSM(rxPorts));
        invalidPorts.addAll(filterPortsWihtDisabledSM(txPorts));
        
        return new ArrayList<>(invalidPorts);
    }
    
    private List<Integer> filterPortsWihtDisabledSM(List<Integer> portIndexes) {
        return portIndexes.stream()
                .map(portIndex -> PortsManager.getInstance().getPortModel(portIndex))
                .filter(portModel -> !portModel.getServiceMode())
                .map(PortModel::getIndex)
                .collect(toList());
    }

    public void handleSavePkts(int monitorId) {
        List<CapturedPkt> capturedPkts = new ArrayList<>();
        int pendingPkts = 1;
        while (pendingPkts > 0) {
            try {
                CapturedPackets capturedPackets = pktCaptureService.fetchCapturedPkts(monitorId, 1000);
                pendingPkts = capturedPackets.getPendingPkts();
                capturedPkts.addAll(capturedPackets.getPkts());
            } catch (PktCaptureServiceException e) {
                LOG.error("Unable to fetch packets.", e);
                break;
            }
        }

        File outFile = fileChooser.showSaveDialog(getScene().getWindow());
        if (outFile != null) {
            try {
                dumpPkts(capturedPkts, outFile.getAbsolutePath());
            } catch (Exception e) {
                LOG.error("Unable to dump packets.", e);
            }
        }
    }

    private void dumpPkts(List<CapturedPkt> pkts, String filename) throws PcapNativeException, NotOpenException {
        PcapHandle handle = Pcaps.openDead(DataLinkType.EN10MB, 65536);
        PcapDumper dumper = handle.dumpOpen(filename);
        
        try {
            pkts.stream().map(this::toEtherPkt)
                    .filter(Objects::nonNull)
                    .collect(toList())
                    .forEach(ethPkt -> {
                        try {
                            dumper.dump(ethPkt);
                        } catch (NotOpenException e) {
                            LOG.error("Unable to dump pkt.", e);
                        }
                    });
        } finally {
            dumper.close();
            handle.close();
        }
    }
    
    private EthernetPacket toEtherPkt(CapturedPkt pkt) {
        byte[] pktBinary = Base64.getDecoder().decode(pkt.getBinary());
        EthernetPacket ethPkt = null;
        try {
            ethPkt = EthernetPacket.newPacket(pktBinary, 0, pktBinary.length);
        } catch (IllegalRawDataException e) {
            LOG.error("Save PCAP. Unable to parse pkt from server.", e);
            return null;
        }
        return ethPkt;
    }
    
    public void handleRemoveRecorer(ActionEvent event) {
        Recorder selectedRecorder = activeRecorders.getSelectionModel().getSelectedItem();
        if (selectedRecorder == null) {
            return;
        }
    }

    private class RecorderService extends ScheduledService<List<CaptureInfo>> {

        @Override
        protected Task<List<CaptureInfo>> createTask() {
            return new Task<List<CaptureInfo>>() {
                @Override
                protected List<CaptureInfo> call() throws Exception {
                    try {
                        return pktCaptureService.getActiveCaptures();
                    } catch (PktCaptureServiceException e) {
                        LOG.error("Unable to fetch pkts from monitor.", e);
                        return null;
                    }
                }
            };
        }
    }
}

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import nz.sodium.CellSink;
import nz.sodium.Listener;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Tuple2;

public class GpsGUI {

    private static final int TRACKER_COUNT = 10;
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double FEET_TO_METERS = 0.3048;
    private static final long WINDOW_MILLIS = 300000L;

    private static Listener[] listeners;
    private static GpsService service;

    // Current filter settings and version
    private static class FilterSettings {
        final double latitude;
        final double longitude;
        final long version;

        FilterSettings(
                double latitude,
                double longitude,
                long version) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.version = version;
        }

        boolean matches(GpsEvent event) {
            return event.latitude >= latitude
                && event.longitude >= longitude;
        }
    }

    // An event that passed the current filter
    private static class FilteredGpsEvent {
        final GpsEvent event;
        final long version;

        FilteredGpsEvent(GpsEvent event, long version) {
            this.event = event;
            this.version = version;
        }
    }

    // GPS coordinates with altitude in meters
    private static class MetricGpsEvent {
        final String name;
        final double latitude;
        final double longitude;
        final double altitudeMeters;
        final double originalAltitudeFeet;
        final long filterVersion;

        MetricGpsEvent(FilteredGpsEvent filtered) {
            GpsEvent event = filtered.event;

            name = event.name;
            latitude = event.latitude;
            longitude = event.longitude;
            altitudeMeters =
                event.altitude * FEET_TO_METERS;
            originalAltitudeFeet = event.altitude;
            filterVersion = filtered.version;
        }
    }

    // Position and its received time
    private static class TimedPosition {
        final MetricGpsEvent position;
        final long timestamp;

        TimedPosition(
                MetricGpsEvent position,
                long timestamp) {
            this.position = position;
            this.timestamp = timestamp;
        }
    }

    // Input commands for FRP distance calculation
    private static class DistanceCommand {
        static final int ADD = 1;
        static final int TICK = 2;
        static final int RESET = 3;

        final int type;
        final MetricGpsEvent event;
        final long timestamp;
        final long version;

        private DistanceCommand(
                int type,
                MetricGpsEvent event,
                long timestamp,
                long version) {
            this.type = type;
            this.event = event;
            this.timestamp = timestamp;
            this.version = version;
        }

        static DistanceCommand add(
                MetricGpsEvent event,
                long timestamp) {
            return new DistanceCommand(
                ADD, event, timestamp, event.filterVersion
            );
        }

        static DistanceCommand tick(long timestamp) {
            return new DistanceCommand(
                TICK, null, timestamp, -1L
            );
        }

        static DistanceCommand reset(
                long timestamp,
                long version) {
            return new DistanceCommand(
                RESET, null, timestamp, version
            );
        }
    }

    // Immutable state managed by Sodium collect()
    private static class DistanceState {
        final long filterVersion;
        final List<List<TimedPosition>> histories;

        DistanceState(long filterVersion) {
            this.filterVersion = filterVersion;
            this.histories = new ArrayList<>();

            for (int i = 0; i < TRACKER_COUNT; i++) {
                histories.add(new ArrayList<TimedPosition>());
            }
        }

        private DistanceState(
                long filterVersion,
                List<List<TimedPosition>> histories) {
            this.filterVersion = filterVersion;
            this.histories = histories;
        }

        // Produce new state for every distance command
        DistanceState next(DistanceCommand command) {

            // Start new history after filter change
            if (command.type == DistanceCommand.RESET) {
                return new DistanceState(command.version);
            }

            // Ignore events from previous filters
            if (command.type == DistanceCommand.ADD
                    && command.version != filterVersion) {
                return this;
            }

            long cutoff = command.timestamp - WINDOW_MILLIS;

            // Copy history so the previous state is unchanged
            List<List<TimedPosition>> updated =
                new ArrayList<>();

            for (int i = 0; i < TRACKER_COUNT; i++) {
                List<TimedPosition> recent =
                    new ArrayList<>();

                for (TimedPosition position : histories.get(i)) {
                    if (position.timestamp >= cutoff) {
                        recent.add(position);
                    }
                }

                updated.add(recent);
            }

            // Add the new valid position
            if (command.type == DistanceCommand.ADD) {
                int index = getTrackerIndex(command.event.name);

                if (index >= 0 && index < TRACKER_COUNT) {
                    updated.get(index).add(
                        new TimedPosition(
                            command.event,
                            command.timestamp
                        )
                    );
                }
            }

            return new DistanceState(filterVersion, updated);
        }

        // Calculate distance for one tracker
        double getDistance(int index) {
            List<TimedPosition> positions =
                histories.get(index);

            double total = 0.0;

            for (int i = 1; i < positions.size(); i++) {
                total += calculateDistance(
                    positions.get(i - 1).position,
                    positions.get(i).position
                );
            }

            return total;
        }
    }

    // Convert Tracker0 into array index 0
    private static int getTrackerIndex(String name) {
        if (name == null || !name.startsWith("Tracker")) {
            return -1;
        }

        try {
            return Integer.parseInt(name.substring(7));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    // Calculate 3D distance in meters
    private static double calculateDistance(
            MetricGpsEvent previous,
            MetricGpsEvent current) {

        double lat1 = Math.toRadians(previous.latitude);
        double lat2 = Math.toRadians(current.latitude);

        double lon1 = Math.toRadians(previous.longitude);
        double lon2 = Math.toRadians(current.longitude);

        double averageLatitude = (lat1 + lat2) / 2.0;

        double dx = EARTH_RADIUS_METERS
            * Math.cos(averageLatitude)
            * (lon2 - lon1);

        double dy = EARTH_RADIUS_METERS * (lat2 - lat1);

        double dz = current.altitudeMeters
            - previous.altitudeMeters;

        return Math.sqrt(
            dx * dx + dy * dy + dz * dz
        );
    }

    // Display distances calculated by the FRP stream
    private static void updateDistanceLabels(
            DistanceState state,
            JLabel[] labels) {

        for (int i = 0; i < TRACKER_COUNT; i++) {
            double distance = state.getDistance(i);
            long rounded = (long) Math.ceil(distance);

            labels[i].setText(
                "Tracker" + (i + 1)
                    + ": " + rounded + " m"
            );
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            // Main GUI window
            JFrame frame = new JFrame("GPS Tracking System");

            frame.setSize(1000, 900);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            JLabel title = new JLabel(
                "GPS Tracking System",
                SwingConstants.CENTER
            );

            title.setFont(new Font("Arial", Font.BOLD, 24));
            frame.add(title, BorderLayout.NORTH);

            // Ten tracker coordinate displays
            JPanel trackerPanel = new JPanel(
                new GridLayout(TRACKER_COUNT, 1)
            );

            trackerPanel.setBorder(
                BorderFactory.createTitledBorder(
                    "GPS Tracker Coordinates"
                )
            );

            JLabel[] trackerLabels =
                new JLabel[TRACKER_COUNT];

            for (int i = 0; i < TRACKER_COUNT; i++) {
                trackerLabels[i] = new JLabel(
                    "Tracker" + (i + 1)
                        + " | Waiting for GPS data..."
                );

                trackerPanel.add(trackerLabels[i]);
            }

            // Five-minute distance display
            JPanel distancePanel = new JPanel(
                new GridLayout(5, 2, 10, 5)
            );

            distancePanel.setBorder(
                BorderFactory.createTitledBorder(
                    "Distance Travelled - Last 5 Minutes"
                )
            );

            JLabel[] distanceLabels =
                new JLabel[TRACKER_COUNT];

            for (int i = 0; i < TRACKER_COUNT; i++) {
                distanceLabels[i] = new JLabel(
                    "Tracker" + (i + 1) + ": 0 m"
                );

                distancePanel.add(distanceLabels[i]);
            }

            JPanel mainPanel = new JPanel(new BorderLayout());

            mainPanel.add(trackerPanel, BorderLayout.CENTER);
            mainPanel.add(distancePanel, BorderLayout.SOUTH);

            frame.add(mainPanel, BorderLayout.CENTER);

            // Filter control panel
            JPanel filterPanel = new JPanel(
                new GridLayout(3, 2, 10, 10)
            );

            filterPanel.setBorder(
                BorderFactory.createTitledBorder(
                    "GPS Filter Control"
                )
            );

            JTextField latitudeField =
                new JTextField("-90");

            JTextField longitudeField =
                new JTextField("-180");

            JButton setFilterButton =
                new JButton("Set Filter");

            filterPanel.add(new JLabel("Minimum Latitude:"));
            filterPanel.add(latitudeField);

            filterPanel.add(new JLabel("Minimum Longitude:"));
            filterPanel.add(longitudeField);

            filterPanel.add(setFilterButton);
            filterPanel.add(new JLabel(""));

            JLabel currentLatitudeLabel = new JLabel(
                "Current Minimum Latitude: -90.0"
            );

            JLabel currentLongitudeLabel = new JLabel(
                "Current Minimum Longitude: -180.0"
            );

            JPanel currentFilterPanel = new JPanel(
                new GridLayout(2, 1)
            );

            currentFilterPanel.add(currentLatitudeLabel);
            currentFilterPanel.add(currentLongitudeLabel);

            // Most recent filtered GPS event
            JPanel eventPanel = new JPanel(new BorderLayout());

            eventPanel.setBorder(
                BorderFactory.createTitledBorder(
                    "Most Recent Filtered GPS Event"
                )
            );

            JLabel recentEventLabel = new JLabel(
                "Waiting for GPS event..."
            );

            eventPanel.add(
                recentEventLabel,
                BorderLayout.CENTER
            );

            // Converted altitude display
            JPanel altitudePanel = new JPanel(new BorderLayout());

            altitudePanel.setBorder(
                BorderFactory.createTitledBorder(
                    "Converted Altitude"
                )
            );

            JLabel altitudeLabel = new JLabel(
                "Waiting for altitude data..."
            );

            altitudePanel.add(
                altitudeLabel,
                BorderLayout.CENTER
            );

            // Group filter and event displays
            JPanel bottomPanel = new JPanel();

            bottomPanel.setLayout(
                new BoxLayout(bottomPanel, BoxLayout.Y_AXIS)
            );

            bottomPanel.add(filterPanel);
            bottomPanel.add(currentFilterPanel);
            bottomPanel.add(eventPanel);
            bottomPanel.add(altitudePanel);

            frame.add(bottomPanel, BorderLayout.SOUTH);

            // Clear recent event after 3 seconds
            Timer clearTimer = new Timer(3000, e -> {
                recentEventLabel.setText("");
            });

            clearTimer.setRepeats(false);

            // Sodium cell for filter settings
            CellSink<FilterSettings> filterSettings =
                new CellSink<>(
                    new FilterSettings(-90.0, -180.0, 0L)
                );

            final long[] currentFilterVersion = {0L};

            // NEW: Event input for distance calculations
            StreamSink<DistanceCommand> distanceCommands =
                new StreamSink<>();

            // NEW: Sodium collect() manages position history
            Stream<DistanceState> distanceUpdates =
                distanceCommands.collect(
                    new DistanceState(0L),
                    (command, previousState) -> {
                        DistanceState newState =
                            previousState.next(command);

                        return new Tuple2<>(
                            newState,
                            newState
                        );
                    }
                );

            // Distance updates are sent to the GUI
            Listener distanceListener =
                distanceUpdates.listen(state -> {
                    SwingUtilities.invokeLater(() -> {
                        if (state.filterVersion
                                != currentFilterVersion[0]) {
                            return;
                        }

                        updateDistanceLabels(
                            state,
                            distanceLabels
                        );
                    });
                });

            // Check expired positions every second
            Timer distanceTimer = new Timer(1000, e -> {
                distanceCommands.send(
                    DistanceCommand.tick(
                        System.currentTimeMillis()
                    )
                );
            });

            distanceTimer.start();

            // Handle Set Filter button
            setFilterButton.addActionListener(e -> {
                try {
                    double latitude = Double.parseDouble(
                        latitudeField.getText().trim()
                    );

                    double longitude = Double.parseDouble(
                        longitudeField.getText().trim()
                    );

                    if (!Double.isFinite(latitude)
                            || !Double.isFinite(longitude)
                            || latitude < -90
                            || latitude > 90
                            || longitude < -180
                            || longitude > 180) {
                        throw new NumberFormatException();
                    }

                    long newVersion =
                        currentFilterVersion[0] + 1L;

                    FilterSettings newSettings =
                        new FilterSettings(
                            latitude,
                            longitude,
                            newVersion
                        );

                    // Update Sodium filter
                    filterSettings.send(newSettings);
                    currentFilterVersion[0] = newVersion;

                    currentLatitudeLabel.setText(
                        "Current Minimum Latitude: " + latitude
                    );

                    currentLongitudeLabel.setText(
                        "Current Minimum Longitude: " + longitude
                    );

                    // Clear old event and altitude
                    clearTimer.stop();
                    recentEventLabel.setText("");
                    altitudeLabel.setText(
                        "Waiting for altitude data..."
                    );

                    // Reset history using an FRP command
                    distanceCommands.send(
                        DistanceCommand.reset(
                            System.currentTimeMillis(),
                            newVersion
                        )
                    );

                    // Show zero immediately
                    for (int i = 0; i < TRACKER_COUNT; i++) {
                        distanceLabels[i].setText(
                            "Tracker" + (i + 1) + ": 0 m"
                        );
                    }

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(
                        frame,
                        "Please enter valid coordinates.\n"
                            + "Latitude: -90 to 90\n"
                            + "Longitude: -180 to 180",
                        "Invalid Input",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            });

            // Start GPS service
            service = new GpsService();

            Stream<GpsEvent>[] streams =
                service.getEventStreams();

            listeners = new Listener[streams.length + 1];

            // Simplified displays using Sodium map()
            for (int i = 0;
                    i < streams.length && i < TRACKER_COUNT;
                    i++) {

                final int index = i;

                Stream<String> simplifiedStream =
                    streams[i].map(event ->
                        "Tracker" + (index + 1)
                            + " | Latitude: " + event.latitude
                            + " | Longitude: " + event.longitude
                    );

                listeners[i] = simplifiedStream.listen(data -> {
                    SwingUtilities.invokeLater(() -> {
                        trackerLabels[index].setText(data);
                    });
                });
            }

            // Combine all GPS input streams
            if (streams.length > 0) {

                Stream<GpsEvent> combinedStream = streams[0];

                for (int i = 1; i < streams.length; i++) {
                    combinedStream =
                        combinedStream.orElse(streams[i]);
                }

                // Read current filter for each event
                Stream<Optional<FilteredGpsEvent>> checkedStream =
                    combinedStream.snapshot(
                        filterSettings,
                        (event, settings) ->
                            settings.matches(event)
                                ? Optional.of(
                                    new FilteredGpsEvent(
                                        event,
                                        settings.version
                                    )
                                )
                                : Optional.empty()
                    );

                // Remove non-matching events
                Stream<FilteredGpsEvent> filteredStream =
                    checkedStream
                        .filter(result -> result.isPresent())
                        .map(result -> result.get());

                // Convert altitude from feet to meters
                Stream<MetricGpsEvent> metricStream =
                    filteredStream.map(
                        event -> new MetricGpsEvent(event)
                    );

                // Handle new valid GPS positions
                listeners[streams.length] =
                    metricStream.listen(event -> {

                        long receivedTime =
                            System.currentTimeMillis();

                        SwingUtilities.invokeLater(() -> {

                            // Ignore events from previous filter
                            if (event.filterVersion
                                    != currentFilterVersion[0]) {
                                return;
                            }

                            int index =
                                getTrackerIndex(event.name);

                            if (index < 0
                                    || index >= TRACKER_COUNT) {
                                return;
                            }

                            // Display four comma-separated values
                            String latestEvent =
                                "Tracker" + (index + 1)
                                    + "," + event.latitude
                                    + "," + event.longitude
                                    + "," + event.originalAltitudeFeet;

                            recentEventLabel.setText(latestEvent);
                            clearTimer.restart();

                            // Display converted altitude
                            altitudeLabel.setText(
                                "Tracker" + (index + 1)
                                    + " | Altitude: "
                                    + String.format(
                                        Locale.US,
                                        "%.2f m",
                                        event.altitudeMeters
                                    )
                            );

                            // NEW: Send position into FRP
                            // distance state calculation
                            distanceCommands.send(
                                DistanceCommand.add(
                                    event,
                                    receivedTime
                                )
                            );
                        });
                    });
            }

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

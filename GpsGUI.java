import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
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

public class GpsGUI {

    private static final int TRACKER_COUNT = 10;
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double FEET_TO_METERS = 0.3048;

    private static Listener[] listeners;
    private static GpsService service;

    // GPS data with altitude in meters
    private static class MetricGpsEvent {
        final String name;
        final double latitude;
        final double longitude;
        final double altitudeMeters;

        MetricGpsEvent(
                String name,
                double latitude,
                double longitude,
                double altitudeMeters) {

            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitudeMeters = altitudeMeters;
        }
    }

    // Store latitude and longitude filter limits
    private static class FilterSettings {
        final double latitude;
        final double longitude;

        FilterSettings(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        boolean matches(GpsEvent event) {
            return event.latitude >= latitude
                && event.longitude >= longitude;
        }
    }

    // Convert tracker name to zero-based index
    private static int getTrackerIndex(String name) {
        return Integer.parseInt(
            name.replace("Tracker", "")
        );
    }

    // Calculate 3D distance between two GPS positions
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            JFrame frame = new JFrame("GPS Tracking System");
            frame.setSize(1000, 900);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            // Create title
            JLabel title = new JLabel(
                "GPS Tracking System",
                SwingConstants.CENTER
            );
            title.setFont(new Font("Arial", Font.BOLD, 24));
            frame.add(title, BorderLayout.NORTH);

            // Create tracker coordinate panel
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

            // Create cumulative distance panel
            JPanel distancePanel = new JPanel(
                new GridLayout(5, 2, 10, 5)
            );

            distancePanel.setBorder(
                BorderFactory.createTitledBorder(
                    "Total Distance Travelled"
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

            // Group coordinate and distance panels
            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.add(trackerPanel, BorderLayout.CENTER);
            mainPanel.add(distancePanel, BorderLayout.SOUTH);

            frame.add(mainPanel, BorderLayout.CENTER);

            // Create filter control panel
            JPanel filterPanel = new JPanel(
                new GridLayout(3, 2, 10, 10)
            );

            filterPanel.setBorder(
                BorderFactory.createTitledBorder(
                    "GPS Filter Control"
                )
            );

            JTextField latitudeField = new JTextField("-90");
            JTextField longitudeField = new JTextField("-180");

            JButton setFilterButton = new JButton("Set Filter");

            filterPanel.add(new JLabel("Minimum Latitude:"));
            filterPanel.add(latitudeField);

            filterPanel.add(new JLabel("Minimum Longitude:"));
            filterPanel.add(longitudeField);

            filterPanel.add(setFilterButton);
            filterPanel.add(new JLabel(""));

            // Show current filter limits
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

            // Create most recent event display
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

            // Create altitude conversion display
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

            // Group bottom panels
            JPanel bottomPanel = new JPanel();

            bottomPanel.setLayout(
                new BoxLayout(
                    bottomPanel,
                    BoxLayout.Y_AXIS
                )
            );

            bottomPanel.add(filterPanel);
            bottomPanel.add(currentFilterPanel);
            bottomPanel.add(eventPanel);
            bottomPanel.add(altitudePanel);

            frame.add(bottomPanel, BorderLayout.SOUTH);

            // Clear the latest event after 3 seconds
            Timer clearTimer = new Timer(3000, e -> {
                recentEventLabel.setText("");
            });

            clearTimer.setRepeats(false);

            // Store filter settings using Sodium
            CellSink<FilterSettings> filterSettings =
                new CellSink<>(
                    new FilterSettings(-90.0, -180.0)
                );

            // Store each tracker's previous valid position
            MetricGpsEvent[] previousPositions =
                new MetricGpsEvent[TRACKER_COUNT];

            // NEW: Store cumulative distance for each tracker
            double[] totalDistances =
                new double[TRACKER_COUNT];

            // Handle filter changes
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

                    FilterSettings newSettings =
                        new FilterSettings(latitude, longitude);

                    // Update the Sodium filter
                    filterSettings.send(newSettings);

                    currentLatitudeLabel.setText(
                        "Current Minimum Latitude: " + latitude
                    );

                    currentLongitudeLabel.setText(
                        "Current Minimum Longitude: " + longitude
                    );

                    clearTimer.stop();
                    recentEventLabel.setText("");

                    altitudeLabel.setText(
                        "Waiting for altitude data..."
                    );

                    // Reset distance when the filter changes
                    for (int i = 0; i < TRACKER_COUNT; i++) {
                        previousPositions[i] = null;
                        totalDistances[i] = 0.0;

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

            listeners = new Listener[streams.length + 2];

            // Simplified displays for ten trackers
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

                listeners[i] =
                    simplifiedStream.listen(data -> {
                        SwingUtilities.invokeLater(() -> {
                            trackerLabels[index].setText(data);
                        });
                    });
            }

            // Combine all GPS streams
            if (streams.length > 0) {
                Stream<GpsEvent> combinedStream = streams[0];

                for (int i = 1; i < streams.length; i++) {
                    combinedStream =
                        combinedStream.orElse(streams[i]);
                }

                // Apply the current GPS filter
                Stream<Optional<GpsEvent>> checkedStream =
                    combinedStream.snapshot(
                        filterSettings,
                        (event, settings) ->
                            settings.matches(event)
                                ? Optional.of(event)
                                : Optional.empty()
                    );

                Stream<GpsEvent> filteredStream =
                    checkedStream
                        .filter(result -> result.isPresent())
                        .map(result -> result.get());

                // Convert altitude from feet to meters
                Stream<MetricGpsEvent> metricStream =
                    filteredStream.map(event ->
                        new MetricGpsEvent(
                            event.name,
                            event.latitude,
                            event.longitude,
                            event.altitude * FEET_TO_METERS
                        )
                    );

                // Display the most recent filtered event
                listeners[streams.length] =
                    filteredStream.listen(event -> {
                        SwingUtilities.invokeLater(() -> {

                            int trackerNumber =
                                getTrackerIndex(event.name) + 1;

                            String latestEvent =
                                "Tracker" + trackerNumber
                                    + "," + event.latitude
                                    + "," + event.longitude
                                    + "," + event.altitude;

                            recentEventLabel.setText(latestEvent);
                            clearTimer.restart();
                        });
                    });

                // Update altitude and cumulative distance
                listeners[streams.length + 1] =
                    metricStream.listen(event -> {
                        SwingUtilities.invokeLater(() -> {

                            int index =
                                getTrackerIndex(event.name);

                            if (index < 0
                                    || index >= TRACKER_COUNT) {
                                return;
                            }

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

                            // Calculate the distance from
                            // the previous valid GPS position
                            if (previousPositions[index] != null) {

                                double segmentDistance =
                                    calculateDistance(
                                        previousPositions[index],
                                        event
                                    );

                                // NEW: Add each segment distance
                                totalDistances[index] +=
                                    segmentDistance;
                            }

                            // Save the current GPS position
                            previousPositions[index] = event;

                            // Round the total distance upwards
                            long roundedTotal =
                                (long) Math.ceil(
                                    totalDistances[index]
                                );

                            // Update the GUI
                            distanceLabels[index].setText(
                                "Tracker" + (index + 1)
                                    + ": " + roundedTotal + " m"
                            );
                        });
                    });
            }

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
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

    private static Listener[] listeners;
    private static GpsService service;

    // Store GPS data with altitude in meters
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

    // Store the current GPS filter settings
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("GPS Tracking System");

            frame.setSize(1000, 900);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            // Create window title
            JLabel title = new JLabel(
                "GPS Tracking System",
                SwingConstants.CENTER
            );
            title.setFont(new Font("Arial", Font.BOLD, 24));
            frame.add(title, BorderLayout.NORTH);

            // Create ten tracker displays
            JPanel trackerPanel = new JPanel(
                new GridLayout(10, 1)
            );

            trackerPanel.setBorder(
                BorderFactory.createTitledBorder(
                    "GPS Tracker Coordinates"
                )
            );

            JLabel[] trackerLabels = new JLabel[10];

            for (int i = 0; i < 10; i++) {
                trackerLabels[i] = new JLabel(
                    "Tracker" + (i + 1)
                        + " | Waiting for GPS data..."
                );
                trackerPanel.add(trackerLabels[i]);
            }

            // Create distance display panel
            JPanel distancePanel = new JPanel(
                new GridLayout(5, 2, 10, 5)
            );

            distancePanel.setBorder(
                BorderFactory.createTitledBorder(
                    "Distance Travelled - Last 5 Minutes"
                )
            );

            JLabel[] distanceLabels = new JLabel[10];

            for (int i = 0; i < 10; i++) {
                distanceLabels[i] = new JLabel(
                    "Tracker" + (i + 1) + ": 0 m"
                );

                distancePanel.add(distanceLabels[i]);
            }

            // Group tracker and distance displays
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

            // Display current filter settings
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

            // Create filtered event display
            JPanel eventPanel = new JPanel(new BorderLayout());

            eventPanel.setBorder(
                BorderFactory.createTitledBorder(
                    "Most Recent Filtered GPS Event"
                )
            );

            JLabel recentEventLabel = new JLabel(
                "Waiting for GPS event..."
            );

            eventPanel.add(recentEventLabel, BorderLayout.CENTER);

            // Create converted altitude display
            JPanel altitudePanel = new JPanel(
                new BorderLayout()
            );

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
                new BoxLayout(bottomPanel, BoxLayout.Y_AXIS)
            );

            bottomPanel.add(filterPanel);
            bottomPanel.add(currentFilterPanel);
            bottomPanel.add(eventPanel);
            bottomPanel.add(altitudePanel);

            frame.add(bottomPanel, BorderLayout.SOUTH);

            // Clear latest GPS event after 3 seconds
            Timer clearTimer = new Timer(3000, e -> {
                recentEventLabel.setText("");
            });

            clearTimer.setRepeats(false);

            // Store filter settings with Sodium CellSink
            CellSink<FilterSettings> filterSettings =
                new CellSink<>(
                    new FilterSettings(-90.0, -180.0)
                );

            // Handle filter button
            setFilterButton.addActionListener(e -> {
                try {
                    double latitude = Double.parseDouble(
                        latitudeField.getText().trim()
                    );

                    double longitude = Double.parseDouble(
                        longitudeField.getText().trim()
                    );

                    // Validate latitude and longitude
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

                    // Update Sodium filter settings
                    filterSettings.send(newSettings);

                    // Update filter labels
                    currentLatitudeLabel.setText(
                        "Current Minimum Latitude: " + latitude
                    );

                    currentLongitudeLabel.setText(
                        "Current Minimum Longitude: " + longitude
                    );

                    // Clear previous event
                    clearTimer.stop();
                    recentEventLabel.setText("");
                    altitudeLabel.setText(
                        "Waiting for altitude data..."
                    );

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

            // Get GPS event streams
            Stream<GpsEvent>[] streams =
                service.getEventStreams();

            // Keep listeners active
            listeners = new Listener[streams.length + 2];

            // Process individual tracker streams
            for (int i = 0;
                    i < streams.length && i < 10;
                    i++) {

                final int index = i;

                // Remove altitude using Sodium map()
                Stream<String> simplifiedStream =
                    streams[i].map(event ->
                        "Tracker" + (index + 1)
                            + " | Latitude: " + event.latitude
                            + " | Longitude: " + event.longitude
                    );

                // Update tracker labels
                listeners[i] = simplifiedStream.listen(data -> {
                    SwingUtilities.invokeLater(() -> {
                        trackerLabels[index].setText(data);
                    });
                });
            }

            // Combine all GPS event streams
            if (streams.length > 0) {

                Stream<GpsEvent> combinedStream = streams[0];

                for (int i = 1; i < streams.length; i++) {
                    combinedStream =
                        combinedStream.orElse(streams[i]);
                }

                // Apply current filter settings
                Stream<Optional<GpsEvent>> checkedStream =
                    combinedStream.snapshot(
                        filterSettings,
                        (event, settings) ->
                            settings.matches(event)
                                ? Optional.of(event)
                                : Optional.empty()
                    );

                // Remove events outside the filter range
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
                            event.altitude * 0.3048
                        )
                    );

                // Display most recent filtered event
                listeners[streams.length] =
                    filteredStream.listen(event -> {
                        SwingUtilities.invokeLater(() -> {
                            int trackerNumber =
                                Integer.parseInt(
                                    event.name.replace(
                                        "Tracker", ""
                                    )
                                ) + 1;

                            String latestEvent =
                                "Tracker" + trackerNumber
                                    + "," + event.latitude
                                    + "," + event.longitude
                                    + "," + event.altitude;

                            recentEventLabel.setText(latestEvent);

                            // Restart the 3-second timer
                            clearTimer.restart();
                        });
                    });

                // Display converted altitude in meters
                listeners[streams.length + 1] =
                    metricStream.listen(event -> {
                        SwingUtilities.invokeLater(() -> {
                            int trackerNumber =
                                Integer.parseInt(
                                    event.name.replace(
                                        "Tracker", ""
                                    )
                                ) + 1;

                            altitudeLabel.setText(
                                "Tracker" + trackerNumber
                                    + " | Altitude: "
                                    + String.format(
                                        "%.2f m",
                                        event.altitudeMeters
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

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

    // Stores the current GPS filter settings
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

            frame.setSize(1000, 850);
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

            // Store distance labels for ten trackers
            JLabel[] distanceLabels = new JLabel[10];

            for (int i = 0; i < 10; i++) {
                distanceLabels[i] = new JLabel(
                    "Tracker" + (i + 1) + ": 0 m"
                );

                distancePanel.add(distanceLabels[i]);
            }

            // Group tracker and distance panels
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

            // Group bottom panels
            JPanel bottomPanel = new JPanel();

            bottomPanel.setLayout(
                new BoxLayout(bottomPanel, BoxLayout.Y_AXIS)
            );

            bottomPanel.add(filterPanel);
            bottomPanel.add(currentFilterPanel);
            bottomPanel.add(eventPanel);

            frame.add(bottomPanel, BorderLayout.SOUTH);

            // Clear latest event after 3 seconds
            Timer clearTimer = new Timer(3000, e -> {
                recentEventLabel.setText("");
            });

            clearTimer.setRepeats(false);

            // Store filter limits using Sodium CellSink
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

                    // Validate coordinates
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

                    // Update current filter labels
                    currentLatitudeLabel.setText(
                        "Current Minimum Latitude: " + latitude
                    );

                    currentLongitudeLabel.setText(
                        "Current Minimum Longitude: " + longitude
                    );

                    // Clear previously displayed event
                    clearTimer.stop();
                    recentEventLabel.setText("");

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
            listeners = new Listener[streams.length + 1];

            // Process each tracker separately
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

                // Update individual tracker display
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

                // Check GPS events against filter settings
                Stream<Optional<GpsEvent>> checkedStream =
                    combinedStream.snapshot(
                        filterSettings,
                        (event, settings) ->
                            settings.matches(event)
                                ? Optional.of(event)
                                : Optional.empty()
                    );

                // Remove events outside the permitted range
                Stream<GpsEvent> filteredStream =
                    checkedStream
                        .filter(result -> result.isPresent())
                        .map(result -> result.get());

                // Display filtered GPS events
                listeners[streams.length] =
                    filteredStream.listen(event -> {
                        SwingUtilities.invokeLater(() -> {

                            // Convert tracker number for GUI
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
            }

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

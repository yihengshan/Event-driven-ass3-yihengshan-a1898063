import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

public class GpsGUI {

    private static final int TRACKER_COUNT = 10;
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double FEET_TO_METERS = 0.3048;
    private static final long WINDOW_MILLIS = 300000L;

    private static Listener[] listeners;
    private static GpsService service;

    // Store the filter limits and their version
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

    // Store a filtered event and its filter version
    private static class FilteredGpsEvent {
        final GpsEvent event;
        final long filterVersion;

        FilteredGpsEvent(GpsEvent event, long filterVersion) {
            this.event = event;
            this.filterVersion = filterVersion;
        }
    }

    // Store GPS data with altitude in meters
    private static class MetricGpsEvent {
        final String name;
        final double latitude;
        final double longitude;
        final double altitudeMeters;
        final double originalAltitudeFeet;
        final long filterVersion;

        MetricGpsEvent(
                FilteredGpsEvent filteredEvent) {

            GpsEvent event = filteredEvent.event;

            this.name = event.name;
            this.latitude = event.latitude;
            this.longitude = event.longitude;
            this.altitudeMeters =
                event.altitude * FEET_TO_METERS;
            this.originalAltitudeFeet = event.altitude;
            this.filterVersion = filteredEvent.filterVersion;
        }
    }

    // Store a position and its arrival time
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

    // Convert Tracker0 to index 0, etc.
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

    // Calculate three-dimensional distance in meters
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

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // Remove positions outside the five-minute window
    private static void removeExpiredPositions(
            Deque<TimedPosition> positions,
            long currentTime) {

        long cutoffTime = currentTime - WINDOW_MILLIS;

        while (!positions.isEmpty()
                && positions.peekFirst().timestamp < cutoffTime) {
            positions.removeFirst();
        }
    }

    // Calculate distance between known positions in the window
    private static double calculateWindowDistance(
            Deque<TimedPosition> positions) {

        double totalDistance = 0.0;
        TimedPosition previous = null;

        for (TimedPosition current : positions) {
            if (previous != null) {
                totalDistance += calculateDistance(
                    previous.position,
                    current.position
                );
            }

            previous = current;
        }

        return totalDistance;
    }

    // Refresh all tracker distance displays
    private static void updateDistanceDisplays(
            List<Deque<TimedPosition>> positionHistory,
            JLabel[] distanceLabels,
            long currentTime) {

        for (int i = 0; i < TRACKER_COUNT; i++) {

            Deque<TimedPosition> positions =
                positionHistory.get(i);

            removeExpiredPositions(positions, currentTime);

            double distance =
                calculateWindowDistance(positions);

            long roundedDistance =
                (long) Math.ceil(distance);

            distanceLabels[i].setText(
                "Tracker" + (i + 1)
                    + ": " + roundedDistance + " m"
            );
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            // Create the main window
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

            // Create ten tracker coordinate displays
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

            // Create distance display panel
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

            // Create GPS filter control panel
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

            // Display current filter limits
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

            // Create the most recent filtered event display
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

            altitudePanel.add(altitudeLabel, BorderLayout.CENTER);

            // Group filter and event panels
            JPanel bottomPanel = new JPanel();

            bottomPanel.setLayout(
                new BoxLayout(bottomPanel, BoxLayout.Y_AXIS)
            );

            bottomPanel.add(filterPanel);
            bottomPanel.add(currentFilterPanel);
            bottomPanel.add(eventPanel);
            bottomPanel.add(altitudePanel);

            frame.add(bottomPanel, BorderLayout.SOUTH);

            // Clear the latest GPS event after three seconds
            Timer clearTimer = new Timer(3000, e -> {
                recentEventLabel.setText("");
            });

            clearTimer.setRepeats(false);

            // Save filter settings in a Sodium cell
            CellSink<FilterSettings> filterSettings =
                new CellSink<>(
                    new FilterSettings(-90.0, -180.0, 0L)
                );

            // Track the currently active filter version
            final long[] currentFilterVersion = {0L};

            // Keep a separate position history for each tracker
            List<Deque<TimedPosition>> positionHistory =
                new ArrayList<>();

            for (int i = 0; i < TRACKER_COUNT; i++) {
                positionHistory.add(
                    new ArrayDeque<TimedPosition>()
                );
            }

            // Refresh expired positions every second
            Timer distanceTimer = new Timer(1000, e -> {
                updateDistanceDisplays(
                    positionHistory,
                    distanceLabels,
                    System.currentTimeMillis()
                );
            });

            distanceTimer.start();

            // Update the filter when the button is clicked
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

                    // Increment filter version for valid changes
                    long newVersion =
                        currentFilterVersion[0] + 1L;

                    FilterSettings newSettings =
                        new FilterSettings(
                            latitude,
                            longitude,
                            newVersion
                        );

                    // Apply the new settings in Sodium
                    filterSettings.send(newSettings);

                    currentFilterVersion[0] = newVersion;

                    currentLatitudeLabel.setText(
                        "Current Minimum Latitude: " + latitude
                    );

                    currentLongitudeLabel.setText(
                        "Current Minimum Longitude: " + longitude
                    );

                    // Clear old event displays
                    clearTimer.stop();
                    recentEventLabel.setText("");

                    altitudeLabel.setText(
                        "Waiting for altitude data..."
                    );

                    // Clear all histories from the old filter
                    for (int i = 0; i < TRACKER_COUNT; i++) {
                        positionHistory.get(i).clear();

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

            // Start the provided GPS service
            service = new GpsService();

            Stream<GpsEvent>[] streams =
                service.getEventStreams();

            // Save all Sodium listeners
            listeners = new Listener[streams.length + 1];

            // Simplified tracker displays using Sodium map()
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

            // Process the combined GPS stream
            if (streams.length > 0) {

                Stream<GpsEvent> combinedStream =
                    streams[0];

                for (int i = 1; i < streams.length; i++) {
                    combinedStream =
                        combinedStream.orElse(streams[i]);
                }

                // Take a snapshot of the current filter
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

                // Remove events that fail the filter
                Stream<FilteredGpsEvent> filteredStream =
                    checkedStream
                        .filter(result -> result.isPresent())
                        .map(result -> result.get());

                // Convert altitude using Sodium map()
                Stream<MetricGpsEvent> metricStream =
                    filteredStream.map(
                        event -> new MetricGpsEvent(event)
                    );

                // Update events and distances
                listeners[streams.length] =
                    metricStream.listen(event -> {

                        // Record when this GPS event arrived
                        long receivedTime =
                            System.currentTimeMillis();

                        SwingUtilities.invokeLater(() -> {

                            // Ignore events from an older filter
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

                            // Display latest event in original units
                            String latestEvent =
                                "Tracker" + (index + 1)
                                    + "," + event.latitude
                                    + "," + event.longitude
                                    + "," + event.originalAltitudeFeet;

                            recentEventLabel.setText(latestEvent);
                            clearTimer.restart();

                            // Display converted altitude in meters
                            altitudeLabel.setText(
                                "Tracker" + (index + 1)
                                    + " | Altitude: "
                                    + String.format(
                                        Locale.US,
                                        "%.2f m",
                                        event.altitudeMeters
                                    )
                            );

                            // Store the valid GPS position
                            Deque<TimedPosition> positions =
                                positionHistory.get(index);

                            positions.addLast(
                                new TimedPosition(
                                    event,
                                    receivedTime
                                )
                            );

                            // Update the five-minute distance
                            updateDistanceDisplays(
                                positionHistory,
                                distanceLabels,
                                System.currentTimeMillis()
                            );
                        });
                    });
            }

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import nz.sodium.Listener;
import nz.sodium.Stream;

public class GpsGUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("GPS Tracking System");

            frame.setSize(900, 750);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            // Create title
            JLabel title = new JLabel(
                "GPS Tracking System",
                SwingConstants.CENTER
            );
            title.setFont(new Font("Arial", Font.BOLD, 24));
            frame.add(title, BorderLayout.NORTH);

            // Create tracker panel
            JPanel trackerPanel = new JPanel(new GridLayout(10, 1));
            JLabel[] trackerLabels = new JLabel[10];

            for (int i = 0; i < 10; i++) {
                trackerLabels[i] = new JLabel(
                    "Tracker" + (i + 1)
                    + " | Waiting for GPS data..."
                );
                trackerPanel.add(trackerLabels[i]);
            }

            frame.add(trackerPanel, BorderLayout.CENTER);

            // Create filter control panel
            JPanel filterPanel = new JPanel(new GridLayout(3, 2, 10, 10));
            filterPanel.setBorder(
                BorderFactory.createTitledBorder("GPS Filter Control")
            );

            JTextField latitudeField = new JTextField("0");
            JTextField longitudeField = new JTextField("0");

            JButton setFilterButton = new JButton("Set Filter");

            JLabel currentLatitudeLabel =
                new JLabel("Current Latitude: Not set");

            JLabel currentLongitudeLabel =
                new JLabel("Current Longitude: Not set");

            filterPanel.add(new JLabel("Latitude:"));
            filterPanel.add(latitudeField);

            filterPanel.add(new JLabel("Longitude:"));
            filterPanel.add(longitudeField);

            filterPanel.add(setFilterButton);
            filterPanel.add(new JLabel(""));

            // Handle filter button
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
                            || latitude < -90 || latitude > 90
                            || longitude < -180 || longitude > 180) {
                        throw new NumberFormatException();
                    }

                    currentLatitudeLabel.setText(
                        "Current Latitude: " + latitude
                    );

                    currentLongitudeLabel.setText(
                        "Current Longitude: " + longitude
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

            // Display current filter settings
            JPanel currentFilterPanel =
                new JPanel(new GridLayout(2, 1));

            currentFilterPanel.add(currentLatitudeLabel);
            currentFilterPanel.add(currentLongitudeLabel);

            // Create recent event panel
            JPanel eventPanel = new JPanel(new BorderLayout());

            eventPanel.setBorder(
                BorderFactory.createTitledBorder(
                    "Most Recent GPS Event"
                )
            );

            JLabel recentEventLabel = new JLabel(
                "Waiting for GPS event..."
            );

            eventPanel.add(recentEventLabel, BorderLayout.CENTER);

            // Group bottom panels
            JPanel bottomPanel = new JPanel();
            bottomPanel.setLayout(
                new javax.swing.BoxLayout(
                    bottomPanel,
                    javax.swing.BoxLayout.Y_AXIS
                )
            );

            bottomPanel.add(filterPanel);
            bottomPanel.add(currentFilterPanel);
            bottomPanel.add(eventPanel);

            frame.add(bottomPanel, BorderLayout.SOUTH);

            // Timer to clear recent event after 3 seconds
            Timer clearTimer = new Timer(3000, e -> {
                recentEventLabel.setText("");
            });
            clearTimer.setRepeats(false);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Start GPS service
            GpsService service = new GpsService();
            Stream<GpsEvent>[] streams = service.getEventStreams();

            // Keep listeners active
            Listener[] listeners = new Listener[streams.length + 1];

            // Process each tracker
            for (int i = 0; i < streams.length && i < 10; i++) {
                final int index = i;

                // Remove altitude using Sodium map()
                Stream<String> simplifiedStream =
                    streams[i].map(event ->
                        "Tracker" + (index + 1)
                        + " | Latitude: " + event.latitude
                        + " | Longitude: " + event.longitude
                    );

                // Update tracker display
                listeners[i] = simplifiedStream.listen(data -> {
                    SwingUtilities.invokeLater(() -> {
                        trackerLabels[index].setText(data);
                    });
                });
            }

            // Combine all GPS streams using orElse()
            if (streams.length > 0) {
                Stream<GpsEvent> combinedStream = streams[0];

                for (int i = 1; i < streams.length; i++) {
                    combinedStream =
                        combinedStream.orElse(streams[i]);
                }

                // Display most recent GPS event
                listeners[streams.length] =
                    combinedStream.listen(event -> {
                        SwingUtilities.invokeLater(() -> {
                            int trackerNumber =
                                Integer.parseInt(
                                    event.name.replace("Tracker", "")
                                ) + 1;

                            String latestEvent =
                                "Tracker" + trackerNumber
                                + "," + event.latitude
                                + "," + event.longitude
                                + "," + event.altitude;

                            recentEventLabel.setText(latestEvent);
                            clearTimer.restart();
                        });
                    });
            }
        });
    }
}

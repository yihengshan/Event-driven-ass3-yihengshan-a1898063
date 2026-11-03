
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import nz.sodium.Listener;
import nz.sodium.Stream;

public class GpsGUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("GPS Tracking System");

            frame.setSize(850, 650);
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
            JPanel mainPanel = new JPanel(new GridLayout(10, 1));
            JLabel[] trackerLabels = new JLabel[10];

            for (int i = 0; i < 10; i++) {
                trackerLabels[i] = new JLabel(
                    "Tracker" + (i + 1)
                    + " | Waiting for GPS data..."
                );
                mainPanel.add(trackerLabels[i]);
            }

            frame.add(mainPanel, BorderLayout.CENTER);

            // Create latest event panel
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
            frame.add(eventPanel, BorderLayout.SOUTH);

            // Clear latest event after 3 seconds
            Timer clearTimer = new Timer(3000, e -> {
                recentEventLabel.setText("");
            });
            clearTimer.setRepeats(false);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Start GPS service
            GpsService service = new GpsService();
            Stream<GpsEvent>[] streams = service.getEventStreams();

            // Keep the listeners active
            Listener[] listeners = new Listener[streams.length + 1];

            // Process each tracker separately
            for (int i = 0; i < streams.length && i < 10; i++) {
                final int index = i;

                // Remove altitude using Sodium map()
                Stream<String> simplifiedStream =
                    streams[i].map(event ->
                        "Tracker" + (index + 1)
                        + " | Latitude: " + event.latitude
                        + " | Longitude: " + event.longitude
                    );

                // Update the tracker display
                listeners[i] = simplifiedStream.listen(data -> {
                    SwingUtilities.invokeLater(() -> {
                        trackerLabels[index].setText(data);
                    });
                });
            }

            // Merge all GPS event streams
            Stream<GpsEvent> combinedStream = streams[0];

            for (int i = 1; i < streams.length; i++) {
                combinedStream = combinedStream.orElse(streams[i]);
            }

            // Listen to the combined stream
            listeners[streams.length] =
                combinedStream.listen(event -> {
                    SwingUtilities.invokeLater(() -> {
                        String trackerName = event.name;

                        // Change Tracker0 to Tracker1, etc.
                        int trackerNumber = Integer.parseInt(
                            trackerName.replace("Tracker", "")
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
        });
    }
}

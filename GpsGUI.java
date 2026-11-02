import javax.swing.*;
import java.awt.*;
import nz.sodium.*;

public class GpsGUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("GPS Tracking System");

            frame.setSize(850, 600);
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

            // Display Tracker1 to Tracker10
            for (int i = 0; i < 10; i++) {
                trackerLabels[i] = new JLabel(
                    "Tracker" + (i + 1)
                    + " | Waiting for GPS data..."
                );
                mainPanel.add(trackerLabels[i]);
            }

            frame.add(mainPanel, BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Start GPS service
            GpsService service = new GpsService();

            // Get GPS event streams
            Stream<GpsEvent>[] streams = service.getEventStreams();

            // Store listeners
            Listener[] listeners = new Listener[streams.length];

            // Listen to GPS events
            for (int i = 0; i < streams.length && i < 10; i++) {
                final int index = i;

                listeners[i] = streams[i].listen(event -> {
                    SwingUtilities.invokeLater(() -> {
                        trackerLabels[index].setText(
                            "Tracker" + (index + 1)
                            + " | Latitude: " + event.latitude
                            + " | Longitude: " + event.longitude
                            + " | Altitude: " + event.altitude
                        );
                    });
                });
            }
        });
    }
}


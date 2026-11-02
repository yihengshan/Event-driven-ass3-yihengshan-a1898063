import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import nz.sodium.Listener;
import nz.sodium.Stream;

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

            // Process GPS events
            for (int i = 0; i < streams.length && i < 10; i++) {
                final int index = i;

                // Remove altitude using Sodium map()
                Stream<String> simplifiedStream =
                    streams[i].map(event ->
                        "Tracker" + (index + 1)
                        + " | Latitude: " + event.latitude
                        + " | Longitude: " + event.longitude
                    );

                // Listen to simplified GPS events
                listeners[i] = simplifiedStream.listen(data -> {
                    SwingUtilities.invokeLater(() -> {
                        trackerLabels[index].setText(data);
                    });
                });
            }
        });
    }
}





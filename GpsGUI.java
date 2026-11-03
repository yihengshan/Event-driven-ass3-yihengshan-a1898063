
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
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

            // Create the most recent event display
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

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Start GPS service
            GpsService service = new GpsService();
            Stream<GpsEvent>[] streams = service.getEventStreams();

            Listener[] listeners = new Listener[streams.length];

            // Listen to each tracker
            for (int i = 0; i < streams.length && i < 10; i++) {
                final int index = i;

                // Remove altitude from simplified GPS data
                Stream<String> simplifiedStream =
                    streams[i].map(event ->
                        "Tracker" + (index + 1)
                        + " | Latitude: " + event.latitude
                        + " | Longitude: " + event.longitude
                    );

                // Update tracker and latest event displays
                listeners[i] = simplifiedStream.listen(data -> {
                    SwingUtilities.invokeLater(() -> {
                        trackerLabels[index].setText(data);

                        recentEventLabel.setText(data);
                    });
                });
            }
        });
    }
}

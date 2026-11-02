import javax.swing.*;
import java.awt.*;

public class GpsGUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("GPS Tracking System");

            frame.setSize(800, 600);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            JLabel title = new JLabel(
                "GPS Tracking System",
                SwingConstants.CENTER
            );
            title.setFont(new Font("Arial", Font.BOLD, 24));

            frame.add(title, BorderLayout.NORTH);

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new GridLayout(10, 1));

            for (int i = 0; i < 10; i++) {
                JLabel trackerLabel = new JLabel(
                    "Tracker" + i + " | Waiting for GPS data..."
                );
                mainPanel.add(trackerLabel);
            }

            frame.add(mainPanel, BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}


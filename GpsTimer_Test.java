
import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class GpsTimer_Test {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            JFrame frame = new JFrame(
                "GPS 3-Second Timer Test"
            );

            frame.setSize(650, 250);
            frame.setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
            );
            frame.setLayout(new BorderLayout());

            JLabel title = new JLabel(
                "Most Recent GPS Event",
                SwingConstants.CENTER
            );
            title.setFont(
                new Font("Arial", Font.BOLD, 20)
            );

            frame.add(title, BorderLayout.NORTH);

            JLabel eventLabel = new JLabel(
                "",
                SwingConstants.CENTER
            );

            eventLabel.setFont(
                new Font("Arial", Font.PLAIN, 16)
            );

            frame.add(eventLabel, BorderLayout.CENTER);

            // Clear the event after 3 seconds
            Timer clearTimer = new Timer(3000, e -> {
                eventLabel.setText("");
                System.out.println(
                    "PASS: GPS event cleared after 3 seconds"
                );
            });

            clearTimer.setRepeats(false);

            JButton sendButton = new JButton(
                "Simulate GPS Event"
            );

            sendButton.addActionListener(e -> {

                // Display a simulated GPS event
                eventLabel.setText(
                    "Tracker1,39.977643,116.327703,100.0"
                );

                System.out.println(
                    "GPS event received"
                );

                // Restart the 3-second timer
                clearTimer.restart();
            });

            JPanel buttonPanel = new JPanel();
            buttonPanel.add(sendButton);

            frame.add(buttonPanel, BorderLayout.SOUTH);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}


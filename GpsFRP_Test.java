import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import nz.sodium.CellSink;
import nz.sodium.Listener;
import nz.sodium.Stream;
import nz.sodium.StreamSink;

public class GpsFRP_Test {

    private static int passed = 0;
    private static int failed = 0;

    private interface TestAction {
        void run() throws Exception;
    }

    // Test filter limits
    private static class FilterLimits {
        final double latitude;
        final double longitude;

        FilterLimits(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        boolean matches(GpsEvent event) {
            return event.latitude >= latitude
                && event.longitude >= longitude;
        }
    }

    private static void runTest(
            String name,
            TestAction action) {

        try {
            action.run();
            passed++;
            System.out.println("PASS: " + name);
        } catch (Exception | AssertionError error) {
            failed++;
            System.out.println("FAIL: " + name);
            System.out.println("  " + error);
        }
    }

    private static void assertEquals(
            int expected,
            int actual) {

        if (expected != actual) {
            throw new AssertionError(
                "Expected " + expected
                    + ", got " + actual
            );
        }
    }

    private static void assertTrue(
            boolean condition) {

        if (!condition) {
            throw new AssertionError(
                "Expected true"
            );
        }
    }

    // Create a simulated GPS event
    private static GpsEvent gps(
            int tracker,
            double latitude,
            double longitude,
            double altitude) {

        return new GpsEvent(
            "Tracker" + tracker,
            latitude,
            longitude,
            altitude
        );
    }

    // Build a filtered stream using Sodium primitives
    private static Stream<GpsEvent> createFilteredStream(
            Stream<GpsEvent> input,
            CellSink<FilterLimits> limits) {

        Stream<Optional<GpsEvent>> checked =
            input.snapshot(
                limits,
                (event, settings) ->
                    settings.matches(event)
                        ? Optional.of(event)
                        : Optional.empty()
            );

        return checked
            .filter(result -> result.isPresent())
            .map(result -> result.get());
    }

    public static void main(String[] args) {

        System.out.println("GPS FRP Integration Tests");
        System.out.println("-------------------------");

        // Test 1: No GPS events
        runTest("Zero GPS events", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = input.listen(
                event -> received.add(event)
            );

            try {
                assertEquals(0, received.size());
            } finally {
                listener.unlisten();
            }
        });

        // Test 2: One GPS event
        runTest("One GPS event", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = input.listen(
                event -> received.add(event)
            );

            try {
                input.send(gps(0, 39.5, 116.4, 100));

                assertEquals(1, received.size());
                assertTrue(
                    received.get(0).name.equals("Tracker0")
                );
            } finally {
                listener.unlisten();
            }
        });

        // Test 3: Two GPS events
        runTest("Two GPS events", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = input.listen(
                event -> received.add(event)
            );

            try {
                input.send(gps(0, 39.5, 116.4, 100));
                input.send(gps(1, 39.6, 116.5, 120));

                assertEquals(2, received.size());
            } finally {
                listener.unlisten();
            }
        });

        // Test 4: Remove altitude using map()
        runTest("Simplified GPS map", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            Stream<String> simplified = input.map(
                event ->
                    event.name
                        + "," + event.latitude
                        + "," + event.longitude
            );

            List<String> received = new ArrayList<>();

            Listener listener = simplified.listen(
                value -> received.add(value)
            );

            try {
                input.send(gps(0, 39.5, 116.4, 100));

                assertEquals(1, received.size());

                assertTrue(
                    received.get(0).equals(
                        "Tracker0,39.5,116.4"
                    )
                );

                assertTrue(
                    !received.get(0).contains("100")
                );
            } finally {
                listener.unlisten();
            }
        });

        // Test 5: Merge GPS streams with orElse()
        runTest("Merge two GPS streams", () -> {
            StreamSink<GpsEvent> first =
                new StreamSink<>();

            StreamSink<GpsEvent> second =
                new StreamSink<>();

            Stream<GpsEvent> combined =
                first.orElse(second);

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = combined.listen(
                event -> received.add(event)
            );

            try {
                first.send(gps(0, 39.5, 116.4, 100));
                second.send(gps(1, 39.6, 116.5, 120));

                assertEquals(2, received.size());

                assertTrue(
                    received.get(0).name.equals("Tracker0")
                );
                assertTrue(
                    received.get(1).name.equals("Tracker1")
                );
            } finally {
                listener.unlisten();
            }
        });

        // Test 6: Default filter accepts GPS events
        runTest("Default GPS filter", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            CellSink<FilterLimits> limits =
                new CellSink<>(
                    new FilterLimits(-90, -180)
                );

            Stream<GpsEvent> filtered =
                createFilteredStream(input, limits);

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = filtered.listen(
                event -> received.add(event)
            );

            try {
                input.send(gps(0, 39.5, 116.4, 100));
                input.send(gps(1, -34.9, 138.6, 120));

                assertEquals(2, received.size());
            } finally {
                listener.unlisten();
            }
        });

        // Test 7: Latitude filter
        runTest("Latitude filtering", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            CellSink<FilterLimits> limits =
                new CellSink<>(
                    new FilterLimits(39, -180)
                );

            Stream<GpsEvent> filtered =
                createFilteredStream(input, limits);

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = filtered.listen(
                event -> received.add(event)
            );

            try {
                input.send(gps(0, 39.5, 116.4, 100));
                input.send(gps(1, 38.5, 116.4, 100));

                assertEquals(1, received.size());

                assertTrue(
                    received.get(0).name.equals("Tracker0")
                );
            } finally {
                listener.unlisten();
            }
        });

        // Test 8: Longitude filter
        runTest("Longitude filtering", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            CellSink<FilterLimits> limits =
                new CellSink<>(
                    new FilterLimits(-90, 116)
                );

            Stream<GpsEvent> filtered =
                createFilteredStream(input, limits);

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = filtered.listen(
                event -> received.add(event)
            );

            try {
                input.send(gps(0, 39.5, 116.4, 100));
                input.send(gps(1, 39.5, 115.4, 100));

                assertEquals(1, received.size());
            } finally {
                listener.unlisten();
            }
        });

        // Test 9: Both latitude and longitude
        runTest("Combined GPS filtering", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            CellSink<FilterLimits> limits =
                new CellSink<>(
                    new FilterLimits(39, 116)
                );

            Stream<GpsEvent> filtered =
                createFilteredStream(input, limits);

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = filtered.listen(
                event -> received.add(event)
            );

            try {
                input.send(gps(0, 39.5, 116.5, 100));
                input.send(gps(1, 38.5, 116.5, 100));
                input.send(gps(2, 39.5, 115.5, 100));
                input.send(gps(3, 38.5, 115.5, 100));

                assertEquals(1, received.size());
            } finally {
                listener.unlisten();
            }
        });

        // Test 10: Update filter settings dynamically
        runTest("Dynamic filter changes", () -> {
            StreamSink<GpsEvent> input =
                new StreamSink<>();

            CellSink<FilterLimits> limits =
                new CellSink<>(
                    new FilterLimits(-90, -180)
                );

            Stream<GpsEvent> filtered =
                createFilteredStream(input, limits);

            List<GpsEvent> received = new ArrayList<>();

            Listener listener = filtered.listen(
                event -> received.add(event)
            );

            try {
                // First event passes the default filter
                input.send(gps(0, 38.5, 115.5, 100));

                // Change the filter
                limits.send(new FilterLimits(39, 116));

                // This event should now be rejected
                input.send(gps(1, 38.5, 115.5, 100));

                // This event passes the new filter
                input.send(gps(2, 39.5, 116.5, 100));

                assertEquals(2, received.size());

                assertTrue(
                    received.get(1).name.equals("Tracker2")
                );
            } finally {
                listener.unlisten();
            }
        });

        System.out.println("-------------------------");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total: " + (passed + failed));

        if (failed > 0) {
            System.exit(1);
        }
    }
}

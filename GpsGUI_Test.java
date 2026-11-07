
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GpsGUI_Test {

    private static int passed = 0;
    private static int failed = 0;

    private static Class<?> filterClass;
    private static Class<?> filteredEventClass;
    private static Class<?> metricClass;
    private static Class<?> commandClass;
    private static Class<?> stateClass;

    private static Constructor<?> filterConstructor;
    private static Constructor<?> filteredEventConstructor;
    private static Constructor<?> metricConstructor;
    private static Constructor<?> stateConstructor;

    private static Method matchesMethod;
    private static Method distanceMethod;
    private static Method addMethod;
    private static Method tickMethod;
    private static Method resetMethod;
    private static Method nextMethod;
    private static Method getDistanceMethod;

    // Allow each test to report its result
    private interface TestAction {
        void run() throws Exception;
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

    // Compare two numbers with a tolerance
    private static void assertNear(
            double expected,
            double actual,
            double tolerance) {

        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(
                "Expected " + expected
                    + ", but got " + actual
            );
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError(
                "Expected true, but got false"
            );
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError(
                "Expected false, but got true"
            );
        }
    }

    // Access the actual private classes in GpsGUI
    private static void setUp() throws Exception {

        Class<?> guiClass = Class.forName("GpsGUI");

        filterClass =
            Class.forName("GpsGUI$FilterSettings");

        filteredEventClass =
            Class.forName("GpsGUI$FilteredGpsEvent");

        metricClass =
            Class.forName("GpsGUI$MetricGpsEvent");

        commandClass =
            Class.forName("GpsGUI$DistanceCommand");

        stateClass =
            Class.forName("GpsGUI$DistanceState");

        filterConstructor = filterClass.getDeclaredConstructor(
            double.class,
            double.class,
            long.class
        );
        filterConstructor.setAccessible(true);

        filteredEventConstructor =
            filteredEventClass.getDeclaredConstructor(
                GpsEvent.class,
                long.class
            );
        filteredEventConstructor.setAccessible(true);

        metricConstructor =
            metricClass.getDeclaredConstructor(
                filteredEventClass
            );
        metricConstructor.setAccessible(true);

        stateConstructor =
            stateClass.getDeclaredConstructor(long.class);
        stateConstructor.setAccessible(true);

        matchesMethod = filterClass.getDeclaredMethod(
            "matches",
            GpsEvent.class
        );
        matchesMethod.setAccessible(true);

        distanceMethod = guiClass.getDeclaredMethod(
            "calculateDistance",
            metricClass,
            metricClass
        );
        distanceMethod.setAccessible(true);

        addMethod = commandClass.getDeclaredMethod(
            "add",
            metricClass,
            long.class
        );
        addMethod.setAccessible(true);

        tickMethod = commandClass.getDeclaredMethod(
            "tick",
            long.class
        );
        tickMethod.setAccessible(true);

        resetMethod = commandClass.getDeclaredMethod(
            "reset",
            long.class,
            long.class
        );
        resetMethod.setAccessible(true);

        nextMethod = stateClass.getDeclaredMethod(
            "next",
            commandClass
        );
        nextMethod.setAccessible(true);

        getDistanceMethod = stateClass.getDeclaredMethod(
            "getDistance",
            int.class
        );
        getDistanceMethod.setAccessible(true);
    }

    // Create GPS data and convert its altitude to meters
    private static Object metric(
            double latitude,
            double longitude,
            double altitudeFeet,
            long version) throws Exception {

        GpsEvent gps = new GpsEvent(
            "Tracker0",
            latitude,
            longitude,
            altitudeFeet
        );

        Object filtered =
            filteredEventConstructor.newInstance(
                gps,
                version
            );

        return metricConstructor.newInstance(filtered);
    }

    private static double altitude(Object event)
            throws Exception {

        Field field = metricClass.getDeclaredField(
            "altitudeMeters"
        );
        field.setAccessible(true);

        return field.getDouble(event);
    }

    private static double distance(
            Object first,
            Object second) throws Exception {

        return (Double) distanceMethod.invoke(
            null,
            first,
            second
        );
    }

    private static boolean matches(
            double minLatitude,
            double minLongitude,
            GpsEvent event) throws Exception {

        Object filter = filterConstructor.newInstance(
            minLatitude,
            minLongitude,
            0L
        );

        return (Boolean) matchesMethod.invoke(
            filter,
            event
        );
    }

    private static Object newState() throws Exception {
        return stateConstructor.newInstance(0L);
    }

    // Add a position through the actual DistanceState
    private static Object add(
            Object state,
            Object position,
            long timestamp) throws Exception {

        Object command = addMethod.invoke(
            null,
            position,
            timestamp
        );

        return nextMethod.invoke(state, command);
    }

    private static Object tick(
            Object state,
            long timestamp) throws Exception {

        Object command = tickMethod.invoke(
            null,
            timestamp
        );

        return nextMethod.invoke(state, command);
    }

    private static Object reset(
            Object state,
            long timestamp,
            long version) throws Exception {

        Object command = resetMethod.invoke(
            null,
            timestamp,
            version
        );

        return nextMethod.invoke(state, command);
    }

    private static double total(Object state)
            throws Exception {

        return (Double) getDistanceMethod.invoke(state, 0);
    }

    public static void main(String[] args) {

        try {
            setUp();
        } catch (Exception error) {
            System.out.println(
                "Cannot load Step 15 GpsGUI classes."
            );
            error.printStackTrace();
            System.exit(1);
            return;
        }

        System.out.println("GPS Assignment 3 - Tests");
        System.out.println("------------------------");

        // Test 1: Altitude conversion
        runTest("Altitude feet to meters", () -> {
            Object point = metric(0, 0, 100, 0);
            assertNear(30.48, altitude(point), 0.00001);
        });

        // Test 2: Same GPS position
        runTest("Zero distance", () -> {
            Object first = metric(0, 0, 0, 0);
            Object second = metric(0, 0, 0, 0);
            assertNear(0, distance(first, second), 0.00001);
        });

        // Test 3: Only altitude changes
        runTest("Vertical distance", () -> {
            Object first = metric(0, 0, 0, 0);
            Object second = metric(0, 0, 10, 0);
            assertNear(3.048, distance(first, second), 0.00001);
        });

        // Test 4: Latitude changes
        runTest("Latitude distance", () -> {
            Object first = metric(0, 0, 0, 0);
            Object second = metric(0.001, 0, 0, 0);

            assertNear(
                111.195,
                distance(first, second),
                0.1
            );
        });

        // Test 5: Longitude changes
        runTest("Longitude distance", () -> {
            Object first = metric(0, 0, 0, 0);
            Object second = metric(0, 0.001, 0, 0);

            assertNear(
                111.195,
                distance(first, second),
                0.1
            );
        });

        // Test 6: Both horizontal and vertical changes
        runTest("Three-dimensional distance", () -> {
            Object first = metric(0, 0, 0, 0);
            Object second = metric(0.001, 0, 10, 0);

            double horizontal = 111.1949266;
            double expected = Math.sqrt(
                horizontal * horizontal
                    + 3.048 * 3.048
            );

            assertNear(
                expected,
                distance(first, second),
                0.1
            );
        });

        // Test 7: Latitude and longitude filtering
        runTest("GPS filter conditions", () -> {
            assertTrue(matches(
                39,
                116,
                new GpsEvent("Tracker0", 39.5, 116.5, 0)
            ));

            assertFalse(matches(
                39,
                116,
                new GpsEvent("Tracker0", 38.5, 116.5, 0)
            ));

            assertFalse(matches(
                39,
                116,
                new GpsEvent("Tracker0", 39.5, 115.5, 0)
            ));

            // Boundary values are included
            assertTrue(matches(
                39,
                116,
                new GpsEvent("Tracker0", 39, 116, 0)
            ));
        });

        // Test 8: Sum of consecutive segments
        runTest("Cumulative segment distance", () -> {
            Object state = newState();

            state = add(
                state,
                metric(0, 0, 0, 0),
                1000L
            );

            state = add(
                state,
                metric(0.0001, 0, 0, 0),
                2000L
            );

            state = add(
                state,
                metric(0.0001, 0, 10, 0),
                3000L
            );

            assertNear(
                11.1195 + 3.048,
                total(state),
                0.01
            );
        });

        // Test 9: Five-minute sliding window
        runTest("Five-minute window expiry", () -> {
            Object state = newState();

            state = add(
                state,
                metric(0, 0, 0, 0),
                1000L
            );

            state = add(
                state,
                metric(0.0001, 0, 0, 0),
                2000L
            );

            state = add(
                state,
                metric(0.0001, 0, 10, 0),
                3000L
            );

            // At 302000 ms, the first position expired.
            state = tick(state, 302000L);

            assertNear(3.048, total(state), 0.00001);

            // All positions have expired.
            state = tick(state, 303001L);

            assertNear(0, total(state), 0.00001);
        });

        // Test 10: Filter reset clears old history
        runTest("Filter reset", () -> {
            Object state = newState();

            state = add(
                state,
                metric(0, 0, 0, 0),
                1000L
            );

            state = add(
                state,
                metric(0, 0, 10, 0),
                2000L
            );

            assertTrue(total(state) > 0);

            state = reset(state, 3000L, 1L);

            assertNear(0, total(state), 0.00001);
        });

        // Test 11: Ignore events from an old filter
        runTest("Old filter version ignored", () -> {
            Object state = newState();

            state = reset(state, 1000L, 1L);

            state = add(
                state,
                metric(0, 0, 0, 0),
                2000L
            );

            state = add(
                state,
                metric(0, 0, 10, 0),
                3000L
            );

            // Old version 0 must not change version 1
            assertNear(0, total(state), 0.00001);

            state = add(
                state,
                metric(0, 0, 0, 1),
                4000L
            );

            state = add(
                state,
                metric(0, 0, 10, 1),
                5000L
            );

            assertNear(3.048, total(state), 0.00001);
        });

        // Test 12: Round total distance upward
        runTest("Distance rounding", () -> {
            Object first = metric(0, 0, 0, 0);
            Object second = metric(0, 0, 10, 0);

            long rounded = (long) Math.ceil(
                distance(first, second)
            );

            if (rounded != 4L) {
                throw new AssertionError(
                    "Expected 4 m, got " + rounded
                );
            }
        });

        System.out.println("------------------------");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total: " + (passed + failed));

        if (failed > 0) {
            System.exit(1);
        }
    }
}


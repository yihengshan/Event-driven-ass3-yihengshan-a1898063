import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class GpsBoundary_Test {

    private static int passed = 0;
    private static int failed = 0;

    private static Class<?> filteredClass;
    private static Class<?> metricClass;
    private static Class<?> commandClass;
    private static Class<?> stateClass;

    private static Constructor<?> filteredConstructor;
    private static Constructor<?> metricConstructor;
    private static Constructor<?> stateConstructor;

    private static Method addMethod;
    private static Method tickMethod;
    private static Method resetMethod;
    private static Method nextMethod;
    private static Method distanceMethod;

    private interface TestAction {
        void run() throws Exception;
    }

    private static void setUp() throws Exception {
        filteredClass = Class.forName(
            "GpsGUI$FilteredGpsEvent"
        );

        metricClass = Class.forName(
            "GpsGUI$MetricGpsEvent"
        );

        commandClass = Class.forName(
            "GpsGUI$DistanceCommand"
        );

        stateClass = Class.forName(
            "GpsGUI$DistanceState"
        );

        filteredConstructor =
            filteredClass.getDeclaredConstructor(
                GpsEvent.class,
                long.class
            );
        filteredConstructor.setAccessible(true);

        metricConstructor =
            metricClass.getDeclaredConstructor(filteredClass);
        metricConstructor.setAccessible(true);

        stateConstructor =
            stateClass.getDeclaredConstructor(long.class);
        stateConstructor.setAccessible(true);

        addMethod = commandClass.getDeclaredMethod(
            "add", metricClass, long.class
        );
        addMethod.setAccessible(true);

        tickMethod = commandClass.getDeclaredMethod(
            "tick", long.class
        );
        tickMethod.setAccessible(true);

        resetMethod = commandClass.getDeclaredMethod(
            "reset", long.class, long.class
        );
        resetMethod.setAccessible(true);

        nextMethod = stateClass.getDeclaredMethod(
            "next", commandClass
        );
        nextMethod.setAccessible(true);

        distanceMethod = stateClass.getDeclaredMethod(
            "getDistance", int.class
        );
        distanceMethod.setAccessible(true);
    }

    private static Object position(
            int tracker,
            double altitudeFeet,
            long version) throws Exception {

        GpsEvent gps = new GpsEvent(
            "Tracker" + tracker,
            0.0,
            0.0,
            altitudeFeet
        );

        Object filtered = filteredConstructor.newInstance(
            gps,
            version
        );

        return metricConstructor.newInstance(filtered);
    }

    private static Object state(long version)
            throws Exception {
        return stateConstructor.newInstance(version);
    }

    private static Object add(
            Object current,
            Object position,
            long time) throws Exception {

        Object command = addMethod.invoke(
            null,
            position,
            time
        );

        return nextMethod.invoke(current, command);
    }

    private static Object tick(
            Object current,
            long time) throws Exception {

        Object command = tickMethod.invoke(null, time);
        return nextMethod.invoke(current, command);
    }

    private static Object reset(
            Object current,
            long time,
            long version) throws Exception {

        Object command = resetMethod.invoke(
            null,
            time,
            version
        );

        return nextMethod.invoke(current, command);
    }

    private static double distance(
            Object current,
            int tracker) throws Exception {

        return (Double) distanceMethod.invoke(
            current,
            tracker
        );
    }

    private static void assertNear(
            double expected,
            double actual) {

        if (!Double.isFinite(actual)
                || Math.abs(expected - actual) > 0.00001) {
            throw new AssertionError(
                "Expected " + expected
                    + ", got " + actual
            );
        }
    }

    private static void runTest(
            String name,
            TestAction test) {

        try {
            test.run();
            passed++;
            System.out.println("PASS: " + name);
        } catch (Exception | AssertionError error) {
            failed++;
            System.out.println("FAIL: " + name);
            error.printStackTrace(System.out);
        }
    }

    public static void main(String[] args) {

        try {
            setUp();
        } catch (Exception error) {
            System.out.println(
                "Could not load Step 15 classes."
            );
            error.printStackTrace();
            System.exit(1);
            return;
        }

        System.out.println("GPS Boundary Tests");
        System.out.println("------------------");

        // Test 1: Exactly five minutes
        runTest("Exactly five minutes", () -> {
            Object current = state(0L);

            current = add(
                current,
                position(0, 0, 0),
                1000L
            );

            current = add(
                current,
                position(0, 10, 0),
                2000L
            );

            current = tick(current, 301000L);

            assertNear(3.048, distance(current, 0));
        });

        // Test 2: One millisecond beyond the window
        runTest("One millisecond past boundary", () -> {
            Object current = state(0L);

            current = add(
                current,
                position(0, 0, 0),
                1000L
            );

            current = add(
                current,
                position(0, 10, 0),
                2000L
            );

            current = tick(current, 301001L);

            assertNear(0.0, distance(current, 0));
        });

        // Test 3: Separate trackers
        runTest("Independent tracker distances", () -> {
            Object current = state(0L);

            current = add(
                current,
                position(0, 0, 0),
                1000L
            );

            current = add(
                current,
                position(1, 0, 0),
                1100L
            );

            current = add(
                current,
                position(0, 10, 0),
                2000L
            );

            current = add(
                current,
                position(1, 20, 0),
                2100L
            );

            assertNear(3.048, distance(current, 0));
            assertNear(6.096, distance(current, 1));
            assertNear(0.0, distance(current, 2));
        });

        // Test 4: Reset clears distance
        runTest("Reset clears all trackers", () -> {
            Object current = state(0L);

            current = add(
                current,
                position(0, 0, 0),
                1000L
            );

            current = add(
                current,
                position(0, 10, 0),
                2000L
            );

            current = reset(current, 3000L, 1L);

            assertNear(0.0, distance(current, 0));
            assertNear(0.0, distance(current, 1));
        });

        // Test 5: Old filter events ignored
        runTest("Ignore previous filter version", () -> {
            Object current = state(0L);

            current = reset(current, 1000L, 1L);

            current = add(
                current,
                position(0, 0, 0),
                2000L
            );

            current = add(
                current,
                position(0, 10, 0),
                3000L
            );

            assertNear(0.0, distance(current, 0));

            current = add(
                current,
                position(0, 0, 1),
                4000L
            );

            current = add(
                current,
                position(0, 10, 1),
                5000L
            );

            assertNear(3.048, distance(current, 0));
        });

        // Test 6: One position has no distance
        runTest("Single GPS position", () -> {
            Object current = state(0L);

            current = add(
                current,
                position(9, 100, 0),
                1000L
            );

            assertNear(0.0, distance(current, 9));
        });

        System.out.println("------------------");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total: " + (passed + failed));

        if (failed > 0) {
            System.exit(1);
        }
    }
}


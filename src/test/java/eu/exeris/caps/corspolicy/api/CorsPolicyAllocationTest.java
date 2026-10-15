package eu.exeris.caps.corspolicy.api;

// com.sun.management.ThreadMXBean is the only way to read exact per-thread allocated bytes.
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Measures the steady-state allocation of {@link CorsPolicy#evaluate} with the protocol of the
 * kernel TCK's {@code JfrAllocationMonitor}: warm up, then bracket the workload with the exact
 * per-thread allocated-bytes counter and require the delta to stay below one byte per iteration.
 * The requests are built before the measurement; only {@code evaluate} runs inside it.
 *
 * <p>The workload runs on a virtual thread, as a gateway's does. A virtual thread has no
 * allocation counter of its own (it reads 0, a false pass), so the counters read are the
 * scheduler's carrier threads. A control that does allocate runs through the same harness and must
 * fail, which is what shows the carrier counters are the ones being read.
 */
@DisplayName("CorsPolicy allocation")
class CorsPolicyAllocationTest {

    private static final String APP = "https://app.example.com";
    private static final String ADMIN = "https://admin.example.com";
    private static final int WARMUP = 300_000;
    private static final int MEASURED = 1_000_000;

    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    private interface Workload {
        void run(int iterations);
    }

    /** Allocated bytes across the virtual-thread scheduler's carriers while {@code workload} ran on a virtual thread. */
    private static long measureOnVirtualThread(Workload workload) throws InterruptedException {
        runOnVirtualThread(() -> workload.run(WARMUP));

        List<Thread> carriers = carriers();
        assertThat(carriers).as("virtual-thread carriers").isNotEmpty();
        long before = allocated(carriers);
        runOnVirtualThread(() -> workload.run(MEASURED));
        return allocated(carriers) - before;
    }

    private static void runOnVirtualThread(Runnable body) throws InterruptedException {
        Throwable[] failure = new Throwable[1];
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        thread.join();
        if (failure[0] != null) {
            throw new AssertionError("workload failed", failure[0]);
        }
    }

    private static List<Thread> carriers() {
        List<Thread> out = new ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String name = t.getName();
            if (name.startsWith("ForkJoinPool-") && name.contains("-worker-")) {
                out.add(t);
            }
        }
        return out;
    }

    private static long allocated(List<Thread> carriers) {
        long sum = 0;
        for (Thread t : carriers) {
            long bytes = THREADS.getThreadAllocatedBytes(t.threadId());
            assertThat(bytes).as("allocated bytes of %s", t.getName()).isNotNegative();
            sum += bytes;
        }
        return sum;
    }

    private static void assertNoPerRequestAllocation(String what, long bytes) {
        // A smallest heap object is 16 bytes, so a genuine per-request allocation passes far
        // beyond this bound while the fixed cost of starting the virtual thread stays under it.
        assertThat(bytes).as("%s: bytes allocated over %d requests", what, MEASURED).isLessThan(MEASURED);
    }

    private static void assertZero(String what, CorsPolicy policy, CorsRequest[] requests, boolean expectAllowed)
            throws InterruptedException {
        long[] sink = new long[1];
        long bytes = measureOnVirtualThread(iterations -> {
            long allowed = 0;
            for (int i = 0; i < iterations; i++) {
                if (policy.evaluate(requests[i % requests.length]).allowed()) {
                    allowed++;
                }
            }
            sink[0] = allowed;
        });
        assertThat(sink[0] > 0).as("workload ran and decisions were %s", expectAllowed ? "allowed" : "denied")
                .isEqualTo(expectAllowed);
        assertNoPerRequestAllocation(what, bytes);
    }

    @Test
    @DisplayName("allowed simple and preflight requests for explicit origins allocate nothing")
    void explicitOriginsAllowed() throws InterruptedException {
        CorsPolicy policy = CorsPolicies.builder()
                .allowOrigins(List.of(APP, ADMIN))
                .methods(List.of("GET", "POST", "PUT", "DELETE"))
                .headers(List.of("Content-Type", "Authorization", "If-Match"))
                .allowCredentials(true)
                .build();
        CorsRequest[] requests = {
                CorsRequest.simple(APP, "GET"),
                CorsRequest.simple(ADMIN, "delete"),
                CorsRequest.preflight(APP, "POST", List.of("content-type", "AUTHORIZATION")),
                CorsRequest.preflight(ADMIN, "PUT", List.of("If-Match")),
        };
        assertZero("evaluate(explicit origins, allowed)", policy, requests, true);
    }

    @Test
    @DisplayName("wildcard requests allocate nothing")
    void wildcardAllowed() throws InterruptedException {
        CorsPolicy policy = CorsPolicies.builder().allowAnyOrigin().headers(List.of("Content-Type")).build();
        CorsRequest[] requests = {
                CorsRequest.simple("https://anyone.example", "GET"),
                CorsRequest.preflight("https://other.example", "POST", List.of("Content-Type")),
        };
        assertZero("evaluate(wildcard)", policy, requests, true);
    }

    @Test
    @DisplayName("every kind of denial allocates nothing either")
    void denials() throws InterruptedException {
        CorsPolicy policy = CorsPolicies.builder().allowOrigin(APP).headers(List.of("Content-Type")).build();
        CorsRequest[] requests = {
                CorsRequest.simple(null, "GET"),
                CorsRequest.simple("https://evil.example", "GET"),
                CorsRequest.simple("null", "GET"),
                CorsRequest.simple(APP, "TRACE"),
                CorsRequest.preflight(APP, "GET", List.of("X-Smuggled")),
                CorsRequest.preflight("https://evil.example", "GET", List.of()),
        };
        assertZero("evaluate(denied)", policy, requests, false);
    }

    @Test
    @DisplayName("the measurement fails for a workload that allocates, so a pass above is not vacuous")
    void measurementDetectsAllocation() throws InterruptedException {
        Object[] sink = new Object[1];
        long bytes = measureOnVirtualThread(iterations -> {
            for (int i = 0; i < iterations; i++) {
                sink[0] = new StringBuilder("q=").append(i).toString();
            }
        });

        assertThatThrownBy(() -> assertNoPerRequestAllocation("control", bytes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("the control also fails for a policy that builds its decision per request")
    void controlPolicyThatAllocatesADecision() throws InterruptedException {
        CorsPolicy allocating = request -> new CorsDecision(
                true, request.origin(), List.of("GET"), List.of(), null, false);
        CorsRequest[] requests = {CorsRequest.simple(APP, "GET")};
        long[] sink = new long[1];
        long bytes = measureOnVirtualThread(iterations -> {
            long allowed = 0;
            for (int i = 0; i < iterations; i++) {
                if (allocating.evaluate(requests[0]).allowed()) {
                    allowed++;
                }
            }
            sink[0] = allowed;
        });

        assertThat(sink[0]).isPositive();
        assertThatThrownBy(() -> assertNoPerRequestAllocation("control policy", bytes))
                .isInstanceOf(AssertionError.class);
    }
}

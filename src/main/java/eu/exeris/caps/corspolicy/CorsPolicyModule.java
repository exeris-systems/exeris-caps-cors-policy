package eu.exeris.caps.corspolicy;

import eu.exeris.caps.corspolicy.api.CorsPolicy;
import eu.exeris.sdk.annotation.capability.CapabilityModule;
import eu.exeris.sdk.annotation.capability.Provides;

/**
 * The capability declaration for {@code exeris-caps-cors-policy}.
 *
 * <p>This class is the entire reason the build treats this repository as a cap. It
 * carries no code and is never instantiated — the annotations below are
 * {@code @Retention(SOURCE)}, so they are erased at {@code javac} and cannot be read
 * at runtime by anything. The build-time pipeline is their only possible consumer,
 * which is what makes the composition contract a build concern end to end.
 *
 * <h2>What the build does with this</h2>
 * <ol>
 *   <li>{@code exeris-processor} extracts the declaration into
 *       {@code target/classes/exeris-metadata/capability_*.json} during {@code compile}.</li>
 *   <li>{@code exeris:generate} resolves the graph and emits {@code cap-manifest.json}
 *       carrying a validated stamp and a SHA-256 content binding over the resolved
 *       cap set.</li>
 *   <li>{@code exeris:verify-capabilities} re-validates against metadata emitted
 *       <em>this</em> build, then scans the compiled bytecode for Wall violations.</li>
 * </ol>
 *
 * <p>Because the processor only writes that metadata during {@code compile}, and
 * {@code generate} runs at {@code generate-sources}, the manifest appears on the
 * <em>second</em> build of a fresh checkout. That is expected, not a fault.
 *
 * <h2>No {@code @Requires}, and no {@code @CapabilityLifecycle}</h2>
 * <p>Both are absent because they would be untrue, not because this is a stub.
 *
 * <p>This cap depends on nothing: its decision function is total over its inputs, so
 * there is no service to require. Adding an empty or speculative {@code @Requires}
 * would put a false edge in the composition DAG and change the derived
 * {@code initOrder} for every SKU that includes this cap.
 *
 * <p>It owns no resources either — no pool, no connection, no thread, nothing to flush.
 * A {@code @CapabilityLifecycle} class here would be four no-op methods that the boot
 * conductor still has to load reflectively and drive through four phases. The hooks
 * exist for caps that acquire something; declaring them when nothing is acquired buys
 * cost and implies a contract this cap does not have.
 *
 * @since 0.1.0
 */
@CapabilityModule
@Provides(service = CorsPolicy.class, version = "1.0.0")
public final class CorsPolicyModule {

    private CorsPolicyModule() {
        // Declaration carrier only — never constructed.
    }
}

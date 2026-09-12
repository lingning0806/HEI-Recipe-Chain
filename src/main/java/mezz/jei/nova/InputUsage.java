/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Local adaptation and modifications: 绫宁, 2026-09-13.
 * See CONTRIBUTION_LICENSE.md and THIRD_PARTY_NOTICES.md.
 */
package mezz.jei.nova;

/** Per-slot requirements. Uses=0 means consumed, MAX_VALUE means returned unchanged. */
public final class InputUsage {
    public static final InputUsage CONSUMED = new InputUsage(0, null);
    public final long uses;
    public final Object remainder;
    public InputUsage(long uses, Object remainder) { this.uses=uses; this.remainder=remainder; }
    public long required(long slots, long batches) {
        if (slots < 0 || batches < 0) throw new IllegalArgumentException("Negative input quantity");
        if (batches == 0) return 0;
        return Math.multiplyExact(slots, uses == Long.MAX_VALUE ? 1 : uses > 0 ? ChainMath.batches(batches,uses) : batches);
    }
}

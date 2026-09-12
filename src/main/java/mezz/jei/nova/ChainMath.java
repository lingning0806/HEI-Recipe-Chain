/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Local adaptation and modifications: 绫宁, 2026-09-13.
 * See CONTRIBUTION_LICENSE.md and THIRD_PARTY_NOTICES.md.
 */
package mezz.jei.nova;

public final class ChainMath {
    private ChainMath() { }
    public static long batches(long demand, long output) {
        if (demand < 0 || output <= 0) throw new IllegalArgumentException("Invalid recipe quantity");
        return demand / output + (demand % output == 0 ? 0 : 1);
    }
}

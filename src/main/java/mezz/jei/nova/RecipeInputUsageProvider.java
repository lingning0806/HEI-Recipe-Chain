/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 * Local adaptation and modifications: 绫宁, 2026-09-13.
 * See CONTRIBUTION_LICENSE.md and THIRD_PARTY_NOTICES.md.
 */
package mezz.jei.nova;

import java.util.List;
/** Optional wrapper adapter. Unknown semantics must return CONSUMED; never infer from item names. */
public interface RecipeInputUsageProvider {
    InputUsage getInputUsage(List<?> alternatives);
}

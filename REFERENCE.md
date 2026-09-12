# Reference baseline

Default design reference: https://github.com/StardustMINUS-01/JustEnoughItems
1.20.1 commit b820bfea48e8f3c82125916c61d1e9f3750653d0.
HEI baseline: https://github.com/CleanroomMC/HadEnoughItems/commit/7236ccd55888b709269c2ad1c264f043ceb96205

RecipeChainMath / RecipeChainPlan / RecipeChainGraph: quantities, returns and chain planning.
AutoCraftingManager: planned per-recipe batches; local execution retains resource-ledger ordering and native game crafting events.
RecipeChainPatternEncodeRequestFactory / JeiRecipeChainPatternEncodingService: canonical material guides, preflight and per-entry results.
PreferenceRules: configurable material/mod/recipe preference ranks.
BookmarkPullPlanner / RecipeChainTooltipModel / bookmark ghost overlay: material withdrawal, views and projection.

This is a Java8 / legacy-AE adaptation, not a full direct port. See THIRD_PARTY_NOTICES.md and 许可核查.md for licensing scope and unresolved attribution boundaries.

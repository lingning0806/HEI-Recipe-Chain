package mezz.jei.input;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import mezz.jei.config.IngredientBlacklistType;
import mezz.jei.config.KeyBindings;
import mezz.jei.gui.Focus;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.ghost.GhostIngredientDragManager;
import mezz.jei.gui.ghost.IGhostIngredientDragSource;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.IngredientListOverlay;
import mezz.jei.gui.overlay.bookmarks.LeftAreaDispatcher;
import mezz.jei.gui.recipes.RecipeClickableArea;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.recipes.RecipeRegistry;
import mezz.jei.runtime.JeiRuntime;
import mezz.jei.util.ReflectionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class InputHandler {
    private final RecipeRegistry recipeRegistry;
    private final IIngredientRegistry ingredientRegistry;
    private final IngredientFilter ingredientFilter;
    private final RecipesGui recipesGui;
    private final IngredientListOverlay ingredientListOverlay;
    private final LeftAreaDispatcher leftAreaDispatcher;
    private final BookmarkList bookmarkList;
    private final IAutocraftingHandler autocraftingHandler;
    private final List<IShowsRecipeFocuses> showsRecipeFocuses = new ArrayList<>();
    private final IntSet clickHandled = new IntArraySet();
    private final GhostIngredientDragManager ghostIngredientDragManager;
    private final mezz.jei.nova.suite.AeSearchDrag aeSearchDrag=new mezz.jei.nova.suite.AeSearchDrag();

    public InputHandler(JeiRuntime runtime, IngredientRegistry ingredientRegistry, IngredientListOverlay ingredientListOverlay, GuiScreenHelper guiScreenHelper, LeftAreaDispatcher leftAreaDispatcher, BookmarkList bookmarkList, GhostIngredientDragManager ghostIngredientDragManager) {
        this.recipeRegistry = runtime.getRecipeRegistry();
        this.ingredientRegistry = ingredientRegistry;
        this.ingredientFilter = runtime.getIngredientFilter();
        this.recipesGui = runtime.getRecipesGui();
        this.ingredientListOverlay = ingredientListOverlay;
        this.leftAreaDispatcher = leftAreaDispatcher;
        this.bookmarkList = bookmarkList;
        this.autocraftingHandler = runtime.getAutocraftingHandler();
        this.ghostIngredientDragManager = ghostIngredientDragManager;

        this.showsRecipeFocuses.add(recipesGui);
        this.showsRecipeFocuses.add(ingredientListOverlay);
        this.showsRecipeFocuses.add(leftAreaDispatcher);
        this.showsRecipeFocuses.add(new GuiContainerWrapper(guiScreenHelper));
    }

    private boolean handleBookmarkExtra() {
        if (Minecraft.getMinecraft().currentScreen == recipesGui) return false;
        boolean forceAdd = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) && Keyboard.isKeyDown(KeyBindings.bookmark.getKeyCode());
        if (!forceAdd) return false;
        IClickedIngredient<?> clicked = getIngredientUnderMouseForKey(MouseHelper.getX(), MouseHelper.getY());
        if (clicked != null) {
            if (!Config.isBookmarkOverlayEnabled())
                Config.toggleBookmarkEnabled();
            return bookmarkList.add(new BookmarkItem<>(clicked.getValue()), true);
        }
        return false;
    }

    /**
     * When we have keyboard focus, use Pre
     */
    @SubscribeEvent
    public void onGuiKeyboardEvent(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (hasKeyboardFocus() && handleKeyEvent()) {
            event.setCanceled(true);
        }
    }

    /**
     * Without focus, use Post
     */
    @SubscribeEvent
    public void onGuiKeyboardEvent(GuiScreenEvent.KeyboardInputEvent.Post event) {
        if (hasKeyboardFocus()) return;
        if (handleBookmarkExtra())
            event.setCanceled(true);
        else if (handleKeyEvent())
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void onGuiMouseEvent(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen guiScreen = event.getGui();
        Minecraft minecraft = guiScreen.mc;
        if (minecraft != null) {
            int x = Mouse.getEventX() * guiScreen.width / minecraft.displayWidth;
            int y = guiScreen.height - Mouse.getEventY() * guiScreen.height / minecraft.displayHeight - 1;
            if (handleMouseEvent(guiScreen, x, y)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void drawAeSearchDrag(GuiScreenEvent.DrawScreenEvent.Post event) {
        aeSearchDrag.draw(event.getMouseX(),event.getMouseY());
    }

    public boolean handleMouseEvent(GuiScreen guiScreen, int mouseX, int mouseY) {
        if(mezz.jei.nova.suite.MaterialPanel.active())return mezz.jei.nova.suite.MaterialPanel.mouse(mouseX,mouseY,Mouse.getEventButton(),Mouse.getEventButtonState(),Mouse.getEventDWheel());
        boolean cancelEvent = false;
        final int eventButton = Mouse.getEventButton();
        if(aeSearchDrag.active()) {
            aeSearchDrag.move(mouseX,mouseY);
            if(eventButton==1 && Mouse.getEventButtonState()) {aeSearchDrag.clear();clickHandled.remove(0);return true;}
            if(eventButton==0 && !Mouse.getEventButtonState()) {
                mezz.jei.nova.suite.PointerGesture gesture=aeSearchDrag.gesture();
                if(gesture.isDrag())aeSearchDrag.release(mouseX,mouseY);
                else {aeSearchDrag.clear();handleMouseClick(guiScreen,0,gesture.x,gesture.y);}
                clickHandled.remove(0);return true;
            }
            return true;
        }
        if(eventButton==0 && Mouse.getEventButtonState() && !ghostIngredientDragManager.isDragging() && (bookmarkList.getGroupOrganizer()==null || !bookmarkList.getGroupOrganizer().isDragging())) {
            IClickedIngredient<?> source=leftAreaDispatcher.getIngredientUnderMouse(mouseX,mouseY);
            if(source==null)source=ingredientListOverlay.getIngredientUnderMouse(mouseX,mouseY);
            if(aeSearchDrag.begin(guiScreen,source,mouseX,mouseY)) {clickHandled.add(0);return true;}
        }
        if(eventButton==0 && !Mouse.getEventButtonState() && ghostIngredientDragManager.release(mouseX,mouseY)) {clickHandled.remove(0);return true;}
        if (eventButton >= 0 && !Mouse.getEventButtonState() && bookmarkList.getGroupOrganizer() != null &&
                bookmarkList.getGroupOrganizer().endDrag(mouseX, mouseY, eventButton)) {
            clickHandled.remove(eventButton);
            return true;
        }
        if (bookmarkList.getGroupOrganizer() != null && bookmarkList.getGroupOrganizer().isDragging()) {
            if (eventButton == 1 && Mouse.getEventButtonState()) bookmarkList.getGroupOrganizer().cancelDrag();
            else bookmarkList.getGroupOrganizer().updateDrag(mouseX, mouseY);
            return true; // Do not scroll/reflow the list or forward movement to container drags.
        }
        if (eventButton > -1) {
            if (Mouse.getEventButtonState()) {
                if (!clickHandled.contains(eventButton)) {
                    cancelEvent = handleMouseClick(guiScreen, eventButton, mouseX, mouseY);
                    if (cancelEvent) {
                        clickHandled.add(eventButton);
                    }
                }
            } else if (clickHandled.contains(eventButton)) {
                clickHandled.remove(eventButton);
                cancelEvent = true;
            }
        } else if (Mouse.getEventDWheel() != 0) {
            cancelEvent = handleMouseScroll(Mouse.getEventDWheel(), mouseX, mouseY);
        }
        return cancelEvent;
    }

    private boolean handleMouseScroll(int dWheel, int mouseX, int mouseY) {
        return ingredientListOverlay.handleMouseScrolled(mouseX, mouseY, dWheel) || leftAreaDispatcher.handleMouseScrolled(mouseX, mouseY, dWheel);
    }

    private boolean handleMouseClick(GuiScreen guiScreen, int mouseButton, int mouseX, int mouseY) {
        IClickedIngredient<?> clicked = getFocusUnderMouseForClick(mouseX, mouseY);
        if (Config.isEditModeEnabled() && clicked != null && handleClickEdit(clicked)) {
            return true;
        }

        if (ingredientListOverlay.handleMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (leftAreaDispatcher.handleMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }

        IIngredientListElement<?> listElement = getElementUnderMouse();
        if (this.ghostIngredientDragManager.handleMouseClicked(guiScreen.mc, guiScreen, clicked, listElement, mouseX, mouseY)) {
            return true;
        }

        if (clicked != null && (Config.mouseClickToSeeRecipe() && handleMouseClickedFocus(mouseButton, clicked))) {
            return true;
        }
        if (handleFocusKeybinds(mouseButton - 100)) {
            return true;
        }

        if (guiScreen instanceof GuiContainer) {
            GuiContainer guiContainer = (GuiContainer) guiScreen;
            RecipeClickableArea clickableArea = recipeRegistry.getRecipeClickableArea(guiContainer, mouseX - guiContainer.getGuiLeft(), mouseY - guiContainer.getGuiTop());
            if (clickableArea != null) {
                List<String> recipeCategoryUids = clickableArea.getRecipeCategoryUids();
                recipesGui.showCategories(recipeCategoryUids);
                return true;
            }
        }

        return handleGlobalKeybinds(mouseButton - 100);
    }

    @Nullable
    private IClickedIngredient<?> getFocusUnderMouseForClick(int mouseX, int mouseY) {
        for (IShowsRecipeFocuses gui : showsRecipeFocuses) {
            if (gui.canSetFocusWithMouse()) {
                IClickedIngredient<?> clicked = gui.getIngredientUnderMouse(mouseX, mouseY);
                if (clicked != null) {
                    return clicked;
                }
            }
        }
        return null;
    }

    @Nullable
    public IIngredientListElement<?> getElementUnderMouse() {
        for (IShowsRecipeFocuses gui : showsRecipeFocuses) {
            if (!(gui instanceof IGhostIngredientDragSource)) {
                continue;
            }
            IIngredientListElement<?> element = ((IGhostIngredientDragSource) gui).getElementUnderMouse();
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    @Nullable
    private IClickedIngredient<?> getIngredientUnderMouseForKey(int mouseX, int mouseY) {
        for (IShowsRecipeFocuses gui : showsRecipeFocuses) {
            IClickedIngredient<?> clicked = gui.getIngredientUnderMouse(mouseX, mouseY);
            if (clicked != null) {
                return clicked;
            }
        }
        return null;
    }

    private <V> boolean handleMouseClickedFocus(int mouseButton, IClickedIngredient<V> clicked) {
        if (mouseButton == 0) {
            IFocus<?> focus = new Focus<>(IFocus.Mode.OUTPUT, clicked.getValue());
            recipesGui.show(focus);
            clicked.onClickHandled();
            return true;
        } else if (mouseButton == 1) {
            IFocus<?> focus = new Focus<>(IFocus.Mode.INPUT, clicked.getValue());
            recipesGui.show(focus);
            clicked.onClickHandled();
            return true;
        }

        return false;
    }

    private <V> boolean handleClickEdit(IClickedIngredient<V> clicked) {
        V ingredient = clicked.getValue();
        IngredientBlacklistType blacklistType = GuiScreen.isCtrlKeyDown() ? IngredientBlacklistType.WILDCARD : IngredientBlacklistType.ITEM;

        IIngredientHelper<V> ingredientHelper = ingredientRegistry.getIngredientHelper(ingredient);

        if (Config.isIngredientOnConfigBlacklist(ingredient, ingredientHelper)) {
            Config.removeIngredientFromConfigBlacklist(ingredientFilter, ingredientRegistry, ingredient, blacklistType, ingredientHelper);
        } else {
            Config.addIngredientToConfigBlacklist(ingredientFilter, ingredientRegistry, ingredient, blacklistType, ingredientHelper);
        }
        clicked.onClickHandled();
        return true;
    }

    private boolean hasKeyboardFocus() {
        return aeSearchDrag.active() || mezz.jei.nova.suite.MaterialPanel.active() || ingredientListOverlay.hasKeyboardFocus() || autocraftingHandler.isActive() ||
                (bookmarkList.getGroupOrganizer() != null && bookmarkList.getGroupOrganizer().isDragging());
    }

    private boolean handleKeyEvent() {
        char typedChar = Keyboard.getEventCharacter();
        int eventKey = Keyboard.getEventKey();

        return ((eventKey == 0 && typedChar >= 32) || Keyboard.getEventKeyState()) &&
                handleKeyDown(typedChar, eventKey);
    }

    private boolean handleKeyDown(char typedChar, int eventKey) {
        if(aeSearchDrag.active()) {
            if(KeyBindings.isInventoryCloseKey(eventKey) || KeyBindings.isInventoryToggleKey(eventKey)) {aeSearchDrag.clear();clickHandled.remove(0);return false;}
            if(KeyBindings.cancelRecipeDrag.isActiveAndMatches(eventKey)) {aeSearchDrag.clear();clickHandled.remove(0);}
            return true;
        }
        if(mezz.jei.nova.suite.MaterialPanel.active()) {
            if(KeyBindings.isInventoryCloseKey(eventKey) || KeyBindings.isInventoryToggleKey(eventKey)) {mezz.jei.nova.suite.MaterialPanel.close();return false;}
            mezz.jei.nova.suite.MaterialPanel.key(eventKey);return true;
        }
        if(KeyBindings.cancelRecipeDrag.isActiveAndMatches(eventKey))mezz.jei.nova.suite.RecipeProjection.clear();
        if(KeyBindings.projectRecipe.isActiveAndMatches(eventKey) && !Keyboard.isRepeatEvent() && !autocraftingHandler.isActive()) {
            IClickedIngredient<?> clicked=leftAreaDispatcher.getIngredientUnderMouse(MouseHelper.getX(),MouseHelper.getY());
            mezz.jei.autocrafting.RecipeBookmarkItem<?> recipe=null;
            Object value=clicked==null?null:clicked.getValue();
            if(value instanceof mezz.jei.autocrafting.RecipeBookmarkItem)recipe=(mezz.jei.autocrafting.RecipeBookmarkItem<?>)value;
            else if(value instanceof BookmarkItem && ((BookmarkItem<?>)value).getGroup() instanceof mezz.jei.autocrafting.RecipeBookmarkGroup)
                recipe=((mezz.jei.autocrafting.RecipeBookmarkGroup)((BookmarkItem<?>)value).getGroup()).ownerOf((BookmarkItem<?>)value);
            mezz.jei.autocrafting.RecipeBookmarkGroup group=bookmarkList.getGroupOrganizer()==null?null:bookmarkList.getGroupOrganizer().groupAtBracket(MouseHelper.getX(),MouseHelper.getY());
            if(recipe==null && group!=null) {
                java.util.List<mezz.jei.autocrafting.RecipeBookmarkItem<?>> roots=new java.util.ArrayList<>();
                for(BookmarkItem<?> item:group.getItemsInternal())if(((mezz.jei.autocrafting.RecipeBookmarkItem<?>)item).selfOutputAmount>0)roots.add((mezz.jei.autocrafting.RecipeBookmarkItem<?>)item);
                if(roots.size()==1)recipe=roots.get(0);
                else {mezz.jei.nova.CraftDiagnostics.notice("这组有多个目标，请将鼠标移到要投影的具体成品书签");return true;}
            }
            mezz.jei.nova.CraftDiagnostics.log("projection key: selected="+(recipe!=null)+", bracket="+(group!=null));
            if(recipe!=null) {
                if(!mezz.jei.nova.suite.RecipeProjection.isSelected(recipe) || !mezz.jei.nova.suite.RecipeProjection.fill())mezz.jei.nova.suite.RecipeProjection.select(recipe);
                return true;
            }
            if(mezz.jei.nova.suite.RecipeProjection.fill())return true;
            if(!isContainerTextFieldFocused()) {mezz.jei.nova.CraftDiagnostics.notice("请悬停左侧成品书签，或单目标配方组的括号后按投影键");return true;}
        }
        if (bookmarkList.getGroupOrganizer() != null && bookmarkList.getGroupOrganizer().isDragging()) {
            if (KeyBindings.isInventoryCloseKey(eventKey) || KeyBindings.isInventoryToggleKey(eventKey)) {
                bookmarkList.getGroupOrganizer().cancelDrag();
                clickHandled.remove(0);
                return false; // Let the original screen close normally.
            }
            if (KeyBindings.cancelRecipeDrag.isActiveAndMatches(eventKey)) {
                bookmarkList.getGroupOrganizer().cancelDrag();
                clickHandled.remove(0);
            }
            return true;
        }
        if (KeyBindings.crafting.isActiveAndMatches(eventKey)) mezz.jei.nova.CraftDiagnostics.log("input dispatcher; searchFocus="+ingredientListOverlay.hasKeyboardFocus()+" busy="+autocraftingHandler.isActive());
        if (autocraftingHandler.isActive()) {
            if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
                autocraftingHandler.stop();
                return true;
            }
        }

        if (ghostIngredientDragManager.handleKeyDown(eventKey)) {
            return true;
        }

        if (ingredientListOverlay.hasKeyboardFocus()) {
            if (KeyBindings.isInventoryCloseKey(eventKey) || KeyBindings.isEnterKey(eventKey)) {
                ingredientListOverlay.setKeyboardFocus(false);
                return true;
            } else if (ingredientListOverlay.onKeyPressed(typedChar, eventKey)) {
                return true;
            }
        }

        if (eventKey == Keyboard.KEY_Z && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) &&
                !ingredientListOverlay.hasKeyboardFocus() && !isContainerTextFieldFocused() && !autocraftingHandler.isActive()) {
            if (bookmarkList.undoRecipeEdit()) {
                if (!Config.isBookmarkOverlayEnabled()) Config.toggleBookmarkEnabled();
                return true;
            }
        }
        if (leftAreaDispatcher.onKeyPressed(typedChar, eventKey)) {
            return true;
        }

        if (handleGlobalKeybinds(eventKey)) {
            return true;
        }

        if (!isContainerTextFieldFocused()) {
            if (handleFocusKeybinds(eventKey)) {
                return true;
            }
            return ingredientListOverlay.onKeyPressed(typedChar, eventKey);
        }

        return false;
    }

    private boolean handleGlobalKeybinds(int eventKey) {
        if (KeyBindings.toggleOverlay.isActiveAndMatches(eventKey)) {
            Config.toggleOverlayEnabled();
            return false;
        }
        if (KeyBindings.toggleBookmarkOverlay.isActiveAndMatches(eventKey)) {
            Config.toggleBookmarkEnabled();
            return false;
        }
        return ingredientListOverlay.onGlobalKeyPressed(eventKey);
    }

    private boolean handleFocusKeybinds(int eventKey) {
        final boolean showRecipe = KeyBindings.showRecipe.isActiveAndMatches(eventKey);
        final boolean showUses = KeyBindings.showUses.isActiveAndMatches(eventKey);
        final boolean bookmark = KeyBindings.bookmark.isActiveAndMatches(eventKey);
        if (showRecipe || showUses || bookmark) {
            IClickedIngredient<?> clicked = getIngredientUnderMouseForKey(MouseHelper.getX(), MouseHelper.getY());
            if (clicked != null) {
                if (bookmark) {
                    if (autocraftingHandler.isActive()) return true;
                    if (bookmarkList.remove(clicked.getValue())) {
                        if (bookmarkList.isEmpty() && Config.isBookmarkOverlayEnabled()) {
                            Config.toggleBookmarkEnabled();
                        }
                        return true;
                    } else {
                        // A stale/derived recipe row must never become a nested item bookmark.
                        if (clicked.getValue() instanceof BookmarkItem) return true;
                        if (!Config.isBookmarkOverlayEnabled()) {
                            Config.toggleBookmarkEnabled();
                        }
                        return bookmarkList.add(new BookmarkItem<>(clicked.getValue()));
                    }
                } else {
                    IFocus.Mode mode = showRecipe ? IFocus.Mode.OUTPUT : IFocus.Mode.INPUT;
                    Object value = clicked.getValue();
                    recipesGui.show(new Focus<>(
                            mode,
                            value instanceof BookmarkItem ? ((BookmarkItem<?>) value).ingredient : value));
                    clicked.onClickHandled();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isContainerTextFieldFocused() {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        if (gui == null) {
            return false;
        }
        GuiTextField textField = ReflectionUtil.getFieldWithClass(gui, GuiTextField.class);
        return textField != null && textField.getVisible() && textField.isFocused();
    }

}

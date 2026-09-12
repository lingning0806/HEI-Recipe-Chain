package mezz.jei.gui.overlay.bookmarks.group;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import mezz.jei.config.KeyBindings;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.bookmarks.BookmarkGridWithNavigation;
import mezz.jei.input.MouseHelper;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_HEIGHT;
import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_PADDING;

public class BookmarkGroupOrganizer {
    public final int GROUP_PADDING_Y = INGREDIENT_HEIGHT / 2 - 5;
    public final int GROUP_PADDING_X = BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH / 2 - 1;
    private final List<BookmarkGroupDisplay> groups = new ArrayList<>();
    private final IngredientListBatchRenderer missingIngredientRenderer = new IngredientListBatchRenderer(false);

    private Rectangle area = new Rectangle();
    private int hoveredGroupId = -1;
    private int missingIngredients = 0;
    private long missingCheckedAt,viewsAt;

    public BookmarkGroupOrganizer() {
    }

    private RecipeBookmarkGroup draggedGroup;
    private GuiScreen dragScreen;
    private mezz.jei.nova.suite.PointerGesture pointer;
    private mezz.jei.nova.GroupDragSelection dragSelection;

    public boolean isDragging() { return draggedGroup != null; }

    public boolean cancelDrag() {
        boolean active = isDragging();
        draggedGroup = null; dragSelection = null; dragScreen = null; pointer=null;
        return active;
    }

    public void updateDrag(int x, int y) {
        if (dragScreen != Minecraft.getMinecraft().currentScreen) { cancelDrag(); return; }
        if(pointer!=null)pointer.move(x,y);
        if (dragSelection != null) dragSelection.update(x, y);
    }

    public boolean beginDrag(int x, int y, int button) {
        if(mezz.jei.nova.suite.MaterialPanel.click(x,y))return true;
        if ((button != 0 && button != 1) || !Config.areRecipeBookmarksEnabled()) return false;
        BookmarkGroupDisplay hit = recipeGroupAt(x, y);
        if (hit == null) return false;
        if (Internal.getRuntime().getAutocraftingHandler().isActive()) return true;
        if (button == 1) {
            Internal.getBookmarkList().splitRecipeGroup((RecipeBookmarkGroup) hit.group);
            return true;
        }
        pointer=new mezz.jei.nova.suite.PointerGesture(x,y,6);
        draggedGroup = (RecipeBookmarkGroup) hit.group;
        dragScreen = Minecraft.getMinecraft().currentScreen;
        List<mezz.jei.nova.GroupDragSelection.Row> rows = new ArrayList<>();
        for (BookmarkGroupDisplay display : groups) {
            if (display.group instanceof RecipeBookmarkGroup)
                rows.add(new mezz.jei.nova.GroupDragSelection.Row(display.group.id, display.area));
        }
        dragSelection = new mezz.jei.nova.GroupDragSelection(rows, hit.group.id, x, y);
        return true;
    }

    private BookmarkGroupDisplay recipeGroupAt(int x, int y) { return recipeGroupAt(x, y, false); }

    private BookmarkGroupDisplay recipeGroupAt(int x, int y, boolean wholeRow) {
        for (BookmarkGroupDisplay display : groups) {
            if (display.group instanceof RecipeBookmarkGroup && x >= display.area.x &&
                    x < display.area.x + (wholeRow ? display.area.width : BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH) &&
                    y >= display.area.y && y < display.area.y + display.area.height) return display;
        }
        return null;
    }

    public boolean endDrag(int x, int y, int button) {
        if (button != 0 || draggedGroup == null) return false;
        updateDrag(x, y);
        boolean moving=pointer!=null && pointer.isDrag();
        List<Integer> selected = !moving || dragSelection == null ? java.util.Collections.emptyList() : dragSelection.selectedIds();
        RecipeBookmarkGroup source=draggedGroup;
        BookmarkGroupDisplay released=recipeGroupAt(x,y);
        boolean click=pointer!=null && !pointer.isDrag() && released!=null && released.group==source;
        cancelDrag();
        if (source==null)return true;
        if(click) {cycleView(source);return true;}
        if (moving && mezz.jei.nova.suite.AeGhost.drop(source,x,y)) return true;
        if (selected.size() < 2 || Internal.getRuntime().getAutocraftingHandler().isActive()) return true;
        try {
            Internal.getBookmarkList().mergeRecipeGroups(selected);
            mezz.jei.nova.CraftDiagnostics.notice("已合并 " + selected.size() + " 组配方 · Ctrl+Z 撤销");
        } catch (RuntimeException ex) {
            mezz.jei.util.Log.get().warn("Recipe group merge rejected", ex);
            mezz.jei.nova.CraftDiagnostics.notice("无法合并：配方存在冲突或循环依赖。原分组已保留。");
        }
        return true;
    }

    private void cycleView(RecipeBookmarkGroup group) {
        if(Internal.getRuntime().getAutocraftingHandler().isActive())return;
        group.cycleView();mezz.jei.nova.CraftDiagnostics.notice(new String[]{"完整配方视图","折叠：成品与基础材料","清单：成品与本次缺口"}[group.viewMode]);
    }
    public RecipeBookmarkGroup groupAtBracket(int x,int y) {
        BookmarkGroupDisplay hit=recipeGroupAt(x,y);return hit==null?null:(RecipeBookmarkGroup)hit.group;
    }

    public void updateBounds(Rectangle availableArea) {
        this.area = availableArea;
    }

    public void setBookmarkGroupIds(List<Integer> bookmarkGroupIds) {
        // Find contiguous groups
        this.groups.clear();
        hoveredGroupId = -1;
        if (bookmarkGroupIds.isEmpty()) {
            return;
        }
        int startOfSequence = 0;
        int contiguousGroupId = bookmarkGroupIds.get(0);
        for (int i = 0; i < bookmarkGroupIds.size(); i++) {
            int groupId = bookmarkGroupIds.get(i);
            if (groupId == contiguousGroupId) {
                continue;
            }
            addGroup(startOfSequence, i - 1, contiguousGroupId);

            startOfSequence = i;
            contiguousGroupId = groupId;
        }
        addGroup(startOfSequence, bookmarkGroupIds.size() - 1, contiguousGroupId);
    }

    private void addGroup(int start, int end, int groupId) {
        if (groupId == -1) {
            return;
        }
        BookmarkGroup group = Internal.getBookmarkList().getBookmarkGroup(groupId);
        if (group == null) {
            return;
        }
        Rectangle groupArea = getGroupArea(start, end, area);
        groups.add(new BookmarkGroupDisplay(groupArea, group));
    }

    private Rectangle getGroupArea(int rowStart, int rowEnd, Rectangle availableArea) {
        final int rows = availableArea.height / INGREDIENT_HEIGHT;
        final int height = rows * INGREDIENT_HEIGHT;
        final int y = availableArea.y + (availableArea.height - height) / 2;


        return new Rectangle(0,
                INGREDIENT_HEIGHT * rowStart + y,
                availableArea.width,
                INGREDIENT_HEIGHT * (rowEnd - rowStart + 1));
    }

    public void draw(Minecraft minecraft, int mouseX, int mouseY) {
        if (!Config.areRecipeBookmarksEnabled()) {
            return;
        }
        if(!isDragging() && Minecraft.getSystemTime()-viewsAt>500 && groups.stream().anyMatch(g->g.group instanceof RecipeBookmarkGroup && ((RecipeBookmarkGroup)g.group).viewMode!=0)) {
            viewsAt=Minecraft.getSystemTime();Internal.getBookmarkList().notifyListenersOfChange();
        }
        if (isDragging()) updateDrag(mouseX, mouseY);
        Rectangle preview = pointer==null || !pointer.isDrag() || dragSelection == null ? null : dragSelection.previewBounds();
        if (preview != null) GuiScreen.drawRect(preview.x, preview.y, preview.x + preview.width, preview.y + preview.height, 0x2240CFFF);
        for (BookmarkGroupDisplay groupDisplay : groups) {
            this.drawGroup(minecraft, mouseX, mouseY, groupDisplay);
        }
        if (preview != null) {
            int left = preview.x + GROUP_PADDING_X;
            GuiScreen.drawRect(left, preview.y, left + 2, preview.y + preview.height, 0xFF40CFFF);
            GuiScreen.drawRect(left, preview.y, left + 9, preview.y + 2, 0xFF40CFFF);
            GuiScreen.drawRect(left, preview.y + preview.height - 2, left + 9, preview.y + preview.height, 0xFF40CFFF);
        }
    }

    private void drawGroup(Minecraft minecraft, int mouseX, int mouseY, BookmarkGroupDisplay display) {
        Rectangle groupArea = display.area;
        BookmarkGroup group = display.group;
        int color = group.getColor();
        if (pointer!=null && pointer.isDrag() && dragSelection != null && dragSelection.selectedIds().contains(group.id)) color = 0xFF40CFFF;
        // Rectangle 1: a rectangle going down the left edge of the group area
        int top = groupArea.y + GROUP_PADDING_Y;
        int bottom = groupArea.y + groupArea.height - GROUP_PADDING_Y;
        int left = groupArea.x + GROUP_PADDING_X;
        int right = groupArea.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH - GROUP_PADDING_X;
        GuiScreen.drawRect(left, top, right, bottom, color);

        // Rectangle 2: a rectangle pointing right from the top edge of the group area, making a left bracket
        GuiScreen.drawRect(left, top - 2, groupArea.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH, top, color);
        // Rectangle 3: a rectangle pointing right from the bottom edge of the group area
        GuiScreen.drawRect(left, bottom, groupArea.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH, bottom + 2, color);
    }

    public void drawTooltips(Minecraft minecraft, int mouseX, int mouseY) {
        if(mezz.jei.nova.suite.MaterialPanel.active()) {mezz.jei.nova.suite.MaterialPanel.draw();return;}
        if (isDragging()) {
            if(pointer==null || !pointer.isDrag())return; // A press is still a click until it moves.
            int count = dragSelection == null ? 0 : dragSelection.selectedIds().size();
            String cancel = KeyBindings.cancelRecipeDrag.getKeyCode() == Keyboard.KEY_NONE ? "右键取消" : KeyBindings.cancelRecipeDrag.getDisplayName() + " 取消";
            String hint = (count > 1 ? "松开合并 " + count + " 组配方" : "拖到目标分组") + " · " + cancel;
            TooltipRenderer.drawHoveringText(minecraft, hint, mouseX + 12, mouseY);
            return;
        }
        if (!Config.areRecipeBookmarksEnabled()) {
            return;
        }
        if (mouseX > area.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH) {
            hoveredGroupId = -1;
            return;
        }

        boolean hovered = false;
        for (BookmarkGroupDisplay group : groups) {
            if (mouseY < group.area.y || mouseY > group.area.y + group.area.height) {
                continue;
            }
            List<String> tooltips = new ArrayList<>();
            List<IngredientListBatchRenderer> slotRows = new ArrayList<>();

            if (group.group instanceof RecipeBookmarkGroup) {
                tooltips.add(Translator.translateToLocal("hei.tooltip.recipe_group"));
                tooltips.add("点击括号：切换视图 · Shift+M 材料明细 · Shift+T AE取料");
                tooltips.add("拖动括号：合并分组 / AE过滤槽；右键：拆组；Ctrl+Z：撤销");
            } else {
                tooltips.add(Translator.translateToLocal("hei.tooltip.item_group"));
            }

            // Detect if the user is holding either ALT key.
            if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
                tooltips.add(Translator.translateToLocal("hei.tooltip.organizer.1"));
                tooltips.add(Translator.translateToLocal("hei.tooltip.organizer.2"));
                if (group.group instanceof RecipeBookmarkGroup) {
                    tooltips.add(Translator.translateToLocal("hei.tooltip.organizer.3"));
                    if (Config.isAutocraftingEnabled()) {
                        tooltips.add(Translator.translateToLocalFormatted("hei.tooltip.organizer.4", KeyBindings.crafting.getDisplayName()));
                        tooltips.add(KeyBindings.patternBatch.getDisplayName()+"：样板预检；再次按下编码");
                    }
                }
            } else {
                hovered = true;
                tooltips.add(Translator.translateToLocal("hei.tooltip.press_alt"));
                if (group.group instanceof RecipeBookmarkGroup) {
                    if (group.group.id != hoveredGroupId || Minecraft.getSystemTime() - missingCheckedAt > 500) {
                        try {
                        List<IIngredientListElement> missing = ((RecipeBookmarkGroup) group.group).getMissingIngredients();
                        this.missingIngredients = missing.size();
                        this.missingIngredientRenderer.clear();
                        List<IngredientListSlot> slots = new ObjectArrayList<>();
                        for (IIngredientListElement any : missing) {
                            slots.add(new IngredientListSlot(0, 0, INGREDIENT_PADDING));
                        }
                        this.missingIngredientRenderer.add(slots);
                        this.missingIngredientRenderer.set(0, missing);
                        } catch (RuntimeException ex) {
                            this.missingIngredients = -1;
                            this.missingIngredientRenderer.clear();
                        }
                        hoveredGroupId = group.group.id;
                        missingCheckedAt = Minecraft.getSystemTime();
                    }
                    if (missingIngredients < 0) tooltips.add("无法计算返还物流转，请减少数量或检查配方依赖");
                    if (missingIngredients > 0) {
                        tooltips.add(((RecipeBookmarkGroup)group.group).hasExecutionProgress() ? "本次剩余目标缺少材料：" : Translator.translateToLocal("hei.tooltip.missing_ingredients"));
                        slotRows.add(this.missingIngredientRenderer);
                    }
                }
            }
            TooltipRenderer.drawHoveringTextAndItems(minecraft, tooltips, slotRows, mouseX, mouseY);
            break;
        }
        if (!hovered) {
            hoveredGroupId = -1;
        }
    }

    public <I> List<IGhostIngredientHandler.Target<I>> getTargets(I ingredient) {
        List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();
        for (BookmarkGroupDisplay groupDisplay : groups) {
            if (groupDisplay.group instanceof RecipeBookmarkGroup ^ ingredient instanceof RecipeBookmarkItem) {
                continue;
            }
            targets.add(groupDisplay);
        }
        return targets;
    }

    public boolean onKeyPressed(char typedChar, int eventKey) {
        if (KeyBindings.crafting.isActiveAndMatches(eventKey) && Keyboard.isRepeatEvent()) return true;
        if (KeyBindings.crafting.isActiveAndMatches(eventKey)) mezz.jei.nova.CraftDiagnostics.log("organizer mouse="+MouseHelper.getX()+","+MouseHelper.getY()+" bounds="+area+" groups="+groups.size()+" enabled="+Config.isAutocraftingEnabled());
        int mouseX = MouseHelper.getX();
        int mouseY = MouseHelper.getY();
        if (mouseX > area.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH) {
            return false;
        }
        for (BookmarkGroupDisplay group : groups) {
            if (mouseY < group.area.y || mouseY > group.area.y + group.area.height) {
                continue;
            }
            BookmarkList bookmarkList = Internal.getBookmarkList();
            if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
                if (bookmarkList.moveGroup(group.group, true)) {
                    return true;
                }
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
                if (bookmarkList.moveGroup(group.group, false)) {
                    return true;
                }
            }
            if (KeyBindings.bookmark.isActiveAndMatches(eventKey)) {
                if (bookmarkList.removeGroup(group.group)) {
                    return true;
                }
            }
            if(group.group instanceof RecipeBookmarkGroup && !Keyboard.isRepeatEvent()) {
                RecipeBookmarkGroup recipeGroup=(RecipeBookmarkGroup)group.group;
                try {
                    if(KeyBindings.pullGroup.isActiveAndMatches(eventKey)) {mezz.jei.nova.suite.AePull.request(recipeGroup);return true;}
                    if(KeyBindings.groupView.isActiveAndMatches(eventKey)) {cycleView(recipeGroup);return true;}
                    if(KeyBindings.materialDetails.isActiveAndMatches(eventKey)) {mezz.jei.nova.suite.MaterialPanel.toggle(recipeGroup);return true;}
                } catch(RuntimeException ex) {mezz.jei.util.Log.get().warn("Group action failed",ex);mezz.jei.nova.CraftDiagnostics.notice("无法处理配方组，请检查依赖和数量");return true;}
            }
            if (KeyBindings.patternBatch.isActiveAndMatches(eventKey) && !Keyboard.isRepeatEvent() && group.group instanceof RecipeBookmarkGroup) {
                mezz.jei.nova.pattern.PatternBatchClient.start((RecipeBookmarkGroup)group.group);
                return true;
            }
            if (KeyBindings.crafting.isActiveAndMatches(eventKey) && Config.isAutocraftingEnabled()) {
                if (group.group instanceof RecipeBookmarkGroup) {
                    mezz.jei.nova.CraftDiagnostics.log("received recipe group crafting request");
                    ((RecipeBookmarkGroup) group.group).autocraft();
                    return true;
                }
            }
        }
        return false;
    }
}

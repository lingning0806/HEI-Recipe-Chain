package mezz.jei.nova;
import java.awt.Rectangle;
import java.util.*;
public final class GroupDragChecks {
    static void check(boolean b) { if(!b)throw new AssertionError(); }
    public static void main(String[] args) {
        List<GroupDragSelection.Row> rows=Arrays.asList(
            new GroupDragSelection.Row(1,new Rectangle(0,10,120,20)),
            new GroupDragSelection.Row(2,new Rectangle(0,35,120,30)),
            new GroupDragSelection.Row(3,new Rectangle(0,70,120,20)));
        GroupDragSelection down=new GroupDragSelection(rows,1,4,20);
        down.update(5,22);check(down.selectedIds().isEmpty()); // Click jitter.
        down.update(80,80);check(down.selectedIds().equals(Arrays.asList(1,2,3)));
        GroupDragSelection up=new GroupDragSelection(rows,3,4,80);
        up.update(80,20);check(up.selectedIds().equals(down.selectedIds()));
        check(up.previewBounds().equals(down.previewBounds()));
        down.update(90,33);check(down.selectedIds().equals(Arrays.asList(1,2))); // Gap tolerance.
        down.update(200,80);check(down.selectedIds().isEmpty()); // Outside cancels.
        down.update(20,80);check(down.selectedIds().size()==3); // Reenter.
        down.update(20,20);check(down.selectedIds().isEmpty()); // Back to source.
        mezz.jei.nova.suite.PointerGesture click=new mezz.jei.nova.suite.PointerGesture(4,20);
        click.move(7,22);check(!click.isDrag());
        click.move(9,20);check(click.isDrag());
        click.move(4,20);check(click.isDrag()); // Returning after a drag must not toggle the view.
        mezz.jei.nova.suite.PointerGesture held=new mezz.jei.nova.suite.PointerGesture(4,20);
        held.move(4,20);check(!held.isDrag());
        mezz.jei.nova.suite.PointerGesture bracket=new mezz.jei.nova.suite.PointerGesture(4,20,6);
        check(!bracket.isDrag());bracket.move(10,24);check(!bracket.isDrag());
        bracket.move(11,20);check(bracket.isDrag());bracket.move(4,20);check(bracket.isDrag());
        System.out.println("PASS: drag jitter, bidirectional range, preview equivalence, gap tolerance, outside cancel/reentry");
    }
}

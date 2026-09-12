package mezz.jei.nova;

import java.awt.Rectangle;
import java.util.*;

/** Frozen drag geometry: preview and release resolve the exact same selection. */
public final class GroupDragSelection {
    public static final class Row {
        public final int id;
        public final Rectangle area;
        public Row(int id, Rectangle area) { this.id = id; this.area = new Rectangle(area); }
    }
    private final List<Row> rows;
    private final int startIndex, startX, startY;
    private int endIndex = -1;
    public GroupDragSelection(List<Row> rows, int sourceId, int x, int y) {
        this.rows = new ArrayList<>(rows);
        int index = -1;
        for (int i=0;i<rows.size();i++) if(rows.get(i).id==sourceId) { index=i;break; }
        if(index<0)throw new IllegalArgumentException("Missing drag source");
        startIndex=index;startX=x;startY=y;
    }
    public void update(int x, int y) {
        endIndex=-1;
        if(Math.abs(x-startX)<=4 && Math.abs(y-startY)<=4)return;
        int distance=Integer.MAX_VALUE;
        for(int i=0;i<rows.size();i++) {
            Rectangle a=rows.get(i).area;
            if(x<a.x-4 || x>=a.x+a.width+4)continue;
            int dy=y<a.y?a.y-y:y>=a.y+a.height?y-(a.y+a.height-1):0;
            if(dy<=8 && dy<distance) { distance=dy;endIndex=i; }
        }
    }
    public List<Integer> selectedIds() {
        if(endIndex<0 || endIndex==startIndex)return Collections.emptyList();
        Set<Integer> ids=new LinkedHashSet<>();
        for(int i=Math.min(startIndex,endIndex);i<=Math.max(startIndex,endIndex);i++)ids.add(rows.get(i).id);
        return new ArrayList<>(ids);
    }
    public Rectangle previewBounds() {
        if(selectedIds().isEmpty())return null;
        Rectangle bounds=new Rectangle(rows.get(Math.min(startIndex,endIndex)).area);
        for(int i=Math.min(startIndex,endIndex)+1;i<=Math.max(startIndex,endIndex);i++)bounds.add(rows.get(i).area);
        return bounds;
    }
}

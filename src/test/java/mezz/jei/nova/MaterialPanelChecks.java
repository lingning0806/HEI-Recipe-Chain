package mezz.jei.nova;
import mezz.jei.nova.suite.*;
import java.util.*;
import java.awt.Rectangle;
public final class MaterialPanelChecks {
    private static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args) {
        MaterialPanelModel.Row intermediate=new MaterialPanelModel.Row("part",128,0,0,0,0,0,0,0);
        MaterialPanelModel.Row enough=new MaterialPanelModel.Row("gold",304,304,0,1805,0,1501,0,0);
        MaterialPanelModel.Row missing=new MaterialPanelModel.Row("iron",1248,1248,10,0,1238,0,0,0);
        MaterialPanelModel.Row output=new MaterialPanelModel.Row("table",0,0,0,0,0,0,16,9);
        java.util.List<MaterialPanelModel.Row> rows=Arrays.asList(intermediate,enough,missing,output);
        java.util.List<MaterialPanelModel.Row> shown=MaterialPanelModel.filter(rows,MaterialPanelModel.Section.MATERIALS);
        check(shown.equals(Arrays.asList(missing,enough)));check(rows.get(0)==intermediate);
        check(MaterialPanelModel.filter(rows,MaterialPanelModel.Section.MISSING).equals(Arrays.asList(missing)));
        check(MaterialPanelModel.filter(rows,MaterialPanelModel.Section.OUTPUTS).equals(Arrays.asList(output)));
        check(MaterialPanelModel.filter(rows,MaterialPanelModel.Section.REMAINDER).equals(Arrays.asList(enough)));
        check(Arrays.equals(output.values(MaterialPanelModel.Section.OUTPUTS),new long[]{16,7,9}));
        check(MaterialPanelModel.number(1248).equals("1,248"));check(MaterialPanelModel.number(0).equals("—"));
        check(MaterialPanelModel.compact(Long.MAX_VALUE).contains("E"));
        for(int[] size:new int[][]{{320,240},{480,270},{766,386},{1920,1080}}) {
            MaterialPanelLayout layout=new MaterialPanelLayout(size[0],size[1]);Rectangle screen=new Rectangle(0,0,size[0],size[1]);
            check(screen.contains(layout.panel));check(layout.panel.contains(layout.close));check(layout.panel.contains(layout.previous));check(layout.panel.contains(layout.next));
            for(Rectangle tab:layout.tabs)check(layout.panel.contains(tab));
            for(int i=0;i<layout.rowCount;i++){check(layout.panel.contains(layout.row(i)));check(layout.row(i).y+layout.rowHeight<=layout.footerY);}
            check(layout.nameWidth>28);check(layout.columnRight(2)<layout.panel.x+layout.panel.width);
            check(layout.pages(0)==1);check(layout.clamp(999,5)==layout.pages(5)-1);check(layout.clamp(-1,5)==0);
        }
        System.out.println("PASS material panel: categorized rows, zero/intermediate filtering, goal preservation, quantity formatting, responsive bounds and paging");
    }
}

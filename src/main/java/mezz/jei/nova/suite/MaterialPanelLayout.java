package mezz.jei.nova.suite;
import java.awt.Rectangle;
/** Geometry in scaled GUI pixels, shared by rendering and hit testing. */
public final class MaterialPanelLayout {
    public final Rectangle panel,close,previous,next;
    public final Rectangle[] tabs;
    public final int bodyX,bodyWidth,headerY,rowsY,rowHeight=22,rowCount,footerY,nameWidth,columnWidth;
    public MaterialPanelLayout(int screenWidth,int screenHeight) {
        int w=Math.max(1,Math.min(500,screenWidth-24)),h=Math.max(1,Math.min(354,screenHeight-24));
        panel=new Rectangle((screenWidth-w)/2,(screenHeight-h)/2,w,h);
        bodyX=panel.x+12;bodyWidth=w-24;
        close=new Rectangle(panel.x+w-28,panel.y+8,18,18);
        tabs=new Rectangle[MaterialPanelModel.Section.values().length];
        for(int i=0;i<tabs.length;i++){int left=bodyX+i*bodyWidth/tabs.length,right=bodyX+(i+1)*bodyWidth/tabs.length;tabs[i]=new Rectangle(left,panel.y+52,right-left-2,20);}
        headerY=panel.y+81;rowsY=panel.y+98;footerY=panel.y+h-64;
        rowCount=Math.max(1,(footerY-rowsY-4)/rowHeight);
        columnWidth=Math.max(38,Math.min(76,(bodyWidth-100)/3));nameWidth=bodyWidth-columnWidth*3;
        previous=new Rectangle(panel.x+w-86,panel.y+h-21,24,16);next=new Rectangle(panel.x+w-36,panel.y+h-21,24,16);
    }
    public Rectangle row(int index){return new Rectangle(bodyX,rowsY+index*rowHeight,bodyWidth,rowHeight);}
    public int pages(int rows){return Math.max(1,(rows+rowCount-1)/rowCount);}
    public int clamp(int page,int rows){return Math.max(0,Math.min(page,pages(rows)-1));}
    public int columnRight(int index){return bodyX+nameWidth+(index+1)*columnWidth-6;}
}

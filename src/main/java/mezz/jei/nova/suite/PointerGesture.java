package mezz.jei.nova.suite;
/** Once the pointer leaves click tolerance, returning to the origin is still a drag. */
public final class PointerGesture {
    public final int x,y;
    private boolean dragged;
    private final int tolerance;
    public PointerGesture(int x,int y){this(x,y,4);}
    public PointerGesture(int x,int y,int tolerance){if(tolerance<0)throw new IllegalArgumentException();this.x=x;this.y=y;this.tolerance=tolerance;}
    public void move(int x,int y){dragged|=Math.abs((long)x-this.x)>tolerance || Math.abs((long)y-this.y)>tolerance;}
    public boolean isDrag(){return dragged;}
}

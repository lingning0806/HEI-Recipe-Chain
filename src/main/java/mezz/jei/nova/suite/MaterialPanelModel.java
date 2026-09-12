package mezz.jei.nova.suite;

import java.util.*;

/** Presentation categories inspired by JEI-unofficial RecipeChainTooltipModel; never edits the ledger. */
public final class MaterialPanelModel {
    public enum Section {
        MATERIALS("所需材料"), MISSING("缺口"), AVAILABLE("已有"), OUTPUTS("产物"), REMAINDER("结余"), ALL("全部");
        public final String title;
        Section(String title){this.title=title;}
    }
    public static final class Row {
        public final String key;
        public final long usage,needed,player,network,missing,remainder,goal,remainingGoal;
        public Row(String key,long usage,long needed,long player,long network,long missing,long remainder,long goal,long remainingGoal) {
            this.key=key;this.usage=usage;this.needed=needed;this.player=player;this.network=network;this.missing=missing;
            this.remainder=remainder;this.goal=goal;this.remainingGoal=remainingGoal;
        }
        public long available(){return Math.addExact(player,network);}
        public boolean shown(Section section) {
            switch(section) {
                case MATERIALS:return needed>0 || missing>0;
                case MISSING:return missing>0;
                case AVAILABLE:return available()>0;
                case OUTPUTS:return goal>0;
                case REMAINDER:return remainder>0;
                default:return true;
            }
        }
        public long[] values(Section section) {
            switch(section) {
                case MISSING:return new long[]{missing,player,network};
                case AVAILABLE:return new long[]{player,network,available()};
                case OUTPUTS:return new long[]{goal,Math.max(0,goal-remainingGoal),remainingGoal};
                case REMAINDER:return new long[]{remainder,available(),usage};
                case ALL:return new long[]{usage,available(),missing};
                default:return new long[]{needed,available(),missing};
            }
        }
    }
    public static List<Row> filter(List<Row> rows,Section section) {
        List<Row> result=new ArrayList<>();for(Row row:rows)if(row.shown(section))result.add(row);
        // Stable ordering within each status keeps moving stock from constantly rearranging the list.
        if(section==Section.MATERIALS || section==Section.ALL)result.sort(Comparator.comparingInt(r->r.missing>0?0:1));
        return result;
    }
    public static String[] headers(Section section) {
        switch(section) {
            case MISSING:return new String[]{"仍缺","背包 / 格","AE 网络"};
            case AVAILABLE:return new String[]{"背包 / 格","AE 网络","合计"};
            case OUTPUTS:return new String[]{"原目标","已完成","本次剩余"};
            case REMAINDER:return new String[]{"预计剩余","当前拥有","总用量"};
            case ALL:return new String[]{"总用量","当前可用","仍缺"};
            default:return new String[]{"起步需备","当前可用","仍缺"};
        }
    }
    public static String description(Section section) {
        switch(section) {
            case MATERIALS:return "按返还物流转计算起步材料；已有库存与最终缺口分别列出。";
            case MISSING:return "完成本次剩余目标还需补充的材料。";
            case AVAILABLE:return "本配方组相关材料的背包、真实合成格与 AE 库存。";
            case OUTPUTS:return "保留原书签目标；已完成与本次剩余单独记录。";
            case REMAINDER:return "假设补齐缺口并完成计划后的库存，包含仍可使用的工具。";
            default:return "包括中间品；总用量是配方吞吐，不等于需准备的基础材料。";
        }
    }
    public static String empty(Section section) {
        switch(section) {
            case MISSING:return "当前没有材料缺口";
            case AVAILABLE:return "当前没有检测到相关库存";
            case REMAINDER:return "此计划没有预计结余";
            default:return "这一分类没有材料";
        }
    }
    public static String number(long amount) {return amount==0?"—":String.format(Locale.ROOT,"%,d",amount);}
    public static String compact(long amount) {
        if(amount==0)return "—";
        String[] suffix={"","K","M","B","T","P","E"};int i=0;double value=amount;
        while(value>=1000 && i<suffix.length-1){value/=1000;i++;}
        return i==0?Long.toString(amount):String.format(Locale.ROOT,value<10?"%.2f%s":value<100?"%.1f%s":"%.0f%s",value,suffix[i]);
    }
}

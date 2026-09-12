package mezz.jei.nova;
import mezz.jei.nova.preferences.PreferenceRules;
import java.util.*;
public final class PreferenceChecks {
    private static void check(boolean b) {if(!b) throw new AssertionError();}
    private static PreferenceRules.Candidate<String> c(String value,String output,String input,String recipe) {return new PreferenceRules.Candidate<>(value,Arrays.asList(output),Arrays.asList(input),Arrays.asList(recipe));}
    public static void main(String[] args) {
        PreferenceRules r=PreferenceRules.parse(Arrays.asList("$ comment","output = table","input = oak; birch","recipe = crafting; machine"));
        check("oak".equals(r.material(Arrays.asList("table"),Arrays.asList(c("birch","table","birch","crafting"),c("oak","table","oak","crafting")),Arrays.asList("crafting"))));
        check("A".equals(r.choose(Arrays.asList(c("A","table","oak","crafting"),c("B","table","birch","crafting")))));
        check(r.choose(Arrays.asList(c("A","table","oak","machine"),c("B","table","birch","crafting")))==null);
        check(r.choose(Arrays.asList(c("A","table","oak","crafting"),c("B","table","oak","crafting")))==null);
        r=PreferenceRules.parse(Arrays.asList("$$","output = ignored","input = ignored","$$","output = *","input = (mod:thermal* | ore:ingotIron) & !mod:bad; *"));
        check("A".equals(r.material(Arrays.asList("table"),Arrays.asList(c("A","table","mod:thermalfoundation","x"),c("B","table","mod:bad","y")))));
        r=PreferenceRules.parse(Arrays.asList("output = table","input ="," oak;"," birch"));
        check("A".equals(r.material(Arrays.asList("table"),Arrays.asList(c("B","table","birch","x"),c("A","table","oak","y")))));
        for(List<String> bad:Arrays.asList(Arrays.asList("output = *","input = (oak"),Arrays.asList("input = oak"),Arrays.asList("output = *","input = oak","input = birch"),Arrays.asList("$$"))) {
            try {PreferenceRules.parse(bad);throw new AssertionError();} catch(IllegalArgumentException expected) {}
        }
        check(PreferenceRules.EMPTY.choose(Arrays.asList(c("A","x","y","z"),c("B","x","y","z")))==null);
        System.out.println("PASS preferences: ranks, ambiguity, partial-order ties, wildcard, boolean, comments, multiline, invalid rules");
    }
}

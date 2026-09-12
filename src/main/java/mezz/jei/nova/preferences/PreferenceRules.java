package mezz.jei.nova.preferences;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Java 8 adaptation of StardustMINUS-01/JustEnoughItems 1.20.1 recipe preferences.
 * Ordered rules and non-dominated input/output/recipe ranks; ambiguous routes remain unresolved.
 * See REFERENCE.md for provenance and 1.12 selector differences. */
public final class PreferenceRules {
    public static final PreferenceRules EMPTY=new PreferenceRules(Collections.emptyList());
    public static final class Candidate<T> {
        public final T value; final List<String> outputs,inputs,recipes;
        public Candidate(T value,List<String> outputs,List<String> inputs,List<String> recipes) {
            this.value=value;this.outputs=outputs;this.inputs=inputs;this.recipes=recipes;
        }
    }
    private static final class Rule {
        final Expression output,input,recipe;
        Rule(Map<String,String> values) {
            if(!values.containsKey("input") && !values.containsKey("recipe")) throw new IllegalArgumentException("Rule requires input or recipe");
            output=new Expression(values.get("output"));
            input=values.containsKey("input")?new Expression(values.get("input")):null;
            recipe=values.containsKey("recipe")?new Expression(values.get("recipe")):null;
        }
    }
    private final List<Rule> rules;
    private PreferenceRules(List<Rule> rules) {this.rules=rules;}
    public boolean isEmpty() {return rules.isEmpty();}
    public <T> T choose(List<Candidate<T>> candidates) {
        if(candidates.size()==1) return candidates.get(0).value;
        for(Rule rule:rules) {
            List<Candidate<T>> eligible=new ArrayList<>();List<int[]> ranks=new ArrayList<>();boolean inputActive=false,recipeActive=false;
            for(Candidate<T> c:candidates) {
                int output=rule.output.rank(c.outputs);if(output<0) continue;
                int input=rule.input==null?-1:rule.input.rank(c.inputs),recipe=rule.recipe==null?-1:rule.recipe.rank(c.recipes);
                eligible.add(c);ranks.add(new int[]{output,input,recipe});inputActive|=input>=0;recipeActive|=recipe>=0;
            }
            T best=null;int count=0;
            for(int i=0;i<eligible.size();i++) {
                int[] rank=ranks.get(i);if(inputActive && rank[1]<0 || recipeActive && rank[2]<0) continue;
                boolean dominated=false;
                for(int j=0;j<eligible.size();j++) {
                    int[] other=ranks.get(j);if(inputActive && other[1]<0 || recipeActive && other[2]<0) continue;
                    boolean noWorse=other[0]<=rank[0] && (!inputActive || other[1]<=rank[1]) && (!recipeActive || other[2]<=rank[2]);
                    boolean better=other[0]<rank[0] || inputActive && other[1]<rank[1] || recipeActive && other[2]<rank[2];
                    if(noWorse && better) {dominated=true;break;}
                }
                if(!dominated) {best=eligible.get(i).value;count++;}
            }
            if(count==1) return best;
        }
        return null;
    }
    /** Per-slot input preference is an HEI extension sharing the reference expression syntax. */
    public <T> T material(List<String> output,List<Candidate<T>> alternatives) {
        return material(output,alternatives,Collections.emptyList());
    }
    public <T> T material(List<String> output,List<Candidate<T>> alternatives,List<String> recipeIds) {
        for(Rule rule:rules) {
            if(rule.input==null || rule.output.rank(output)<0 || rule.recipe!=null && rule.recipe.rank(recipeIds)<0) continue;
            int best=Integer.MAX_VALUE,count=0;T result=null;
            for(Candidate<T> c:alternatives) {
                int rank=rule.input.rank(c.inputs);if(rank<0) continue;
                if(rank<best) {best=rank;result=c.value;count=1;} else if(rank==best) count++;
            }
            if(count==1) return result;
        }
        return null;
    }
    public static PreferenceRules parse(List<String> lines) {
        List<Rule> rules=new ArrayList<>();Map<String,String> values=new LinkedHashMap<>();String key=null;boolean block=false;
        for(String raw:lines) {
            StringBuilder cleaned=new StringBuilder();
            for(int i=0;i<raw.length();i++) {
                char c=raw.charAt(i);
                if(c=='$' && i+1<raw.length() && raw.charAt(i+1)=='$') {block=!block;i++;continue;}
                if(!block && c=='$') break;
                if(!block) cleaned.append(c);
            }
            String line=cleaned.toString().trim();if(line.isEmpty()) continue;
            if(line.startsWith("[[") && line.endsWith("]]")) {key=null;continue;}
            int eq=line.indexOf('=');
            if(eq>=0) {
                key=line.substring(0,eq).trim();String value=line.substring(eq+1).trim();
                if(key.equals("output")) {if(!values.isEmpty()) rules.add(new Rule(values));values=new LinkedHashMap<>();}
                if(!Arrays.asList("output","input","recipe").contains(key)) {key=null;continue;}
                if(!key.equals("output") && !values.containsKey("output")) throw new IllegalArgumentException("Missing preceding output");
                if(values.putIfAbsent(key,value)!=null) throw new IllegalArgumentException("Duplicate key: "+key);
            } else if(key!=null) values.put(key,values.get(key)+" "+line);
        }
        if(block) throw new IllegalArgumentException("Unclosed block comment");
        if(!values.isEmpty()) rules.add(new Rule(values));
        return new PreferenceRules(Collections.unmodifiableList(rules));
    }
    static final class Expression {
        final List<Predicate<List<String>>> tiers=new ArrayList<>();
        Expression(String text) {
            if(text==null || text.length()>8192) throw new IllegalArgumentException("Invalid expression");
            Parser p=new Parser(text);
            do {tiers.add(p.or(0));} while(p.take(';'));
            if(!p.end()) throw new IllegalArgumentException("Unexpected expression token at "+p.pos);
        }
        int rank(List<String> values) {for(int i=0;i<tiers.size();i++) if(tiers.get(i).test(values)) return i;return -1;}
    }
    static final class Parser {
        final String text;int pos;
        Parser(String text) {this.text=text;}
        void space() {while(pos<text.length() && Character.isWhitespace(text.charAt(pos))) pos++;}
        boolean end() {space();return pos==text.length();}
        boolean take(char c) {space();if(pos<text.length() && text.charAt(pos)==c) {pos++;return true;}return false;}
        Predicate<List<String>> or(int depth) {
            Predicate<List<String>> p=and(depth);while(take('|')) p=p.or(and(depth));return p;
        }
        Predicate<List<String>> and(int depth) {
            Predicate<List<String>> p=atom(depth);while(take('&')) p=p.and(atom(depth));return p;
        }
        Predicate<List<String>> atom(int depth) {
            if(depth>32) throw new IllegalArgumentException("Expression nesting limit");
            if(take('!')) return atom(depth+1).negate();
            if(take('(')) {Predicate<List<String>> p=or(depth+1);if(!take(')')) throw new IllegalArgumentException("Missing )");return p;}
            space();int start=pos;while(pos<text.length() && !Character.isWhitespace(text.charAt(pos)) && "!&|();".indexOf(text.charAt(pos))<0) pos++;
            if(start==pos) throw new IllegalArgumentException("Missing selector");
            String token=text.substring(start,pos);StringBuilder regex=new StringBuilder("^");
            for(int i=0;i<token.length();i++) {char c=token.charAt(i);regex.append(c=='*'?".*":Pattern.quote(String.valueOf(c)));}regex.append('$');
            Pattern pattern=Pattern.compile(regex.toString());return values->values.stream().anyMatch(v->pattern.matcher(v).matches());
        }
    }
}

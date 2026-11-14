package ru.avganbaev.sevenstarsbuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Interprets build scripts: name, def, let, loop/end, fill, set. */
public final class ScriptParser {
    public static final class ParseException extends Exception {
        public ParseException(String m) { super(m); }
    }

    private static final int MAX_OPS = 20000, MAX_FILL = 32768, MAX_ITER = 10000;
    private static final Pattern DEF_REF = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern BLOCK_OK = Pattern.compile("[a-z0-9_.:/-]+(\\[[a-z0-9_=,]*\\])?");

    private final Map<String, String> defs = new HashMap<>();
    private final Map<String, Integer> vars = new HashMap<>();
    private final List<String[]> toks = new ArrayList<>();
    private final List<Integer> nums = new ArrayList<>();
    private final List<BuildDefinition.Op> ops = new ArrayList<>();
    private String name = "Постройка";
    private int curLine;

    public static BuildDefinition parse(String text) throws ParseException { return new ScriptParser().run(text); }

    private ParseException err(String m) { return new ParseException("строка " + curLine + ": " + m); }

    private BuildDefinition run(String text) throws ParseException {
        if (text == null) throw new ParseException("буфер обмена пуст");
        List<String> raw = text.replace('\u00a0', ' ').replace("\uFEFF", "").lines().toList();
        for (int i = 0; i < raw.size(); i++) {
            String l = raw.get(i).strip();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("//") || l.startsWith("```")) continue;
            String[] t;
            if (l.regionMatches(true, 0, "name ", 0, 5)) t = new String[]{"name", l.substring(5).strip()};
            else t = l.split("\\s+");
            toks.add(t);
            nums.add(i + 1);
        }
        if (toks.isEmpty()) throw new ParseException("скрипт пуст");
        exec(0, toks.size());
        if (ops.isEmpty()) throw new ParseException("в скрипте нет команд fill/set");
        return new BuildDefinition(name, ops);
    }

    private void exec(int from, int to) throws ParseException {
        for (int i = from; i < to; i++) {
            String[] t = toks.get(i);
            curLine = nums.get(i);
            switch (t[0].toLowerCase()) {
                case "name" -> { if (t.length > 1 && !t[1].isEmpty()) name = t[1]; }
                case "def" -> { need(t, 3); defs.put(t[1], t[2]); }
                case "let" -> { need(t, 3); vars.put(t[1], eval(t[2])); }
                case "fill" -> {
                    need(t, 8);
                    int x1 = eval(t[1]), y1 = eval(t[2]), z1 = eval(t[3]), x2 = eval(t[4]), y2 = eval(t[5]), z2 = eval(t[6]);
                    long vol = (long) (Math.abs(x2 - x1) + 1) * (Math.abs(y2 - y1) + 1) * (Math.abs(z2 - z1) + 1);
                    if (vol > MAX_FILL) throw err("fill больше " + MAX_FILL + " блоков, разбейте на части");
                    add(x1, y1, z1, x2, y2, z2, block(t[7]));
                }
                case "set" -> {
                    need(t, 5);
                    int x = eval(t[1]), y = eval(t[2]), z = eval(t[3]);
                    add(x, y, z, x, y, z, block(t[4]));
                }
                case "loop" -> {
                    if (t.length < 4 || t.length > 5) throw err("loop переменная от до [шаг]");
                    int depth = 1, j = i + 1;
                    for (; j < to; j++) {
                        String c = toks.get(j)[0].toLowerCase();
                        if (c.equals("loop")) depth++;
                        else if (c.equals("end") && --depth == 0) break;
                    }
                    if (j >= to) throw err("нет end для loop");
                    int a = eval(t[2]), b = eval(t[3]), step = t.length == 5 ? eval(t[4]) : 1;
                    if (step == 0) throw err("шаг цикла не может быть 0");
                    Integer old = vars.get(t[1]);
                    int n = 0;
                    for (int v = a; step > 0 ? v <= b : v >= b; v += step) {
                        if (++n > MAX_ITER) throw err("слишком много итераций");
                        vars.put(t[1], v);
                        exec(i + 1, j);
                    }
                    if (old == null) vars.remove(t[1]); else vars.put(t[1], old);
                    i = j;
                }
                case "end" -> throw err("лишний end");
                default -> throw err("неизвестная команда '" + t[0] + "'");
            }
        }
    }

    private void need(String[] t, int n) throws ParseException {
        if (t.length != n) throw err(t[0] + ": нужно аргументов " + (n - 1) + ", есть " + (t.length - 1));
    }

    private void add(int x1, int y1, int z1, int x2, int y2, int z2, String b) throws ParseException {
        if (ops.size() >= MAX_OPS) throw err("слишком много команд (макс. " + MAX_OPS + ")");
        ops.add(new BuildDefinition.Op(x1, y1, z1, x2, y2, z2, b));
    }

    private String block(String tok) throws ParseException {
        Matcher m = DEF_REF.matcher(tok);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = defs.get(m.group(1));
            if (v == null) throw err("неизвестный def $" + m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        String b = sb.toString().toLowerCase();
        if (!b.contains(":")) b = "minecraft:" + b;
        if (!BLOCK_OK.matcher(b).matches()) throw err("некорректный блок '" + tok + "'");
        return b;
    }

    private int eval(String s) throws ParseException {
        Expr e = new Expr(s);
        int v = e.expr();
        if (e.p != s.length()) throw err("ошибка в выражении '" + s + "'");
        return v;
    }

    private final class Expr {
        final String s; int p;
        Expr(String s) { this.s = s; }
        int expr() throws ParseException {
            int v = term();
            while (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) {
                char o = s.charAt(p++);
                int r = term();
                v = o == '+' ? v + r : v - r;
            }
            return v;
        }
        int term() throws ParseException {
            int v = factor();
            while (p < s.length() && (s.charAt(p) == '*' || s.charAt(p) == '/' || s.charAt(p) == '%')) {
                char o = s.charAt(p++);
                int r = factor();
                if (o != '*' && r == 0) throw err("деление на ноль в '" + s + "'");
                v = o == '*' ? v * r : o == '/' ? v / r : v % r;
            }
            return v;
        }
        int factor() throws ParseException {
            if (p >= s.length()) throw err("ошибка в выражении '" + s + "'");
            char c = s.charAt(p);
            if (c == '-') { p++; return -factor(); }
            if (c == '+') { p++; return factor(); }
            if (c == '(') {
                p++;
                int v = expr();
                if (p >= s.length() || s.charAt(p++) != ')') throw err("нет ')' в '" + s + "'");
                return v;
            }
            int st = p;
            if (Character.isDigit(c)) {
                while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
                try { return Integer.parseInt(s.substring(st, p)); } catch (NumberFormatException ex) { throw err("слишком большое число"); }
            }
            if (Character.isLetter(c) || c == '_') {
                while (p < s.length() && (Character.isLetterOrDigit(s.charAt(p)) || s.charAt(p) == '_')) p++;
                Integer v = vars.get(s.substring(st, p));
                if (v == null) throw err("неизвестная переменная '" + s.substring(st, p) + "'");
                return v;
            }
            throw err("непонятный символ '" + c + "' в '" + s + "'");
        }
    }
}

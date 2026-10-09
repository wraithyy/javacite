package io.github.wraithyy.javacite.core.generate;

/** Minimal attribute-safe XML escaping for the string-built generators. */
final class XmlEscape {

    private XmlEscape() {}

    static String attr(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

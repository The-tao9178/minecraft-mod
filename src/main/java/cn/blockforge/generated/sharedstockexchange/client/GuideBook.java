package cn.blockforge.generated.sharedstockexchange.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易所手册：用原版「成书」界面翻看教程。
 *
 * 每段教程（标题 + 正文）都放在语言文件里，这里把正文按原版书页的宽度折好行，
 * 再按每页能放下的行数切成若干页，交给原版 {@link BookViewScreen} 显示。
 * 用原版界面是因为它的字体、翻页按钮、书页背景都是游戏自带的，
 * 不会出现「界面对了但没有字」这种自己排版才会出的毛病。
 */
public final class GuideBook {
    /**
     * 最多找这么多段。真正的段数是从语言文件里数出来的（page.N.title / page.N.body 连着有就继续数），
     * 以后往教程里加内容只用改语言文件，不用再回来改这个类。
     */
    private static final int MAX_SECTIONS = 64;

    /** 原版书页正文的宽度（像素），和 BookViewScreen.TEXT_WIDTH 一致。 */
    private static final int PAGE_TEXT_WIDTH = 114;
    /** 一本书一页最多放几行：书页正文高 128、每行 9，也就是 14 行，这里留一行余量。 */
    private static final int LINES_PER_PAGE = 13;

    private GuideBook() {
    }

    /** 打开手册（只有客户端才有界面）。 */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages(minecraft.font))));
    }

    /** 语言文件里到底写了几段教程：从 1 开始一段一段数，断了就停。 */
    private static int sections() {
        int found = 0;
        for (int section = 1; section <= MAX_SECTIONS; section++) {
            if (!hasText(key(section, "title")) || !hasText(key(section, "body"))) {
                break;
            }
            found = section;
        }
        return found;
    }

    /** 这个 key 在语言文件里有没有正文（缺 key 时原版会把 key 原样返回）。 */
    private static boolean hasText(String key) {
        String text = Component.translatable(key).getString();
        return !text.isEmpty() && !key.equals(text);
    }

    /**
     * 教程全文，纯文本 —— 给 MCphone 书城的本地书用的。
     *
     * 手机书城只认 {@code mcphone/books/} 里的 .txt 文件，不认游戏里的翻译键，
     * 所以要把同一份教程按「第 N 章」分好章再写成文件。章节号用中文数字是因为
     * 书城自己的切章规则认「第 N 章」这种整行标题（见 {@code TxtChapters}），
     * 不这么写整本书会被当成一段、目录里只有「第 1 段」。
     *
     * 内容永远以语言文件为准：{@link #sections()} 数出来几段就导几段，
     * 以后加教程不用回来改这里，也不会和游戏里的书对不上。
     */
    public static String plainText() {
        StringBuilder out = new StringBuilder(8192);
        int sections = sections();
        // 章节号跟着玩家当前语言走：中文书用「第一章」，英文书用「Chapter 1」，
        // 两种写法书城的切章规则都认
        boolean chinese = currentLanguage().startsWith("zh");
        for (int section = 1; section <= sections; section++) {
            String title = Component.translatable(key(section, "title")).getString();
            String body = Component.translatable(key(section, "body")).getString();
            String prefix = chinese
                    ? "第" + chineseNumber(section) + "章 "
                    : "Chapter " + section + " ";
            // 书城的切章规则要求整行标题不超过 40 个字符，超了这一章会被当成正文。
            // 英文标题本来就长，超了就把标题截一下，别让整本章掉出去
            int maxTitle = Math.max(1, 40 - prefix.length());
            if (title.length() > maxTitle) {
                title = title.substring(0, Math.max(1, maxTitle - 1)) + "\u2026";
            }
            out.append(prefix).append(title).append('\n');
            out.append('\n');
            out.append(body).append('\n');
            out.append('\n');
        }
        return out.toString();
    }

    /** 玩家当前选的语言，形如 {@code zh_cn} / {@code en_us} */
    private static String currentLanguage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getLanguageManager() == null) {
            return "";
        }
        return minecraft.getLanguageManager().getSelected();
    }

    /** 1 → 一，11 → 十一，23 → 二十三。教程段数很少，不用管"零"和百位以上 */
    private static String chineseNumber(int value) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (value < 10) {
            return digits[value];
        }
        if (value < 20) {
            return "十" + (value % 10 == 0 ? "" : digits[value % 10]);
        }
        if (value < 100) {
            return digits[value / 10] + "十" + (value % 10 == 0 ? "" : digits[value % 10]);
        }
        return Integer.toString(value);
    }

    /** 把所有教程段落折行、分页，装成原版书页要的格式。 */
    private static List<Component> pages(Font font) {
        List<Component> lines = new ArrayList<>();
        int sections = sections();
        for (int section = 1; section <= sections; section++) {
            // 段落之间空一行，读起来不挤
            if (section > 1) {
                lines.add(Component.literal(""));
            }
            lines.add(Component.translatable(key(section, "title"))
                    .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_BLUE));
            String body = Component.translatable(key(section, "body")).getString();
            for (String paragraph : body.split("\n", -1)) {
                if (paragraph.isEmpty()) {
                    lines.add(Component.literal(""));
                    continue;
                }
                for (String piece : wrap(font, paragraph)) {
                    lines.add(Component.literal(piece));
                }
            }
        }

        List<Component> pages = new ArrayList<>();
        int cursor = 0;
        while (cursor < lines.size()) {
            // 页首的空行没有意义，跳过
            while (cursor < lines.size() && lines.get(cursor).getString().isEmpty()) {
                cursor++;
            }
            if (cursor >= lines.size()) {
                break;
            }
            int end = Math.min(lines.size(), cursor + LINES_PER_PAGE);
            MutableComponent page = Component.empty();
            for (int i = cursor; i < end; i++) {
                if (i > cursor) {
                    page.append("\n");
                }
                page.append(lines.get(i));
            }
            pages.add(page);
            cursor = end;
        }
        if (pages.isEmpty()) {
            pages.add(Component.translatable("gui.shared_stock_exchange.guide_title"));
        }
        return pages;
    }

    /**
     * 把一段文字按书页宽度折成多行。
     *
     * 不能直接用 {@code font.split(...)}：1.21.1 里它返回的是 {@code FormattedCharSequence}，
     * 拿不到字符串，早先按 {@code FormattedText} 取内容就一行都取不到（书里因此没有字）。
     * 这里改成用字的真实宽度自己切：中文逐字断，英文尽量在空格处断，不会把一个词劈开。
     */
    private static List<String> wrap(Font font, String paragraph) {
        List<String> lines = new ArrayList<>();
        String rest = paragraph;
        while (!rest.isEmpty()) {
            if (font.width(rest) <= PAGE_TEXT_WIDTH) {
                lines.add(rest);
                break;
            }
            String head = font.plainSubstrByWidth(rest, PAGE_TEXT_WIDTH);
            if (head.isEmpty()) {
                // 一个字都放不下（正常情况下不会发生），硬切一个字符，免得死循环
                head = rest.substring(0, rest.offsetByCodePoints(0, 1));
            } else {
                int space = head.lastIndexOf(' ');
                if (space > 0) {
                    head = head.substring(0, space);
                }
            }
            lines.add(head.stripTrailing());
            int next = head.length();
            while (next < rest.length() && rest.charAt(next) == ' ') {
                next++;
            }
            rest = rest.substring(next);
        }
        return lines;
    }

    private static String key(int section, String part) {
        return "guide.shared_stock_exchange.page." + section + "." + part;
    }
}

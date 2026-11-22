package cn.blockforge.generated.sharedstockexchange.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;

public final class GuideBook {
   private static final int MAX_SECTIONS = 64;
   private static final int PAGE_TEXT_WIDTH = 114;
   private static final int LINES_PER_PAGE = 13;

   private GuideBook() {
   }

   public static void open() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.screen == null && minecraft.player != null) {
         List<Component> pages = pages(minecraft.font);
         minecraft.setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
            @Override
            public int getPageCount() {
               return pages.size();
            }

            @Override
            public FormattedText getPageRaw(int index) {
               return pages.get(index);
            }
         }));
      }
   }

   private static int sections() {
      int found = 0;
      int section = 1;

      while (section <= 64 && hasText(key(section, "title")) && hasText(key(section, "body"))) {
         found = section++;
      }

      return found;
   }

   private static boolean hasText(String key) {
      String text = Component.translatable(key).getString();
      return !text.isEmpty() && !key.equals(text);
   }

   public static String plainText() {
      StringBuilder out = new StringBuilder(8192);
      int sections = sections();
      boolean chinese = currentLanguage().startsWith("zh");

      for (int section = 1; section <= sections; section++) {
         String title = Component.translatable(key(section, "title")).getString();
         String body = Component.translatable(key(section, "body")).getString();
         String prefix = chinese ? "第" + chineseNumber(section) + "章 " : "Chapter " + section + " ";
         int maxTitle = Math.max(1, 40 - prefix.length());
         if (title.length() > maxTitle) {
            title = title.substring(0, Math.max(1, maxTitle - 1)) + "…";
         }

         out.append(prefix).append(title).append('\n');
         out.append('\n');
         out.append(body).append('\n');
         out.append('\n');
      }

      return out.toString();
   }

   private static String currentLanguage() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft != null && minecraft.getLanguageManager() != null ? minecraft.getLanguageManager().getSelected() : "";
   }

   private static String chineseNumber(int value) {
      String[] digits = new String[]{"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
      if (value < 10) {
         return digits[value];
      } else if (value < 20) {
         return "十" + (value % 10 == 0 ? "" : digits[value % 10]);
      } else {
         return value < 100 ? digits[value / 10] + "十" + (value % 10 == 0 ? "" : digits[value % 10]) : Integer.toString(value);
      }
   }

   private static List<Component> pages(Font font) {
      List<Component> lines = new ArrayList<>();
      int sections = sections();

      for (int section = 1; section <= sections; section++) {
         if (section > 1) {
            lines.add(Component.literal(""));
         }

         lines.add(Component.translatable(key(section, "title")).withStyle(new ChatFormatting[]{ChatFormatting.BOLD, ChatFormatting.DARK_BLUE}));
         String body = Component.translatable(key(section, "body")).getString();

         for (String paragraph : body.split("\n", -1)) {
            if (paragraph.isEmpty()) {
               lines.add(Component.literal(""));
            } else {
               for (String piece : wrap(font, paragraph)) {
                  lines.add(Component.literal(piece));
               }
            }
         }
      }

      List<Component> pages = new ArrayList<>();
      int cursor = 0;

      while (cursor < lines.size()) {
         while (cursor < lines.size() && lines.get(cursor).getString().isEmpty()) {
            cursor++;
         }

         if (cursor >= lines.size()) {
            break;
         }

         int end = Math.min(lines.size(), cursor + 13);
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

   private static List<String> wrap(Font font, String paragraph) {
      List<String> lines = new ArrayList<>();
      String rest = paragraph;

      while (!rest.isEmpty()) {
         if (font.width(rest) <= 114) {
            lines.add(rest);
            break;
         }

         String head = font.plainSubstrByWidth(rest, 114);
         if (head.isEmpty()) {
            head = rest.substring(0, rest.offsetByCodePoints(0, 1));
         } else {
            int space = head.lastIndexOf(32);
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

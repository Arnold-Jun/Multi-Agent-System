package com.zhouruojun.pdfTest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws IOException {
        // 可选：降低日志噪音
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");

        String inputPath = "Ruojun Zhou.pdf";
        String outputPath = "resume_with_extra.pdf";
        String text = "工作经历：优化算法、提升转化率 18.7%";

        try (PDDocument doc = Loader.loadPDF(new File(inputPath))) {
            PDPage page = doc.getPage(0);
            PDRectangle box = page.getMediaBox();

            // 1) 拿到页面内嵌字体（可能是子集，不能保证含所有中文字形）
            PDFont pageFont = null;
            PDResources res = page.getResources();
            if (res != null) {
                Set<COSName> names = (Set<COSName>) res.getFontNames();
                for (COSName name : names) {
                    PDFont f = res.getFont(name);
                    if (f != null) { pageFont = f; break; }
                }
            }

            // 2) 加载一款完整 CJK 字体（强烈推荐把字体放进 resources）
            PDFont cjkFont = tryLoadCJKFont(doc);
            // 3) 英文字体兜底（Standard14，不含中文字形）
            PDFont latin = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                cs.beginText();
                cs.newLineAtOffset(50, box.getHeight() - 120);

                // 逐字符选择“当前能编码的最佳字体”，把同字体的连续片段合并写入
                StringBuilder buf = new StringBuilder();
                PDFont current = null;

                for (int i = 0; i < text.length(); i++) {
                    char ch = text.charAt(i);
                    PDFont target = pickFontForChar(ch, pageFont, cjkFont, latin);

                    if (current != target && buf.length() > 0) {
                        // flush 上一个片段
                        cs.setFont(current, 12);
                        cs.showText(buf.toString());
                        buf.setLength(0);
                    }
                    current = target;
                    buf.append(ch);
                }
                if (buf.length() > 0) {
                    cs.setFont(current, 12);
                    cs.showText(buf.toString());
                }

                cs.endText();
            }

            doc.save(outputPath);
            System.out.println("[OK] 已保存：" + outputPath);
        }
    }

    /** 选择能编码该字符的字体：优先页面字体，其次 CJK 字体，最后 ASCII 用 Helvetica */
    private static PDFont pickFontForChar(char ch, PDFont pageFont, PDFont cjkFont, PDFont latin) {
        String s = String.valueOf(ch);
        // 尝试页面字体
        if (pageFont != null && canEncode(pageFont, s)) return pageFont;
        // 尝试 CJK 字体（完整中文字体）
        if (cjkFont != null && canEncode(cjkFont, s)) return cjkFont;
        // ASCII 走 latin（含空格/英文/数字/常见符号）
        if (ch <= 0x7F) return latin;
        // 兜底：如果没有任何字体能编码（极少见），用方块代替
        return latin;
    }

    /** 用 try-encode 的方式探测字体是否能编码该字符串（PDFBox 3.x 安全办法） */
    private static boolean canEncode(PDFont font, String s) {
        try {
            font.encode(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 加载一款 CJK 字体（优先 resources，其次常见系统路径）；返回 null 表示找不到 */
    private static PDFont tryLoadCJKFont(PDDocument doc) {
        // 先从 resources 里找（推荐把 NotoSansSC-Regular.otf 放到这个路径）
        String[] resourceCandidates = new String[] {
                "/fonts/NotoSansSC-Regular.otf",
                "/fonts/NotoSansSC-Regular.ttf",
                "/fonts/SourceHanSansCN-Regular.otf",
                "/fonts/SourceHanSansCN-Regular.ttf"
        };
        for (String resPath : resourceCandidates) {
            try (InputStream is = Main.class.getResourceAsStream(resPath)) {
                if (is != null) {
                    System.out.println("[INFO] 使用资源字体：" + resPath);
                    return PDType0Font.load(doc, is);
                }
            } catch (Exception ignore) {}
        }

        // 再尝试常见系统字体（路径按需增改；优先 TTF/OTF，TTC 也可试）
        String[] sysCandidates = new String[] {
                "C:\\Windows\\Fonts\\msyh.ttf",     // 微软雅黑
                "C:\\Windows\\Fonts\\simhei.ttf",   // 黑体
                "/System/Library/Fonts/PingFang.ttc",   // macOS 苹方
                "/Library/Fonts/Arial Unicode.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf"
        };
        for (String p : sysCandidates) {
            Path path = Path.of(p);
            if (Files.exists(path)) {
                try {
                    System.out.println("[INFO] 使用系统字体：" + path);
                    return PDType0Font.load(doc, path.toFile());
                } catch (Exception ignore) {}
            }
        }
        System.out.println("[WARN] 未找到可用 CJK 字体，中文可能无法显示。");
        return null;
    }
}

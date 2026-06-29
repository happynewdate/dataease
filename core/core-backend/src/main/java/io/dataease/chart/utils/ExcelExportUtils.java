package io.dataease.chart.utils;

import cn.hutool.core.util.ReflectUtil;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.context.annotation.Configuration;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Configuration
public class ExcelExportUtils {
    public static ByteArrayOutputStream createWaterMark(String content) throws IOException {
        // 这是水印字体的宽和高，可以调整这个大小来控制
        int width = 200;
        int height = 150;
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);// 获取bufferedImage对象
        String fontType = "微软雅黑";
        int fontStyle = Font.BOLD;
        // 这是水印显示字体的大小
        int fontSize = 20;
        Font font = new Font(fontType, fontStyle, fontSize);
        Graphics2D g2d = image.createGraphics(); // 获取Graphics2d对象
        image = g2d.getDeviceConfiguration().createCompatibleImage(width,
                height, Transparency.TRANSLUCENT);
        g2d.dispose();
        g2d = image.createGraphics();
        g2d.setColor(new Color(0, 0, 0, 20)); // 设置字体颜色和透明度，最后一个参数为透明度
        g2d.setStroke(new BasicStroke(1)); // 设置字体
        g2d.setFont(font); // 设置字体类型 加粗 大小
        g2d.rotate(-0.5, (double) image.getWidth() / 2,
                (double) image.getHeight() / 2);// 设置倾斜度
        FontRenderContext context = g2d.getFontRenderContext();
        Rectangle2D bounds = font.getStringBounds(content, context);
        double x = (width - bounds.getWidth()) / 2;
        double y = (height - bounds.getHeight()) / 2;
        double ascent = -bounds.getY();
        double baseY = y + ascent;
        // 写入水印文字原定高度过小，所以累计写水印，增加高度
        g2d.drawString(content, (int) x, (int) baseY);
        // 设置透明度
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
        // 释放对象
        g2d.dispose();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        return os;
    }

    /**
     * Excel 导出添加水印
     *
     * @param workbook ExcelWorkbook
     */
    public static void insertWaterMarkTextToXlsx(Workbook workbook, String waterMarkText) throws IOException {

        if (workbook instanceof SXSSFWorkbook) {
            insertWaterMarkTextToXlsx((SXSSFWorkbook) workbook, waterMarkText);
        } else if (workbook instanceof XSSFWorkbook) {
            insertWaterMarkTextToXlsx((XSSFWorkbook) workbook, waterMarkText);
        }
    }


    /**
     * 给 Excel 添加水印
     *
     * @param workbook      SXSSFWorkbook
     * @param waterMarkText 水印文字内容
     */
    public static void insertWaterMarkTextToXlsx(SXSSFWorkbook workbook, String waterMarkText) throws IOException {
        BufferedImage image = createWatermarkImage(waterMarkText);
        ByteArrayOutputStream imageOs = new ByteArrayOutputStream();
        ImageIO.write(image, "png", imageOs);
        int pictureIdx = workbook.addPicture(imageOs.toByteArray(), XSSFWorkbook.PICTURE_TYPE_PNG);
        XSSFPictureData pictureData = (XSSFPictureData) workbook.getAllPictures().get(pictureIdx);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {//获取每个Sheet表
            SXSSFSheet sheet = workbook.getSheetAt(i);
            //这里由于 SXSSFSheet 没有 getCTWorksheet() 方法，通过反射取出 _sh 属性
            XSSFSheet shReflect = (XSSFSheet) ReflectUtil.getFieldValue(sheet, "_sh");
            PackagePartName ppn = pictureData.getPackagePart().getPartName();
            String relType = XSSFRelation.IMAGES.getRelation();
            PackageRelationship pr = shReflect.getPackagePart().addRelationship(ppn, TargetMode.INTERNAL, relType, null);
            shReflect.getCTWorksheet().addNewPicture().setId(pr.getId());
        }
    }


    /**
     * 给 Excel 添加水印
     *
     * @param workbook      XSSFWorkbook
     * @param waterMarkText 水印文字内容
     */
    public static void insertWaterMarkTextToXlsx(XSSFWorkbook workbook, String waterMarkText) throws IOException {
        BufferedImage image = createWatermarkImage(waterMarkText);
        ByteArrayOutputStream imageOs = new ByteArrayOutputStream();
        ImageIO.write(image, "png", imageOs);
        int pictureIdx = workbook.addPicture(imageOs.toByteArray(), XSSFWorkbook.PICTURE_TYPE_PNG);
        XSSFPictureData pictureData = workbook.getAllPictures().get(pictureIdx);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {//获取每个Sheet表
            XSSFSheet sheet = workbook.getSheetAt(i);
            PackagePartName ppn = pictureData.getPackagePart().getPartName();
            String relType = XSSFRelation.IMAGES.getRelation();
            PackageRelationship pr = sheet.getPackagePart().addRelationship(ppn, TargetMode.INTERNAL, relType, null);
            sheet.getCTWorksheet().addNewPicture().setId(pr.getId());
        }
    }

    /**
     * 创建水印图片
     *
     * @param waterMark 水印文字
     */
    public static BufferedImage createWatermarkImage(String waterMark) {
        String[] textArray = waterMark.split("\n");
        Font font = new Font("SimHei", Font.PLAIN, 32);
        // 调试代码：打印出 JVM 能识别的所有字体
        int width = 500;
        int height = 400;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // 背景透明 开始
        Graphics2D g = image.createGraphics();
        image = g.getDeviceConfiguration().createCompatibleImage(width, height, Transparency.TRANSLUCENT);
        g.dispose();
        // 背景透明 结束
        g = image.createGraphics();
        g.setColor(new Color(Color.lightGray.getRGB()));// 设定画笔颜色
        g.setFont(font);// 设置画笔字体
        //   g.shear(0.1, -0.26);// 设定倾斜度

//        设置字体平滑
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        //文字从中心开始输入，算出文字宽度，左移动一半的宽度，即居中
        FontMetrics fontMetrics = g.getFontMetrics(font);

        // 水印位置
        int x = width / 2;
        int y = height / 2;
        // 设置水印旋转
        g.rotate(Math.toRadians(-40), x, y);
        for (String s : textArray) {
            // 文字宽度
            int textWidth = fontMetrics.stringWidth(s);
            g.drawString(s, x - (textWidth / 2), y);// 画出字符串
            y = y + font.getSize();
        }

        g.dispose();// 释放画笔
        return image;
    }

    /**
     * 设置打印的参数
     *
     * @param wb XSSFWorkbook
     */
    public static void setPrintParams(XSSFWorkbook wb) {
        XSSFSheet sheet = wb.getSheetAt(0);
        XSSFPrintSetup printSetup = sheet.getPrintSetup();
        // 打印方向，true：横向，false：纵向(默认
        printSetup.setLandscape(true);
        //设置A4纸
        printSetup.setPaperSize(XSSFPrintSetup.A4_PAPERSIZE);
        // 将整个工作表打印在一页（缩放）,如果行数很多的话，可能会出问题
        // sheet.setAutobreaks(true);
        //将所有的列调整为一页，行数多的话，自动分页
        printSetup.setScale((short) 70);//缩放的百分比，自行调整
        sheet.setAutobreaks(false);
    }

}

package io.snowdrop.cartography.service;

import io.snowdrop.cartography.model.Capability;
import io.snowdrop.cartography.model.FrameworkEntry;
import io.snowdrop.cartography.store.RegistryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@ApplicationScoped
public class ExcelService {

    private static final String[] HEADERS = {
            "Capability", "Description", "Tags", "Framework", "Name", "Doc", "SCM",
            "Type", "Since", "Review By", "Review Date", "Quarkus Status", "Status Comment"
    };

    private static final int[] COL_WIDTHS = {
            30, 40, 15, 10, 30, 40, 40, 10, 15, 15, 12, 15, 40
    };

    private static final Map<String, byte[]> STATUS_COLORS = Map.of(
            "to develop", new byte[]{(byte) 0xE6, 0x7E, 0x22},
            "no action", new byte[]{(byte) 0x95, (byte) 0xA5, (byte) 0xA6},
            "approved", new byte[]{0x34, (byte) 0x98, (byte) 0xDB},
            "in progress", new byte[]{(byte) 0x9B, 0x59, (byte) 0xB6},
            "done", new byte[]{0x27, (byte) 0xAE, 0x60}
    );

    @Inject
    RegistryStore store;

    public byte[] exportXlsx() throws IOException {
        try (var workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Registry");

            for (int i = 0; i < COL_WIDTHS.length; i++) {
                sheet.setColumnWidth(i, COL_WIDTHS[i] * 256);
            }

            var headerStyle = createHeaderStyle(workbook);
            var wrapStyle = createWrapStyle(workbook);
            var linkStyle = createLinkStyle(workbook);
            var defaultStyle = createDefaultStyle(workbook);

            XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            List<Capability> capabilities = store.findAllOrdered();
            var helper = workbook.getCreationHelper();

            for (Capability c : capabilities) {
                if (c.getEntries().isEmpty()) {
                    var row = sheet.createRow(rowNum++);
                    setCellWithStyle(row, 0, c.getCategory(), defaultStyle);
                    setCellWithStyle(row, 1, c.getDescription(), wrapStyle);
                    setCellWithStyle(row, 2, c.getTags(), wrapStyle);
                    for (int i = 3; i <= 8; i++) setCellWithStyle(row, i, null, defaultStyle);
                    setCellWithStyle(row, 9, c.getReviewBy(), defaultStyle);
                    setCellWithStyle(row, 10, c.getReviewDate() != null ? c.getReviewDate().toString() : null, defaultStyle);
                    setStatusCell(workbook, row, 11, c.getQuarkusStatus());
                    setCellWithStyle(row, 12, c.getStatusComment(), wrapStyle);
                } else {
                    boolean first = true;
                    var sorted = c.getEntries().stream()
                            .sorted(Comparator.comparingInt(e -> e.getFramework() != null ? e.getFramework().ordinal() : Integer.MAX_VALUE))
                            .toList();
                    for (FrameworkEntry e : sorted) {
                        var row = sheet.createRow(rowNum++);
                        setCellWithStyle(row, 0, c.getCategory(), defaultStyle);
                        setCellWithStyle(row, 1, c.getDescription(), wrapStyle);
                        setCellWithStyle(row, 2, c.getTags(), wrapStyle);
                        setCellWithStyle(row, 3, e.getFramework() != null ? e.getFramework().name() : "", defaultStyle);
                        setCellWithStyle(row, 4, e.getName(), defaultStyle);
                        setHyperlinkCell(row, 5, e.getDoc(), e.getName(), helper, linkStyle, defaultStyle);
                        setHyperlinkCell(row, 6, e.getScm(), e.getScmRepoName(), helper, linkStyle, defaultStyle);
                        setCellWithStyle(row, 7, e.getType() != null ? e.getType().name() : "", defaultStyle);
                        setCellWithStyle(row, 8, e.getSince(), defaultStyle);
                        if (first) {
                            setCellWithStyle(row, 9, c.getReviewBy(), defaultStyle);
                            setCellWithStyle(row, 10, c.getReviewDate() != null ? c.getReviewDate().toString() : null, defaultStyle);
                            setStatusCell(workbook, row, 11, c.getQuarkusStatus());
                            setCellWithStyle(row, 12, c.getStatusComment(), wrapStyle);
                            first = false;
                        } else {
                            for (int i = 9; i <= 12; i++) setCellWithStyle(row, i, null, defaultStyle);
                        }
                    }
                }
            }

            sheet.createFreezePane(0, 1);

            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowNum - 1, 0, HEADERS.length - 1));

            int lastDataRow = Math.max(rowNum - 1, 1);
            addDropdownValidation(sheet, 3, lastDataRow, new String[]{"Spring", "Quarkus", "SpringBoot"});
            addDropdownValidation(sheet, 7, lastDataRow, new String[]{"STARTER", "EXTENSION"});
            addDropdownValidation(sheet, 11, lastDataRow, new String[]{"to develop", "no action", "approved", "in progress", "done"});

            var out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setCellWithStyle(XSSFRow row, int col, String value, XSSFCellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void setHyperlinkCell(XSSFRow row, int col, String url, String label,
                                   org.apache.poi.ss.usermodel.CreationHelper helper,
                                   XSSFCellStyle linkStyle, XSSFCellStyle defaultStyle) {
        var cell = row.createCell(col);
        if (url != null && !url.isBlank()) {
            cell.setCellValue(label != null && !label.isBlank() ? label : url);
            var link = helper.createHyperlink(HyperlinkType.URL);
            link.setAddress(url);
            cell.setHyperlink(link);
            cell.setCellStyle(linkStyle);
        } else {
            cell.setCellValue("");
            cell.setCellStyle(defaultStyle);
        }
    }

    private void setStatusCell(XSSFWorkbook workbook, XSSFRow row, int col, String status) {
        var cell = row.createCell(col);
        cell.setCellValue(status != null ? status : "");

        byte[] rgb = status != null ? STATUS_COLORS.get(status) : null;
        if (rgb != null) {
            var style = workbook.createCellStyle();
            style.cloneStyleFrom(createDefaultStyle(workbook));
            style.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = workbook.createFont();
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setFontHeightInPoints((short) 10);
            style.setFont(font);
            cell.setCellStyle(style);
        } else {
            cell.setCellStyle(createDefaultStyle(workbook));
        }
    }

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook workbook) {
        var style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xD9, (byte) 0xE2, (byte) 0xF3}, new DefaultIndexedColorMap()));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private XSSFCellStyle createWrapStyle(XSSFWorkbook workbook) {
        var style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        var font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle createLinkStyle(XSSFWorkbook workbook) {
        var style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setColor(IndexedColors.BLUE.getIndex());
        font.setUnderline(XSSFFont.U_SINGLE);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private void addDropdownValidation(XSSFSheet sheet, int col, int lastRow, String[] values) {
        var dvHelper = new XSSFDataValidationHelper(sheet);
        var constraint = dvHelper.createExplicitListConstraint(values);
        var range = new CellRangeAddressList(1, lastRow, col, col);
        XSSFDataValidation validation = (XSSFDataValidation) dvHelper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private XSSFCellStyle createDefaultStyle(XSSFWorkbook workbook) {
        var style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        var font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }
}

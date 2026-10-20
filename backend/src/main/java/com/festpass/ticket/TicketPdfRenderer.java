package com.festpass.ticket;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Renders tickets as a PDF with OpenPDF: one A6 page per ticket with its QR code. */
@Component
public class TicketPdfRenderer {

    private static final Color INK = new Color(0x14, 0x14, 0x1f);
    private static final Color MUTED = new Color(0x6b, 0x6b, 0x80);

    private final DateTimeFormatter dateFormat;

    public TicketPdfRenderer(@Value("${festpass.display-zone}") ZoneId zone) {
        this.dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.ENGLISH).withZone(zone);
    }

    public byte[] render(List<TicketDetails> tickets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A6, 24, 24, 36, 24);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle("FestPass tickets");
            document.open();
            for (int i = 0; i < tickets.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                page(document, writer, tickets.get(i));
            }
            document.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private void page(Document document, PdfWriter writer, TicketDetails t) throws IOException {
        // A band in the tier's colour across the top of the ticket.
        Color tierColor = Color.decode(t.tier().getColorHex());
        PdfContentByte canvas = writer.getDirectContentUnder();
        Rectangle page = document.getPageSize();
        canvas.setColorFill(tierColor);
        canvas.rectangle(0, page.getHeight() - 16, page.getWidth(), 16);
        canvas.fill();

        document.add(text("FESTPASS", FontFactory.HELVETICA_BOLD, 8, tierColor));
        document.add(text(t.event().getTitle(), FontFactory.HELVETICA_BOLD, 15, INK));
        document.add(text(dateFormat.format(t.event().getStartsAt()), FontFactory.HELVETICA, 9, INK));
        document.add(text(t.event().getVenueName(), FontFactory.HELVETICA, 9, MUTED));
        document.add(Chunk.NEWLINE);
        document.add(text(t.tier().getName() + "  ·  " + t.seatLabel(), FontFactory.HELVETICA_BOLD, 10, INK));
        document.add(text(t.ticket().getHolderName(), FontFactory.HELVETICA, 9, MUTED));

        Image qr = Image.getInstance(QrCodes.png(t.code(), 400));
        qr.scaleAbsolute(150, 150);
        qr.setAlignment(Element.ALIGN_CENTER);
        document.add(qr);

        Paragraph id = text("Ticket " + t.ticket().getId(), FontFactory.COURIER, 6, MUTED);
        id.setAlignment(Element.ALIGN_CENTER);
        document.add(id);
    }

    private static Paragraph text(String value, String font, float size, Color color) {
        Font f = FontFactory.getFont(font, size, color);
        Paragraph p = new Paragraph(value, f);
        p.setSpacingAfter(2);
        return p;
    }
}

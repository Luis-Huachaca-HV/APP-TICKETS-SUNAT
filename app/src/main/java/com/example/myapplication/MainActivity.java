package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import android.view.View;
import android.widget.ImageView;


public class MainActivity extends AppCompatActivity {

    private static final int PICK_XML_FILE = 1;
    private static final int PICK_PDF_FILE = 2;

    private String xmlContent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button selectXmlButton = findViewById(R.id.selectXmlButton);
        selectXmlButton.setOnClickListener(v -> openFilePickerXml());

        Button selectPdfButton = findViewById(R.id.selectPdfButton);
        selectPdfButton.setOnClickListener(v -> openFilePickerPdf());
    }

    private void openFilePickerXml() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_XML_FILE);
    }

    private void openFilePickerPdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_PDF_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == PICK_XML_FILE) {
                readXmlContent(uri);
            } else if (requestCode == PICK_PDF_FILE) {
                renderPdfAndSimulateEscpos(uri);
            }
        }
    }

    private void readXmlContent(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }

            xmlContent = stringBuilder.toString();
            parseXml(xmlContent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al leer el archivo XML", Toast.LENGTH_LONG).show();
        }
    }

    private void parseXml(String xml) {
        TextView output = findViewById(R.id.outputTextView);
        String ticket = parseAndGenerateTicket(xml);
        output.setText(ticket);
    }

    private String parseAndGenerateTicket(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            InputStream is = new ByteArrayInputStream(xml.getBytes());
            Document doc = builder.parse(is);

            String ns_cbc = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
            String ns_cac = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();

            xpath.setNamespaceContext(new NamespaceContext() {
                public String getNamespaceURI(String prefix) {
                    switch (prefix) {
                        case "cbc": return ns_cbc;
                        case "cac": return ns_cac;
                        default: return XMLConstants.NULL_NS_URI;
                    }
                }
                public String getPrefix(String uri) { return null; }
                public Iterator getPrefixes(String uri) { return null; }
            });

            String empresa = xpath.evaluate("//cac:AccountingSupplierParty//cbc:RegistrationName", doc);
            String ruc = xpath.evaluate("//cac:AccountingSupplierParty//cbc:ID", doc);
            String fecha = xpath.evaluate("//cbc:IssueDate", doc);
            String cliente = xpath.evaluate("//cac:AccountingCustomerParty//cbc:RegistrationName", doc);
            if (cliente.isEmpty()) cliente = "-";

            String direccion = xpath.evaluate("//cac:AccountingSupplierParty//cac:Party//cac:PhysicalLocation//cac:Address//cbc:StreetName", doc);
            if (direccion.isEmpty()) direccion = "-";

            String telefono = xpath.evaluate("//cac:AccountingSupplierParty//cac:Party//cac:Contact//cbc:Telephone", doc);
            if (telefono.isEmpty()) telefono = "-";

            NodeList items = (NodeList) xpath.evaluate("//cac:InvoiceLine", doc, XPathConstants.NODESET);

            StringBuilder ticket = new StringBuilder();
            ticket.append("        RUC: ").append(ruc).append("\n");
            ticket.append("HAMPIY INVERSIONES S.R.L.\n");
            ticket.append(direccion).append("\n");
            ticket.append("Tel: ").append(telefono).append("\n");
            ticket.append("--------------------------------\n");
            ticket.append("Cliente: ").append(cliente).append("\n");
            ticket.append("--------------------------------\n");

            double total = 0.0;
            for (int i = 0; i < items.getLength(); i++) {
                Node item = items.item(i);
                String desc = xpath.evaluate(".//cac:Item//cbc:Description", item);
                String cantidadStr = xpath.evaluate(".//cbc:InvoicedQuantity", item);
                String precioStr = xpath.evaluate(".//cac:Price//cbc:PriceAmount", item);
                String totalItemStr = xpath.evaluate(".//cbc:LineExtensionAmount", item);

                double cantidad = Double.parseDouble(cantidadStr);
                double precio = Double.parseDouble(precioStr);
                double totalItem = Double.parseDouble(totalItemStr);

                total += totalItem;

                if (desc.length() > 16) desc = desc.substring(0, 16);
                ticket.append(String.format("%-16s%2.0f x %5.2f = %6.2f\n", desc, cantidad, precio, totalItem));
            }

            double subtotal = total / 1.18;
            double igv = total - subtotal;

            ticket.append("--------------------------------\n");
            ticket.append(String.format("SUBTOTAL:       S/ %7.2f\n", subtotal));
            ticket.append(String.format("IGV (18%%):      S/ %7.2f\n", igv));
            ticket.append(String.format("TOTAL:          S/ %7.2f\n", total));
            ticket.append("--------------------------------\n");
            ticket.append("Gracias por su compra!\n");
            ticket.append("Vuelva pronto :)\n");

            return ticket.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al procesar XML.";
        }
    }

    private void renderPdfAndSimulateEscpos(Uri uri) {
        try {
            ParcelFileDescriptor fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            PdfRenderer renderer = new PdfRenderer(fileDescriptor);
            PdfRenderer.Page page = renderer.openPage(0);

            int width = 384;
            int height = (int) (page.getHeight() * (384.0 / page.getWidth()));

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            page.close();
            renderer.close();

            // Mostrar la imagen renderizada
            ImageView imageView = findViewById(R.id.pdfPreviewImageView);
            imageView.setImageBitmap(bitmap);
            imageView.setVisibility(View.VISIBLE);  // ✅ mostrar preview

            // Opcional: mostrar información en el TextView
            StringBuilder sb = new StringBuilder();
            sb.append("Vista previa del PDF (ticket):\n");
            sb.append("Ancho: ").append(width).append(" px\n");
            sb.append("Alto: ").append(height).append(" px\n");

            TextView output = findViewById(R.id.outputTextView);
            output.setText(sb.toString());

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al procesar PDF", Toast.LENGTH_LONG).show();
        }
    }

}
